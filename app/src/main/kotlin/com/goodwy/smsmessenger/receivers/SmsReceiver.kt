package com.goodwy.smsmessenger.receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Telephony
import com.goodwy.commons.extensions.baseConfig
import com.goodwy.commons.extensions.getMyContactsCursor
import com.goodwy.commons.extensions.isNumberBlocked
import com.goodwy.commons.helpers.ContactLookupResult
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.PhoneNumber
import com.goodwy.commons.models.SimpleContact
import com.goodwy.smsmessenger.extensions.config
import com.goodwy.smsmessenger.extensions.getContactFromAddress
import com.goodwy.smsmessenger.extensions.getConversations
import com.goodwy.smsmessenger.extensions.getNotificationBitmap
import com.goodwy.smsmessenger.extensions.getThreadId
import com.goodwy.smsmessenger.extensions.insertNewSMS
import com.goodwy.smsmessenger.extensions.insertOrUpdateConversation
import com.goodwy.smsmessenger.extensions.messagesDB
import com.goodwy.smsmessenger.extensions.shouldUnarchive
import com.goodwy.smsmessenger.extensions.showReceivedMessageNotification
import com.goodwy.smsmessenger.extensions.updateConversationArchivedStatus
import com.goodwy.smsmessenger.helpers.ReceiverUtils.isMessageFilteredOut
import com.goodwy.smsmessenger.helpers.refreshConversations
import com.goodwy.smsmessenger.helpers.refreshMessages
import com.goodwy.smsmessenger.models.Message

class SmsReceiver : BroadcastReceiver() {
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext

        ensureBackgroundThread {
            try {
                val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (parts.isEmpty()) return@ensureBackgroundThread

                // this is how it has always worked, but need to revisit this.
                val address = parts.last().originatingAddress.orEmpty()
                if (address.isBlank()) return@ensureBackgroundThread
                val subject = parts.last().pseudoSubject.orEmpty()
                val status = parts.last().status
                val body = buildString { parts.forEach { append(it.messageBody.orEmpty()) } }

                if (isMessageFilteredOut(appContext, body)) return@ensureBackgroundThread
                if (appContext.isNumberBlocked(address)) return@ensureBackgroundThread
                if (appContext.baseConfig.blockUnknownNumbers) {
                    val privateCursor =
                        appContext.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                    val result = SimpleContactsHelper(appContext).existsSync(address, privateCursor)
                    if (result == ContactLookupResult.NotFound) return@ensureBackgroundThread
                }

                val date = System.currentTimeMillis() // Current date of receipt
                val dateSent = parts.last().timestampMillis // Original dispatch date
                val threadId = appContext.getThreadId(address)
                val subscriptionId = intent.getIntExtra("subscription", -1)

                handleMessageSync(
                    context = appContext,
                    address = address,
                    subject = subject,
                    body = body,
                    date = date,
                    dateSent = dateSent,
                    threadId = threadId,
                    subscriptionId = subscriptionId,
                    status = status
                )
            } finally {
                pending.finish()
            }
        }

        if (context.config.notifyTurnsOnScreen) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakelock = powerManager.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "goodwy.messages:sms.receiver"
            )
            wakelock.acquire(3000)
        }
    }

    private fun handleMessageSync(
        context: Context,
        address: String,
        subject: String,
        body: String,
        date: Long,
        dateSent: Long,
        read: Int = 0,
        threadId: Long,
        type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX,
        subscriptionId: Int,
        status: Int
    ) {

        var photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)
        var bitmap = context.getNotificationBitmap(photoUri)
//        Handler(Looper.getMainLooper()).post {
//            if (!context.isNumberBlocked(address)) {
//                val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
//                ensureBackgroundThread {
//                    SimpleContactsHelper(context).getAvailableContacts(false) {
//                        val privateContacts = MyContactsContentProvider.getSimpleContacts(context, privateCursor)
//                        val contacts = ArrayList(it + privateContacts)
//
//                        val newMessageId = context.insertNewSMS(address, subject, body, date, read, threadId, type, subscriptionId)
//
//                        val conversation = context.getConversations(threadId).firstOrNull() ?: return@getAvailableContacts
//                        try {
//                            context.insertOrUpdateConversation(conversation)
//                        } catch (_: Exception) {
//                        }
//
//                        val senderName = context.getNameFromAddress(address, privateCursor)
//                        val participant = if (contacts.isNotEmpty()) {
//                            val contact = contacts.firstOrNull { it.doesHavePhoneNumber(address) } ?: contacts.firstOrNull { it.phoneNumbers.map { it.value }.any { it == address } }
//                            if (contact != null) {
//                                val phoneNumber = contact.phoneNumbers.firstOrNull { it.normalizedNumber == address } ?: PhoneNumber(address, 0, "", address)
//                                if (photoUri.isEmpty()) photoUri = contact.photoUri
//                                if (bitmap == null ) bitmap = context.getNotificationBitmap(photoUri)
//                                SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList(), contact.company, contact.jobPosition)
//                            } else {
//                                val phoneNumber = PhoneNumber(address, 0, "", address)
//                                SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList())
//                            }
//                        } else {
//                            val phoneNumber = PhoneNumber(address, 0, "", address)
//                            SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList())
//                        }
//
//                        val participants = arrayListOf(participant)
//                        val messageDate = (date / 1000).toInt()
//
//                        val message =
//                            Message(
//                                newMessageId,
//                                body,
//                                type,
//                                status,
//                                participants,
//                                messageDate,
//                                false,
//                                threadId,
//                                false,
//                                null,
//                                address,
//                                senderName,
//                                photoUri,
//                                subscriptionId
//                            )
//                        context.messagesDB.insertOrUpdate(message)
//                        if (context.shouldUnarchive()) {
//                            context.updateConversationArchivedStatus(threadId, false)
//                        }
//                        refreshMessages()
//                        refreshConversations()
//                        context.showReceivedMessageNotification(newMessageId, address, body, threadId, bitmap, subscriptionId)
//                    }
//                }
//            }
//        }


        Handler(Looper.getMainLooper()).post {
            context.getContactFromAddress(address) { simpleContact ->
                val newMessageId = context.insertNewSMS(
                    address = address,
                    subject = subject,
                    body = body,
                    date = date,
                    dateSent = dateSent,
                    read = read,
                    threadId = threadId,
                    type = type,
                    subscriptionId = subscriptionId
                )

                context.getConversations(threadId).firstOrNull()?.let { conv ->
                    runCatching { context.insertOrUpdateConversation(conv) }
                }

//        val senderName = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true).use {
//            context.getNameFromAddress(address, it)
//        }

//        val participant = SimpleContact(
//            rawId = 0,
//            contactId = 0,
//            name = senderName,
//            photoUri = photoUri,
//            phoneNumbers = arrayListOf(PhoneNumber(value = address, type = 0, label = "", normalizedNumber = address)),
//            birthdays = ArrayList(),
//            anniversaries = ArrayList()
//        )

                val senderName = simpleContact?.name ?: address
                val participant = if (simpleContact != null) {
                    val phoneNumber = simpleContact.phoneNumbers.firstOrNull { it.normalizedNumber == address } ?: PhoneNumber(address, 0, "", address)
                    if (photoUri.isEmpty()) photoUri = simpleContact.photoUri
                    if (bitmap == null) bitmap = context.getNotificationBitmap(photoUri)
                    SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList(), simpleContact.company, simpleContact.jobPosition)
                } else {
                    val phoneNumber = PhoneNumber(address, 0, "", address)
                    SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList())
                }

                val message = Message(
                    id = newMessageId,
                    body = body,
                    type = type,
                    status = status,
                    participants = arrayListOf(participant),
                    date = (date / 1000).toInt(),
                    read = false,
                    threadId = threadId,
                    isMMS = false,
                    attachment = null,
                    senderPhoneNumber = address,
                    senderName = senderName,
                    senderPhotoUri = photoUri,
                    subscriptionId = subscriptionId
                )

                context.messagesDB.insertOrUpdate(message)

                if (context.shouldUnarchive()) {
                    context.updateConversationArchivedStatus(threadId, false)
                }

                refreshMessages()
                refreshConversations()
                context.showReceivedMessageNotification(
                    messageId = newMessageId,
                    address = address,
                    senderName = senderName,
                    body = body,
                    threadId = threadId,
                    bitmap = bitmap,
                    subscriptionId = subscriptionId,
                    contact = simpleContact
                )
            }
        }
    }
}

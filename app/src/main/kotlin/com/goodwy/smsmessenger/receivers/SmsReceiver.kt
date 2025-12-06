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
import com.goodwy.commons.helpers.MyContactsContentProvider
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.PhoneNumber
import com.goodwy.commons.models.SimpleContact
import com.goodwy.smsmessenger.extensions.config
import com.goodwy.smsmessenger.extensions.getConversations
import com.goodwy.smsmessenger.extensions.getNameFromAddress
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
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        var address = ""
        var body = ""
        var subject = ""
        var date = 0L
        var threadId = 0L
        var status = Telephony.Sms.STATUS_NONE
        val type = Telephony.Sms.MESSAGE_TYPE_INBOX
        val read = 0
        val subscriptionId = intent.getIntExtra("subscription", -1)

        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            messages.forEach {
                address = it.originatingAddress ?: ""
                subject = it.pseudoSubject
                status = it.status
                body += it.messageBody
                date = System.currentTimeMillis()
                threadId = context.getThreadId(address)
            }

            if (context.baseConfig.blockUnknownNumbers) {
                val simpleContactsHelper = SimpleContactsHelper(context)
                simpleContactsHelper.exists(address, privateCursor) { exists ->
                    if (exists) {
                        handleMessage(context, address, subject, body, date, read, threadId, type, subscriptionId, status)
                    }
                }
            } else {
                handleMessage(context, address, subject, body, date, read, threadId, type, subscriptionId, status)
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

    private fun handleMessage(
        context: Context,
        address: String,
        subject: String,
        body: String,
        date: Long,
        read: Int,
        threadId: Long,
        type: Int,
        subscriptionId: Int,
        status: Int
    ) {
        if (isMessageFilteredOut(context, body)) {
            return
        }

        var photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)
        var bitmap = context.getNotificationBitmap(photoUri)
        Handler(Looper.getMainLooper()).post {
            if (!context.isNumberBlocked(address)) {
                val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                ensureBackgroundThread {
                    SimpleContactsHelper(context).getAvailableContacts(false) {
                        val privateContacts = MyContactsContentProvider.getSimpleContacts(context, privateCursor)
                        val contacts = ArrayList(it + privateContacts)

                        val newMessageId = context.insertNewSMS(address, subject, body, date, read, threadId, type, subscriptionId)

                        val conversation = context.getConversations(threadId).firstOrNull() ?: return@getAvailableContacts
                        try {
                            context.insertOrUpdateConversation(conversation)
                        } catch (_: Exception) {
                        }

                        val senderName = context.getNameFromAddress(address, privateCursor)
                        val participant = if (contacts.isNotEmpty()) {
                            val contact = contacts.firstOrNull { it.doesHavePhoneNumber(address) } ?: contacts.firstOrNull { it.phoneNumbers.map { it.value }.any { it == address } }
                            if (contact != null) {
                                val phoneNumber = contact.phoneNumbers.firstOrNull { it.normalizedNumber == address } ?: PhoneNumber(address, 0, "", address)
                                if (photoUri.isEmpty()) photoUri = contact.photoUri
                                if (bitmap == null ) bitmap = context.getNotificationBitmap(photoUri)
                                SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList(), contact.company, contact.jobPosition)
                            } else {
                                val phoneNumber = PhoneNumber(address, 0, "", address)
                                SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList())
                            }
                        } else {
                            val phoneNumber = PhoneNumber(address, 0, "", address)
                            SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList())
                        }

                        val participants = arrayListOf(participant)
                        val messageDate = (date / 1000).toInt()

                        val message =
                            Message(
                                newMessageId,
                                body,
                                type,
                                status,
                                participants,
                                messageDate,
                                false,
                                threadId,
                                false,
                                null,
                                address,
                                senderName,
                                photoUri,
                                subscriptionId
                            )
                        context.messagesDB.insertOrUpdate(message)
                        if (context.shouldUnarchive()) {
                            context.updateConversationArchivedStatus(threadId, false)
                        }
                        refreshMessages()
                        refreshConversations()
                        context.showReceivedMessageNotification(newMessageId, address, body, threadId, bitmap, subscriptionId)
                    }
                }
            }
        }
    }
}

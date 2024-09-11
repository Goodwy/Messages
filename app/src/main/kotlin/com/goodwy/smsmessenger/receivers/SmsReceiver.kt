package com.goodwy.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.goodwy.commons.extensions.baseConfig
import com.goodwy.commons.extensions.getMyContactsCursor
import com.goodwy.commons.extensions.isNumberBlocked
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.PhoneNumber
import com.goodwy.commons.models.SimpleContact
import com.goodwy.smsmessenger.extensions.*
import com.goodwy.smsmessenger.helpers.ReceiverUtils.isMessageFilteredOut
import com.goodwy.smsmessenger.helpers.refreshMessages
import com.goodwy.smsmessenger.models.Message

class SmsReceiver : BroadcastReceiver() {
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

        val privateCursor = context.getMyContactsCursor(false, true)
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

        val photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)
        val bitmap = context.getNotificationBitmap(photoUri)
        Handler(Looper.getMainLooper()).post {
            if (!context.isNumberBlocked(address)) {
                val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                ensureBackgroundThread {
                    val newMessageId = context.insertNewSMS(address, subject, body, date, read, threadId, type, subscriptionId)

                    val conversation = context.getConversations(threadId).firstOrNull() ?: return@ensureBackgroundThread
                    try {
                        context.insertOrUpdateConversation(conversation)
                    } catch (ignored: Exception) {
                    }

                    try {
                        context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
                    } catch (ignored: Exception) {
                    }

                    val senderName = context.getNameFromAddress(address, privateCursor)
                    val phoneNumber = PhoneNumber(address, 0, "", address)
                    val participant = SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList())
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
                    if (context.config.isArchiveAvailable) {
                        context.updateConversationArchivedStatus(threadId, false)
                    }
                    refreshMessages()
                    context.showReceivedMessageNotification(newMessageId, address, body, threadId, bitmap, subscriptionId)
                }
            }
        }
    }
}

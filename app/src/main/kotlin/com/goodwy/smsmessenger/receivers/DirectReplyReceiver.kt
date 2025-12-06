package com.goodwy.smsmessenger.receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.RemoteInput
import com.goodwy.commons.extensions.showErrorToast
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.smsmessenger.extensions.*
import com.goodwy.smsmessenger.helpers.*
import com.goodwy.smsmessenger.messaging.sendMessageCompat

class DirectReplyReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(THREAD_NUMBER)
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val simToReply: Int = intent.getIntExtra(SIM_TO_REPLY, -1)
        val sender = intent.getStringExtra(THREAD_TITLE)
        var body = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REPLY)?.toString() ?: return

        body = context.removeDiacriticsIfNeeded(body)

        if (address != null) {
            var subscriptionId: Int? = null
            val availableSIMs = context.subscriptionManagerCompat().activeSubscriptionInfoList
            if ((availableSIMs?.size ?: 0) > 1) {
                val currentSIMCardIndex = context.config.getUseSIMIdAtNumber(address)
                val wantedId = availableSIMs?.getOrNull(currentSIMCardIndex)
                if (wantedId != null) {
                    subscriptionId = wantedId.subscriptionId
                }
            }

            val simToReplyFinal = if (simToReply == -1) subscriptionId else simToReply

            ensureBackgroundThread {
                var messageId = 0L
                try {
                    context.sendMessageCompat(body, listOf(address), simToReplyFinal, emptyList())
                    val message = context.getMessages(
                        threadId = threadId, includeScheduledMessages = false, limit = 1
                    ).lastOrNull()
                    if (message != null) {
                        context.messagesDB.insertOrUpdate(message)
                        messageId = message.id

                        context.updateLastConversationMessage(threadId)
                    }
                } catch (e: Exception) {
                    context.showErrorToast(e)
                }

                val photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)
                val bitmap = context.getNotificationBitmap(photoUri)
                context.getContactFromAddress(address) { simpleContact ->
                    Handler(Looper.getMainLooper()).post {
                        context.notificationHelper.showMessageNotification(
                            messageId = messageId,
                            address = address,
                            body = body,
                            threadId = threadId,
                            bitmap = bitmap,
                            sender = null,
                            senderCache = sender,
                            alertOnlyOnce = true,
                            subscriptionId = simToReplyFinal,
                            contact = simpleContact
                        )
                    }
                }

                context.markThreadMessagesRead(threadId)
                context.conversationsDB.markRead(threadId)
            }
        }
    }
}

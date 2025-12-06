package com.goodwy.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.goodwy.commons.extensions.showErrorToast
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.smsmessenger.extensions.conversationsDB
import com.goodwy.smsmessenger.extensions.deleteScheduledMessage
import com.goodwy.smsmessenger.extensions.getAddresses
import com.goodwy.smsmessenger.extensions.messagesDB
import com.goodwy.smsmessenger.helpers.SCHEDULED_MESSAGE_ID
import com.goodwy.smsmessenger.helpers.THREAD_ID
import com.goodwy.smsmessenger.helpers.refreshConversations
import com.goodwy.smsmessenger.helpers.refreshMessages
import com.goodwy.smsmessenger.messaging.sendMessageCompat

class ScheduledMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "goodwy.messages:scheduled.message.receiver")
        wakelock.acquire(3000)


        ensureBackgroundThread {
            handleIntent(context, intent)
        }
    }

    private fun handleIntent(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val messageId = intent.getLongExtra(SCHEDULED_MESSAGE_ID, 0L)
        val message = try {
            context.messagesDB.getScheduledMessageWithId(threadId, messageId)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        val addresses = message.participants.getAddresses()
        val attachments = message.attachment?.attachments ?: emptyList()

        try {
            Handler(Looper.getMainLooper()).post {
                context.sendMessageCompat(message.body, addresses, message.subscriptionId, attachments)
            }

            // delete temporary conversation and message as it's already persisted to the telephony db now
            context.deleteScheduledMessage(messageId)
            context.conversationsDB.deleteThreadId(messageId)
            refreshMessages()
            refreshConversations()
        } catch (e: Exception) {
            context.showErrorToast(e)
        } catch (e: Error) {
            context.showErrorToast(e.localizedMessage ?: context.getString(com.goodwy.commons.R.string.unknown_error_occurred))
        }
    }
}

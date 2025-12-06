package com.goodwy.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goodwy.commons.extensions.notificationManager
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.smsmessenger.extensions.conversationsDB
import com.goodwy.smsmessenger.extensions.deleteMessage
import com.goodwy.smsmessenger.extensions.markThreadMessagesRead
import com.goodwy.smsmessenger.extensions.updateLastConversationMessage
import com.goodwy.smsmessenger.helpers.IS_MMS
import com.goodwy.smsmessenger.helpers.MESSAGE_ID
import com.goodwy.smsmessenger.helpers.THREAD_ID
import com.goodwy.smsmessenger.helpers.refreshConversations
import com.goodwy.smsmessenger.helpers.refreshMessages

class DeleteSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val messageId = intent.getLongExtra(MESSAGE_ID, 0L)
        val isMms = intent.getBooleanExtra(IS_MMS, false)
        context.notificationManager.cancel(threadId.hashCode())
        ensureBackgroundThread {
            context.markThreadMessagesRead(threadId)
            context.conversationsDB.markRead(threadId)
            context.deleteMessage(messageId, isMms)
            context.updateLastConversationMessage(threadId)
            refreshMessages()
            refreshConversations()
        }
    }
}

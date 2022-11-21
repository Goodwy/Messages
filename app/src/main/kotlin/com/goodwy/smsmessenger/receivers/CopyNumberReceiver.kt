package com.goodwy.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goodwy.commons.extensions.copyToClipboard
import com.goodwy.commons.extensions.notificationManager
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.smsmessenger.extensions.conversationsDB
import com.goodwy.smsmessenger.extensions.markThreadMessagesRead
import com.goodwy.smsmessenger.extensions.updateUnreadCountBadge
import com.goodwy.smsmessenger.helpers.*

class CopyNumberReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            COPY_NUMBER -> {
                val body = intent.getStringExtra(THREAD_TEXT)
                ensureBackgroundThread {
                    context.copyToClipboard(body!!)
                }
            }
        }
    }
}

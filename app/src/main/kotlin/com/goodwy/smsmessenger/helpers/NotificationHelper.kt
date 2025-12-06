package com.goodwy.smsmessenger.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.SimpleContact
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.activities.MainActivity
import com.goodwy.smsmessenger.activities.ThreadActivity
import com.goodwy.smsmessenger.extensions.config
import com.goodwy.smsmessenger.extensions.getOTPFromText
import com.goodwy.smsmessenger.extensions.shortcutHelper
import com.goodwy.smsmessenger.messaging.isShortCodeWithLetters
import com.goodwy.smsmessenger.receivers.CopyNumberReceiver
import com.goodwy.smsmessenger.receivers.DeleteSmsReceiver
import com.goodwy.smsmessenger.receivers.DirectReplyReceiver
import com.goodwy.smsmessenger.receivers.MarkAsReadReceiver

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.notificationManager
    private val soundUri get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    private val user = Person.Builder()
        .setName(context.getString(R.string.me))
        .setIcon(IconCompat.createWithResource(context, com.goodwy.commons.R.drawable.placeholder_contact))
        .build()

    @SuppressLint("NewApi")
    fun showMessageNotification(
        messageId: Long,
        address: String,
        body: String,
        threadId: Long,
        bitmap: Bitmap?,
        sender: String?,
        senderCache: String? = null,
        alertOnlyOnce: Boolean = false,
        subscriptionId: Int? = null,
        contact: SimpleContact? = null
    ) {
        val hasCustomNotifications =
            context.config.customNotifications.contains(threadId.toString())
        val notificationChannelId =
            if (hasCustomNotifications) threadId.toString() else NOTIFICATION_CHANNEL
        if (!hasCustomNotifications) {
            createChannel(notificationChannelId, context.getString(R.string.channel_received_sms))
        }

        val notificationId = threadId.hashCode()
        val contentIntent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }
        val contentPendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

        val markAsReadIntent = Intent(context, MarkAsReadReceiver::class.java).apply {
            action = MARK_AS_READ
            putExtra(THREAD_ID, threadId)
        }
        val markAsReadPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                markAsReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

        val deleteSmsIntent = Intent(context, DeleteSmsReceiver::class.java).apply {
            putExtra(THREAD_ID, threadId)
            putExtra(MESSAGE_ID, messageId)
        }
        val deleteSmsPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                deleteSmsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

        var replyAction: NotificationCompat.Action? = null
        val isNoReplySms = isShortCodeWithLetters(address)
        if (!isNoReplySms) {
            val replyLabel = context.getString(R.string.reply)
            val remoteInput = RemoteInput.Builder(REPLY)
                .setLabel(replyLabel)
                .build()

            val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
                putExtra(THREAD_ID, threadId)
                putExtra(THREAD_NUMBER, address)
                putExtra(SIM_TO_REPLY, subscriptionId)
                putExtra(THREAD_TITLE, sender ?: senderCache)
            }

            val replyPendingIntent =
                PendingIntent.getBroadcast(
                    context.applicationContext,
                    notificationId,
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_send_vector,
                replyLabel,
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .build()
        }

        val title = sender ?: senderCache
        val largeIcon = bitmap ?: if (contact != null && title != null) {
            if (contact.isABusinessContact() || isNoReplySms) {
                SimpleContactsHelper(context).getColoredCompanyIcon(title).convertToBitmap()
            } else {
                SimpleContactsHelper(context).getContactLetterIcon(title)
            }
        } else if (title == address) {
            SimpleContactsHelper(context).getColoredContactIcon(title).convertToBitmap()
        } else if (sender != null) {
            SimpleContactsHelper(context).getContactLetterIcon(sender)
        } else {
            null
        }

        val builder = NotificationCompat.Builder(context, notificationChannelId).apply {
            when (context.config.lockScreenVisibilitySetting) {
                LOCK_SCREEN_SENDER_MESSAGE -> {
                    setLargeIcon(largeIcon)
                    setStyle(getMessagesStyle(address, body, notificationId, sender))
                }

                LOCK_SCREEN_SENDER -> {
                    setContentTitle(sender)
                    setLargeIcon(largeIcon)
                    val summaryText = context.getString(R.string.new_message)
                    setStyle(
                        NotificationCompat.BigTextStyle().setSummaryText(summaryText).bigText(body)
                    )
                }
            }

            color = context.getProperPrimaryColor()
            setSmallIcon(R.drawable.ic_messages)
            setContentIntent(contentPendingIntent)
            priority = NotificationCompat.PRIORITY_MAX
            setDefaults(Notification.DEFAULT_LIGHTS)
            setCategory(Notification.CATEGORY_MESSAGE)
            setAutoCancel(true)
            setOnlyAlertOnce(alertOnlyOnce)
            setSound(soundUri, AudioManager.STREAM_NOTIFICATION)
        }

        if (replyAction != null && context.config.lockScreenVisibilitySetting == LOCK_SCREEN_SENDER_MESSAGE) {
            builder.addAction(replyAction)
        }

        builder.addAction(
            com.goodwy.commons.R.drawable.ic_check_vector,
            context.getString(R.string.mark_as_read),
            markAsReadPendingIntent
        )
            .setChannelId(notificationChannelId)

        val number = body.getOTPFromText()
        if (number != null) {
            val copyNumberAndDelete = context.config.copyNumberAndDelete
            val copyNumberIntent = Intent(context, CopyNumberReceiver::class.java).apply {
                action = if (copyNumberAndDelete) COPY_NUMBER_AND_DELETE else COPY_NUMBER
                putExtra(THREAD_TEXT, number)
                putExtra(THREAD_ID, threadId)
                if (copyNumberAndDelete) {
                    putExtra(MESSAGE_ID, messageId)
                }
            }
            val textCopyNumber = context.getString(com.goodwy.commons.R.string.copy) + " \"${number}\""
            val copyNumber =
                PendingIntent.getBroadcast(context, threadId.hashCode(), copyNumberIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            builder.addAction(com.goodwy.commons.R.drawable.ic_copy_vector, textCopyNumber, copyNumber)
                .setChannelId(notificationChannelId)
        }

//        if (isNoReplySms) {
            builder.addAction(
                com.goodwy.commons.R.drawable.ic_delete_outline,
                context.getString(com.goodwy.commons.R.string.delete),
                deleteSmsPendingIntent
            ).setChannelId(notificationChannelId)
//        }

        var shortcut = context.shortcutHelper.getShortcut(threadId)
        if (shortcut == null) {
            ensureBackgroundThread {
                notificationManager.notify(notificationId, builder.build())
                //The shortcut icon replaces notification icons if the shortcut was previously launched by notifications
                shortcut = context.shortcutHelper.createOrUpdateShortcut(threadId)
                builder.setShortcutInfo(shortcut)
                context.shortcutHelper.reportReceiveMessageUsage(threadId)
            }
        } else {
            notificationManager.notify(notificationId, builder.build())
            //The shortcut icon replaces notification icons if the shortcut was previously launched by notifications
            builder.setShortcutInfo(shortcut)
            ensureBackgroundThread {
                context.shortcutHelper.reportReceiveMessageUsage(threadId)
            }
        }
    }

    @SuppressLint("NewApi")
    fun showSendingFailedNotification(recipientName: String, threadId: Long) {
        val hasCustomNotifications =
            context.config.customNotifications.contains(threadId.toString())
        val notificationChannelId =
            if (hasCustomNotifications) threadId.toString() else NOTIFICATION_CHANNEL
        if (!hasCustomNotifications) {
            createChannel(notificationChannelId, context.getString(R.string.message_not_sent_short))
        }

        val notificationId = generateRandomId().hashCode()
        val intent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val summaryText =
            String.format(context.getString(R.string.message_sending_error), recipientName)
        val largeIcon = SimpleContactsHelper(context).getContactLetterIcon(recipientName)
        val builder = NotificationCompat.Builder(context, notificationChannelId)
            .setContentTitle(context.getString(R.string.message_not_sent_short))
            .setContentText(summaryText)
            .setColor(context.getProperPrimaryColor())
            .setSmallIcon(R.drawable.ic_messages)
            .setLargeIcon(largeIcon)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setChannelId(notificationChannelId)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun createChannel(id: String, name: String) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()

        val importance = IMPORTANCE_HIGH
        NotificationChannel(id, name, importance).apply {
            setBypassDnd(false)
            enableLights(true)
            setSound(soundUri, audioAttributes)
            enableVibration(true)
            notificationManager.createNotificationChannel(this)
        }
    }

    private fun getMessagesStyle(
        address: String,
        body: String,
        notificationId: Int,
        name: String?
    ): NotificationCompat.MessagingStyle {
        val sender = if (name != null) {
            Person.Builder()
                .setName(name)
                .setKey(address)
                .build()
        } else {
            null
        }

        return NotificationCompat.MessagingStyle(user).also { style ->
            getOldMessages(notificationId).forEach {
                style.addMessage(it)
            }
            val newMessage =
                NotificationCompat.MessagingStyle.Message(body, System.currentTimeMillis(), sender)
            style.addMessage(newMessage)
        }
    }

    private fun getOldMessages(notificationId: Int): List<NotificationCompat.MessagingStyle.Message> {
        val currentNotification =
            notificationManager.activeNotifications.find { it.id == notificationId }
        return if (currentNotification != null) {
            val activeStyle =
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(
                    currentNotification.notification
                )
            activeStyle?.messages.orEmpty()
        } else {
            emptyList()
        }
    }

    @SuppressLint("NewApi")
    fun showMmsReceivedFailedNotification() {
        val notificationChannelId = NOTIFICATION_CHANNEL
        createChannel(notificationChannelId, context.getString(R.string.couldnt_download_mms))

        val notificationId = generateRandomId().hashCode()
        val intent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val builder = NotificationCompat.Builder(context, notificationChannelId)
            .setContentTitle(context.getString(R.string.couldnt_download_mms))
            .setColor(context.getProperPrimaryColor())
            .setSmallIcon(R.drawable.ic_messages)
            .setStyle(NotificationCompat.BigTextStyle())
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setChannelId(notificationChannelId)

        notificationManager.notify(notificationId, builder.build())
    }
}

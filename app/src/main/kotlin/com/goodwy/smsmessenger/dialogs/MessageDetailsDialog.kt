package com.goodwy.smsmessenger.dialogs

import android.annotation.SuppressLint
import android.provider.Telephony.Sms
import android.telephony.SubscriptionInfo
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.dialogs.BasePropertiesDialog
import com.goodwy.commons.extensions.formatDateOrTime
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.getTimeFormatWithSeconds
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.extensions.subscriptionManagerCompat
import com.goodwy.smsmessenger.models.Message
import org.joda.time.DateTime

class MessageDetailsDialog(val activity: BaseSimpleActivity, val message: Message) : BasePropertiesDialog(activity) {
    init {
        addProperty(R.string.message_type, if (message.isMMS) "MMS" else "SMS")
        addProperty(com.goodwy.strings.R.string.status, message.getStatus())

        @SuppressLint("MissingPermission")
        val availableSIMs = activity.subscriptionManagerCompat().activeSubscriptionInfoList.orEmpty()

        addProperty(message.getSenderOrReceiverLabel(), message.getSenderOrReceiverPhoneNumbers())
        if (availableSIMs.count() > 1) {
            addProperty(R.string.message_details_sim, message.getSIM(availableSIMs))
        }
        addProperty(message.getSentOrReceivedAtLabel(), message.getSentOrReceivedAt())

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok) { _, _ -> }
            .apply {
                activity.setupDialogStuff(mDialogView.root, this, R.string.message_details)
            }
    }

    private fun Message.getSenderOrReceiverLabel(): Int {
        return if (isReceivedMessage()) {
            R.string.message_details_sender
        } else {
            R.string.message_details_receiver
        }
    }

    private fun Message.getSenderOrReceiverPhoneNumbers(): String {
        return if (isReceivedMessage()) {
            formatContactInfo(senderName, senderPhoneNumber)
        } else {
            participants.joinToString(", ") {
                formatContactInfo(it.name, it.phoneNumbers.first().value)
            }
        }
    }

    private fun formatContactInfo(name: String, phoneNumber: String): String {
        return if (name != phoneNumber) {
            "$name ($phoneNumber)"
        } else {
            phoneNumber
        }
    }

    private fun Message.getSIM(availableSIMs: List<SubscriptionInfo>): String {
        return availableSIMs.firstOrNull { it.subscriptionId == subscriptionId }?.displayName?.toString()
            ?: activity.getString(com.goodwy.commons.R.string.unknown)
    }

    private fun Message.getSentOrReceivedAtLabel(): Int {
        return if (isReceivedMessage()) {
            R.string.message_details_received_at
        } else {
            R.string.message_details_sent_at
        }
    }

    private fun Message.getSentOrReceivedAt(): String {
//        return DateTime(date * 1000L).toString("${activity.config.dateFormat} ${activity.getTimeFormatWithSeconds()}")
        return (date * 1000L).formatDateOrTime(
            context = activity,
            hideTimeOnOtherDays = false,
            showCurrentYear = true,
            hideTodaysDate = false,
            timeFormat = activity.getTimeFormatWithSeconds()
        )
    }

    private fun Message.getStatus(): String {
        return when (status) {
            Sms.STATUS_COMPLETE -> activity.getString(R.string.delivered)
            Sms.STATUS_FAILED -> activity.getString(R.string.failed)
            Sms.STATUS_PENDING -> activity.getString(R.string.pending)
            else -> activity.getString(com.goodwy.commons.R.string.unknown)
        }
    }
}

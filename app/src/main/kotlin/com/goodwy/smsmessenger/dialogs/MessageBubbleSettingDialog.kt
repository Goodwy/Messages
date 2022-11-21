package com.goodwy.smsmessenger.dialogs

import android.content.res.ColorStateList
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.isTiramisuPlus
import com.goodwy.commons.helpers.letterBackgroundColors
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.activities.SimpleActivity
import com.goodwy.smsmessenger.extensions.config
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.android.synthetic.main.dialog_message_bubble_setting.view.*
import java.io.File
import java.util.*

class MessageBubbleSettingDialog(
    private val activity: SimpleActivity,
    private val callback: (file: File) -> Unit,
) {
    private val config = activity.config

    init {
        val view = (activity.layoutInflater.inflate(R.layout.dialog_message_bubble_setting, null) as ViewGroup).apply {
            setupBubbleInvertColor(this)
            setupColors(this)
            setupToggleBubbleStyle(this)

            if (config.isUsingSystemTheme) {
                val drawable = resources.getColoredDrawableWithColor(R.drawable.rounded_rectangle_color, context.getProperBackgroundColor())
                style_original.background = drawable
                style_ios.background = drawable
            }
            style_original.setOnClickListener {
                config.bubbleStyle = 0
                setupToggleBubbleStyle(this, 0)
            }
            style_ios.setOnClickListener {
                config.bubbleStyle = 1
                setupToggleBubbleStyle(this, 1)
            }
        }

        activity.getAlertDialogBuilder()
            .setNegativeButton(R.string.ok, null)
            .apply {
                activity.setupDialogStuff(view, this, R.string.speech_bubble) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    }
                }
            }
    }

    private fun setupToggleBubbleStyle(view: ViewGroup, style: Int = config.bubbleStyle) {
        view.apply {
            style_original_check.isActivated = style == 0
            style_ios_check.isActivated = style == 1
            val states = arrayOf(
                intArrayOf(android.R.attr.state_activated),
                intArrayOf(-android.R.attr.state_activated))
            arrayOf(style_original_check, style_ios_check).forEach {
                it.imageTintList = ColorStateList(states, intArrayOf(context.getProperPrimaryColor(), context.getProperTextColor()))
            }
        }
    }

    private fun setupBubbleInvertColor(view: ViewGroup) {
        view.apply {
            bubble_invert_color.isChecked = config.bubbleInvertColor
            bubble_invert_color_holder.setOnClickListener {
                bubble_invert_color.toggle()
                config.bubbleInvertColor = bubble_invert_color.isChecked
                setupColors(view)
            }
        }
    }

    private fun setupColors(view: ViewGroup) {
        view.apply {
            val backgroundReceived = if (context.config.bubbleInvertColor) context.getProperPrimaryColor() else context.getBottomNavigationBackgroundColor()
            val contrastColorReceived = backgroundReceived.getContrastColor()
            style_original_bubble_one.background.applyColorFilter(backgroundReceived)
            style_original_bubble_two.background.applyColorFilter(backgroundReceived)
            style_ios_bubble_one.background.applyColorFilter(backgroundReceived)
            style_ios_bubble_two.background.applyColorFilter(backgroundReceived)
            style_original_bubble_one.setTextColor(contrastColorReceived)
            style_original_bubble_two.setTextColor(contrastColorReceived)
            style_ios_bubble_one.setTextColor(contrastColorReceived)
            style_ios_bubble_two.setTextColor(contrastColorReceived)

            val backgroundSender = if (context.config.bubbleInvertColor) context.getBottomNavigationBackgroundColor() else context.getProperPrimaryColor()
            val contrastColorSender = backgroundSender.getContrastColor()
            style_original_bubble_three.background.applyColorFilter(backgroundSender)
            style_ios_bubble_three.background.applyColorFilter(backgroundSender)
            style_original_bubble_three.setTextColor(contrastColorSender)
            style_ios_bubble_three.setTextColor(contrastColorSender)
        }
    }
}

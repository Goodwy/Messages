package com.goodwy.smsmessenger.dialogs

import android.content.res.ColorStateList
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.extensions.*
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.activities.SimpleActivity
import com.goodwy.smsmessenger.databinding.DialogMessageBubbleSettingBinding
import com.goodwy.smsmessenger.extensions.config
import java.io.File

class MessageBubbleSettingDialog(
    private val activity: SimpleActivity,
    private val callback: (file: File) -> Unit,
) {
    private val binding = DialogMessageBubbleSettingBinding.inflate(activity.layoutInflater)
    private val config = activity.config

    init {
        setupBubbleInvertColor()
        setupColors()
        setupToggleBubbleStyle()

        if (config.isUsingSystemTheme || activity.isBlackTheme()) {
            val drawable = binding.root.resources.getColoredDrawableWithColor(R.drawable.rounded_rectangle_color, binding.root.context.getProperBackgroundColor())
            binding.styleOriginal.background = drawable
            binding.styleIos.background = drawable
        }
        binding.styleOriginal.setOnClickListener {
            config.bubbleStyle = 0
            setupToggleBubbleStyle(0)
        }
        binding.styleIos.setOnClickListener {
            config.bubbleStyle = 1
            setupToggleBubbleStyle(1)
        }

        activity.getAlertDialogBuilder()
            .setNegativeButton(com.goodwy.commons.R.string.ok, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, com.goodwy.commons.R.string.speech_bubble) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    }
                }
            }
    }

    private fun setupToggleBubbleStyle(style: Int = config.bubbleStyle) {
        binding.styleOriginalCheck.isActivated = style == 0
        binding.styleIosCheck.isActivated = style == 1
        val states = arrayOf(
            intArrayOf(android.R.attr.state_activated),
            intArrayOf(-android.R.attr.state_activated))
        arrayOf(binding.styleOriginalCheck, binding.styleIosCheck).forEach {
            it.imageTintList = ColorStateList(states, intArrayOf(binding.root.context.getProperPrimaryColor(), binding.root.context.getProperTextColor()))
        }
    }

    private fun setupBubbleInvertColor() {
        binding.bubbleInvertColor.isChecked = config.bubbleInvertColor
        binding.bubbleInvertColorHolder.setOnClickListener {
            binding.bubbleInvertColor.toggle()
            config.bubbleInvertColor = binding.bubbleInvertColor.isChecked
            setupColors()
        }
    }

    private fun setupColors() {
        binding.apply {
            val backgroundReceived = if (root.context.config.bubbleInvertColor) root.context.getProperPrimaryColor() else root.context.getBottomNavigationBackgroundColor()
            val contrastColorReceived = backgroundReceived.getContrastColor()
            styleOriginalBubbleOne.background.applyColorFilter(backgroundReceived)
            styleOriginalBubbleTwo.background.applyColorFilter(backgroundReceived)
            styleIosBubbleOne.background.applyColorFilter(backgroundReceived)
            styleIosBubbleTwo.background.applyColorFilter(backgroundReceived)
            styleOriginalBubbleOne.setTextColor(contrastColorReceived)
            styleOriginalBubbleTwo.setTextColor(contrastColorReceived)
            styleIosBubbleOne.setTextColor(contrastColorReceived)
            styleIosBubbleTwo.setTextColor(contrastColorReceived)

            val backgroundSender = if (root.context.config.bubbleInvertColor) root.context.getBottomNavigationBackgroundColor() else root.context.getProperPrimaryColor()
            val contrastColorSender = backgroundSender.getContrastColor()
            styleOriginalBubbleThree.background.applyColorFilter(backgroundSender)
            styleIosBubbleThree.background.applyColorFilter(backgroundSender)
            styleOriginalBubbleThree.setTextColor(contrastColorSender)
            styleIosBubbleThree.setTextColor(contrastColorSender)
        }
    }
}

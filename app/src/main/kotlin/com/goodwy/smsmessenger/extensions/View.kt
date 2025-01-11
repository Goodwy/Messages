package com.goodwy.smsmessenger.extensions

import android.animation.ObjectAnimator
import android.app.Activity
import android.view.View
import androidx.core.animation.doOnStart
import androidx.core.view.isVisible
import com.goodwy.commons.extensions.isRTLLayout
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.helpers.BUBBLE_STYLE_IOS
import com.goodwy.smsmessenger.helpers.BUBBLE_STYLE_IOS_NEW

fun View.showWithAnimation(duration: Long = 250L) {
    if (!isVisible) {
        ObjectAnimator.ofFloat(
            this, "alpha", 0f, 1f
        ).apply {
            this.duration = duration
            doOnStart { visibility = View.VISIBLE }
        }.start()
    }
}

fun View.setPaddingBubble(activity: Activity, bubbleStyle: Int, isReceived: Boolean = true) {
    val isRtl = activity.isRTLLayout
    if (isReceived) {
        when (bubbleStyle) {
            BUBBLE_STYLE_IOS -> {
                val paddingTop = resources.getDimensionPixelOffset(com.goodwy.commons.R.dimen.medium_margin)
                val paddingLeft =
                    if (isRtl) resources.getDimensionPixelOffset(R.dimen.bubble_padding_right_ios) else resources.getDimensionPixelOffset(R.dimen.bubble_padding_bottom)
                val paddingRight =
                    if (isRtl) resources.getDimensionPixelOffset(R.dimen.bubble_padding_bottom) else resources.getDimensionPixelOffset(R.dimen.bubble_padding_right_ios)
                val paddingBottom = resources.getDimensionPixelOffset(R.dimen.bubble_padding_bottom)
                setPadding(paddingRight, paddingTop, paddingLeft, paddingBottom)
            }

            BUBBLE_STYLE_IOS_NEW -> {
                val paddingTop = resources.getDimensionPixelOffset(com.goodwy.commons.R.dimen.medium_margin)
                val paddingBottom = resources.getDimensionPixelOffset(com.goodwy.commons.R.dimen.ten_dpi)
                val paddingLeft =
                    if (isRtl) resources.getDimensionPixelOffset(R.dimen.bubble_padding_right_ios) else resources.getDimensionPixelOffset(R.dimen.bubble_padding_bottom)
                val paddingRight =
                    if (isRtl) resources.getDimensionPixelOffset(R.dimen.bubble_padding_bottom) else resources.getDimensionPixelOffset(R.dimen.bubble_padding_right_ios)
                setPadding(paddingRight, paddingTop, paddingLeft, paddingBottom)
            }

            else -> {
                val paddingTop = resources.getDimensionPixelOffset(com.goodwy.commons.R.dimen.medium_margin)
                val paddingBottom = resources.getDimensionPixelOffset(com.goodwy.commons.R.dimen.ten_dpi)
                val paddingHorizontal = resources.getDimensionPixelOffset(R.dimen.bubble_padding_bottom)
                setPadding(paddingHorizontal, paddingTop, paddingHorizontal, paddingBottom)
            }
        }
    } else {
        when (bubbleStyle) {
            BUBBLE_STYLE_IOS -> {
                val paddingTop = resources.getDimensionPixelOffset(com.goodwy.commons.R.dimen.medium_margin)
                val paddingLeft = if (isRtl) resources.getDimensionPixelOffset(R.dimen.bubble_padding_right_ios) else resources.getDimensionPixelOffset(R.dimen.bubble_padding_bottom)
                val paddingRight = if (isRtl) resources.getDimensionPixelOffset(R.dimen.bubble_padding_bottom) else resources.getDimensionPixelOffset(R.dimen.bubble_padding_right_ios)
                val paddingBottom = resources.getDimensionPixelOffset(R.dimen.bubble_padding_bottom)
                setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
            }
            BUBBLE_STYLE_IOS_NEW -> {
                val paddingTop = resources.getDimensionPixelOffset(com.goodwy.commons.R.dimen.medium_margin)
                val paddingBottom = resources.getDimensionPixelOffset(com.goodwy.commons.R.dimen.ten_dpi)
                val paddingLeft = if (isRtl) resources.getDimensionPixelOffset(R.dimen.bubble_padding_right_ios) else resources.getDimensionPixelOffset(R.dimen.bubble_padding_bottom)
                val paddingRight = if (isRtl) resources.getDimensionPixelOffset(R.dimen.bubble_padding_bottom) else resources.getDimensionPixelOffset(R.dimen.bubble_padding_right_ios)
                setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
            }
            else -> {
                val paddingTop = resources.getDimensionPixelOffset(com.goodwy.commons.R.dimen.medium_margin)
                val paddingBottom = resources.getDimensionPixelOffset(com.goodwy.commons.R.dimen.ten_dpi)
                val paddingHorizontal = resources.getDimensionPixelOffset(R.dimen.bubble_padding_bottom)
                setPadding(paddingHorizontal, paddingTop, paddingHorizontal, paddingBottom)
            }
        }
    }
}


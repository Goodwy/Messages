package com.goodwy.smsmessenger.extensions

import android.text.TextUtils
import com.goodwy.commons.models.SimpleContact

fun ArrayList<SimpleContact>.getThreadTitle(): String = TextUtils.join(", ", map { it.name }.toTypedArray()).orEmpty()

fun ArrayList<SimpleContact>.getAddresses() = flatMap { it.phoneNumbers }.map { it.normalizedNumber }

fun ArrayList<SimpleContact>.getThreadSubtitle(): String = TextUtils.join(", ", map { it.phoneNumbers.first().normalizedNumber }.toTypedArray())

package com.goodwy.smsmessenger.helpers

import android.content.Context
import android.net.Uri
import com.goodwy.commons.helpers.ensureBackgroundThread
import ezvcard.Ezvcard
import ezvcard.VCard

fun parseVCardFromUri(context: Context, uri: Uri, callback: (vCards: List<VCard>) -> Unit) {
    ensureBackgroundThread {
        val inputStream = context.contentResolver.openInputStream(uri)
        val vCards = Ezvcard.parse(inputStream).all()
        callback(vCards)
    }
}

fun VCard?.parseNameFromVCard(): String? {
    if (this == null) return null
    var fullName = formattedName?.value
    if (fullName.isNullOrEmpty()) {
        val structured = structuredName ?: return null
        val nameComponents = arrayListOf<String?>().apply {
            addAll(structured.prefixes)
            add(structured.given)
            addAll(structured.additionalNames)
            add(structured.family)
            addAll(structured.suffixes)
        }
        fullName = nameComponents.filter { !it.isNullOrEmpty() }.joinToString(separator = " ")
    }
    return fullName
}

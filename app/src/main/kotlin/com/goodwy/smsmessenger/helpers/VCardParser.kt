package com.goodwy.smsmessenger.helpers

import android.content.Context
import android.net.Uri
import com.goodwy.commons.helpers.ensureBackgroundThread
import ezvcard.Ezvcard
import ezvcard.VCard

fun parseVCardFromUri(context: Context, uri: Uri, callback: (vCards: List<VCard>) -> Unit) {
    ensureBackgroundThread {
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            callback(emptyList())
            return@ensureBackgroundThread
        }
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
    if (fullName.isEmpty()) {
        val organization = organization ?: return null
        val organizationComponents = arrayListOf<String?>().apply {
            addAll(organization.values)
        }
        fullName = organizationComponents.filter { !it.isNullOrEmpty() }.joinToString(separator = " ")
    }
    if (fullName.isEmpty()) {
        val jobPosition = titles ?: return null
        val jobPositionComponents = arrayListOf<String?>().apply {
            add(jobPosition.firstOrNull()?.value ?: "")
        }
        fullName = jobPositionComponents.filter { !it.isNullOrEmpty() }.joinToString(separator = " ")
    }
    return fullName
}

fun VCard?.isCompanyVCard(fullName: String): Boolean {
    if (this == null) return false

    val organization = organization
    val organizationComponents = arrayListOf<String?>().apply {
        if (organization != null) addAll(organization.values)
    }
    val company = organizationComponents.filter { !it.isNullOrEmpty() }.joinToString(separator = " ")

    val jobPosition = titles
    val jobPositionComponents = arrayListOf<String?>().apply {
        if (jobPosition != null) add(jobPosition.firstOrNull()?.value ?: "")
    }
    val job = jobPositionComponents.filter { !it.isNullOrEmpty() }.joinToString(separator = " ")

    return fullName == company ||
        fullName == job ||
        fullName.filter { !it.isWhitespace() } == "$company,$job".filter { !it.isWhitespace() }
}

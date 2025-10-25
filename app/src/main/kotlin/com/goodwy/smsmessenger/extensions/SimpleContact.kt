package com.goodwy.smsmessenger.extensions

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.provider.ContactsContract
import android.text.TextUtils
import androidx.core.app.Person
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.models.SimpleContact
import androidx.core.net.toUri
import com.goodwy.commons.extensions.applyColorFilter
import com.goodwy.commons.extensions.baseConfig
import com.goodwy.commons.extensions.convertToBitmap
import com.goodwy.commons.extensions.getLetterBackgroundColors
import com.goodwy.commons.extensions.toast
import com.goodwy.smsmessenger.R
import kotlin.math.abs

fun ArrayList<SimpleContact>.getThreadTitle(): String {
    return TextUtils.join(", ", map { it.name }.toTypedArray()).orEmpty()
}

fun ArrayList<SimpleContact>.getAddresses(): List<String> {
    return flatMap { it.phoneNumbers }.map { it.normalizedNumber }
}

fun ArrayList<SimpleContact>.getThreadSubtitle(): String {
    return TextUtils.join(", ", map { it.phoneNumbers.first().normalizedNumber }.toTypedArray())
}

fun SimpleContact.toPerson(context: Context? = null): Person {
    val uri =
        Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, contactId.toString())
    val iconCompat = if (context != null) {
        loadIcon(context)
    } else {
        IconCompat.createWithContentUri(photoUri)
    }

    return Person.Builder()
        .setName(name)
        .setUri(uri.toString())
        .setIcon(iconCompat)
        .setKey(uri.toString())
        .build()
}

fun SimpleContact.loadIcon(context: Context): IconCompat {
    try {
        val stream = context.contentResolver.openInputStream(photoUri.toUri())
        val bitmap = BitmapFactory.decodeStream(stream)
        stream?.close()
        val iconCompat = IconCompat.createWithAdaptiveBitmap(bitmap)
        return iconCompat
    } catch (_: Exception) {
        return if (isABusinessContact()) {
            IconCompat.createWithBitmap(
                SimpleContactsHelper(context).getColoredCompanyIcon(name).toBitmap()
            )
        } else {
            IconCompat.createWithBitmap(
                SimpleContactsHelper(context).getContactLetterIcon(name)
            )
        }
    }
}

package com.goodwy.smsmessenger

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import com.goodwy.commons.RightApp
import com.goodwy.commons.helpers.PERMISSION_READ_CONTACTS
import com.goodwy.commons.helpers.PurchaseHelper
import com.goodwy.commons.extensions.hasPermission
import com.goodwy.smsmessenger.helpers.MessagingCache

class App : RightApp() {
    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        PurchaseHelper().initPurchaseIfNeed(this, "685530047")

        if (hasPermission(PERMISSION_READ_CONTACTS)) {
            listOf(
                ContactsContract.Contacts.CONTENT_URI,
                ContactsContract.Data.CONTENT_URI,
                ContactsContract.DisplayPhoto.CONTENT_URI
            ).forEach {
                try {
                    contentResolver.registerContentObserver(it, true, contactsObserver)
                } catch (_: Exception){
                }
            }
        }
    }

    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            MessagingCache.namePhoto.evictAll()
            MessagingCache.participantsCache.evictAll()
        }
    }
}

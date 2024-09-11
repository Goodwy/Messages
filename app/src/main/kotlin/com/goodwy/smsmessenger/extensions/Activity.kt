package com.goodwy.smsmessenger.extensions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.ContactsContract
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import com.goodwy.commons.dialogs.NewAppDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.SimpleContact
import com.goodwy.smsmessenger.BuildConfig
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.activities.SimpleActivity
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

fun SimpleActivity.dialNumber(phoneNumber: String, callback: (() -> Unit)? = null) {
    hideKeyboard()
//    Intent(Intent.ACTION_DIAL).apply {
//        data = Uri.fromParts("tel", phoneNumber, null)
//
//        try {
//            startActivity(this)
//            callback?.invoke()
//        } catch (e: ActivityNotFoundException) {
//            toast(com.goodwy.commons.R.string.no_app_found)
//        } catch (e: Exception) {
//            showErrorToast(e)
//        }
//    }

    handlePermission(PERMISSION_CALL_PHONE) {
        val action = if (it) Intent.ACTION_CALL else Intent.ACTION_DIAL
        Intent(action).apply {
            data = Uri.fromParts("tel", phoneNumber, null)
            putExtra(IS_RIGHT_APP, BuildConfig.RIGHT_APP_KEY)

            try {
                launchActivityIntent(this)
                callback?.invoke()
            } catch (e: ActivityNotFoundException) {
                toast(com.goodwy.commons.R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }
}

fun Activity.launchViewIntent(uri: Uri, mimetype: String, filename: String) {
    Intent().apply {
        action = Intent.ACTION_VIEW
        setDataAndType(uri, mimetype.lowercase(Locale.getDefault()))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            hideKeyboard()
            startActivity(this)
        } catch (e: ActivityNotFoundException) {
            val newMimetype = filename.getMimeType()
            if (newMimetype.isNotEmpty() && mimetype != newMimetype) {
                launchViewIntent(uri, newMimetype, filename)
            } else {
                toast(com.goodwy.commons.R.string.no_app_found)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}

fun Activity.startContactDetailsIntent(contact: SimpleContact) {
    val simpleContacts = "com.goodwy.contacts"
    val simpleContactsDebug = "com.goodwy.contacts.debug"
    if ((0..config.appRecommendationDialogCount).random() == 2 && (!isPackageInstalled(simpleContacts) && !isPackageInstalled(simpleContactsDebug))) {
        NewAppDialog(this, simpleContacts, getString(com.goodwy.strings.R.string.recommendation_dialog_contacts_g), getString(com.goodwy.commons.R.string.right_contacts),
            AppCompatResources.getDrawable(this, com.goodwy.commons.R.drawable.ic_contacts)) {}
    } else {
        if (contact.rawId > 1000000 && contact.contactId > 1000000 && contact.rawId == contact.contactId &&
            (isPackageInstalled(simpleContacts) || isPackageInstalled(simpleContactsDebug))
        ) {
            Intent().apply {
                action = Intent.ACTION_VIEW
                putExtra(CONTACT_ID, contact.rawId)
                putExtra(IS_PRIVATE, true)
                `package` = if (isPackageInstalled(simpleContacts)) simpleContacts else simpleContactsDebug
                setDataAndType(ContactsContract.Contacts.CONTENT_LOOKUP_URI, "vnd.android.cursor.dir/person")
                launchActivityIntent(this)
            }
        } else {
            ensureBackgroundThread {
                val lookupKey = SimpleContactsHelper(this).getContactLookupKey((contact).rawId.toString())
                val publicUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
                runOnUiThread {
                    launchViewContactIntent(publicUri)
                }
            }
        }
    }
}

fun SimpleActivity.launchPurchase() {
    val productIdX1 = BuildConfig.PRODUCT_ID_X1
    val productIdX2 = BuildConfig.PRODUCT_ID_X2
    val productIdX3 = BuildConfig.PRODUCT_ID_X3
    val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3

    startPurchaseActivity(
        R.string.app_name_g,
        productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
        productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
        subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
        subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
        playStoreInstalled = isPlayStoreInstalled(),
        ruStoreInstalled = isRuStoreInstalled()
    )
}

fun SimpleActivity.showSnackbar(view: View) {
    view.performHapticFeedback()

    val snackbar = Snackbar.make(view, com.goodwy.strings.R.string.support_project_to_unlock, Snackbar.LENGTH_SHORT)
        .setAction(com.goodwy.commons.R.string.support) {
            launchPurchase()
        }

    val bgDrawable = ResourcesCompat.getDrawable(view.resources, com.goodwy.commons.R.drawable.button_background_16dp, null)
    snackbar.view.background = bgDrawable
    val properBackgroundColor = getProperBackgroundColor()
    val backgroundColor = if (properBackgroundColor == Color.BLACK) getBottomNavigationBackgroundColor().lightenColor(6) else getBottomNavigationBackgroundColor().darkenColor(6)
    snackbar.setBackgroundTint(backgroundColor)
    snackbar.setTextColor(getProperTextColor())
    snackbar.setActionTextColor(getProperPrimaryColor())
    snackbar.show()
}

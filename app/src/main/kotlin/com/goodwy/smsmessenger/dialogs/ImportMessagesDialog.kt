package com.goodwy.smsmessenger.dialogs

import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.commons.extensions.toast
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.activities.SimpleActivity
import com.goodwy.smsmessenger.extensions.config
import com.goodwy.smsmessenger.helpers.MessagesImporter
import com.goodwy.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_OK
import com.goodwy.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_PARTIAL
import kotlinx.android.synthetic.main.dialog_import_messages.view.*

class ImportMessagesDialog(
    private val activity: SimpleActivity,
    private val path: String,
) {

    private val config = activity.config

    init {
        var ignoreClicks = false
        val view = (activity.layoutInflater.inflate(R.layout.dialog_import_messages, null) as ViewGroup).apply {
            import_sms_checkbox.isChecked = config.importSms
            import_mms_checkbox.isChecked = config.importMms
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, R.string.import_messages) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (ignoreClicks) {
                            return@setOnClickListener
                        }

                        if (!view.import_sms_checkbox.isChecked && !view.import_mms_checkbox.isChecked) {
                            activity.toast(R.string.no_option_selected)
                            return@setOnClickListener
                        }

                        ignoreClicks = true
                        activity.toast(R.string.importing)
                        config.importSms = view.import_sms_checkbox.isChecked
                        config.importMms = view.import_mms_checkbox.isChecked
                        ensureBackgroundThread {
                            MessagesImporter(activity).importMessages(path) {
                                handleParseResult(it)
                                alertDialog.dismiss()
                            }
                        }
                    }
                }
            }
    }

    private fun handleParseResult(result: MessagesImporter.ImportResult) {
        activity.toast(
            when (result) {
                IMPORT_OK -> R.string.importing_successful
                IMPORT_PARTIAL -> R.string.importing_some_entries_failed
                else -> R.string.no_items_found
            }
        )
    }
}

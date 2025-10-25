package com.goodwy.smsmessenger.dialogs

import android.annotation.SuppressLint
import android.net.Uri
import android.provider.DocumentsContract
import androidx.appcompat.app.AlertDialog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.getCurrentFormattedDateTime
import com.goodwy.commons.extensions.getProperPrimaryColor
import com.goodwy.commons.extensions.isAValidFilename
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.commons.extensions.showErrorToast
import com.goodwy.commons.extensions.toast
import com.goodwy.commons.extensions.value
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.activities.SimpleActivity
import com.goodwy.smsmessenger.databinding.DialogExportMessagesBinding
import com.goodwy.smsmessenger.extensions.config
import com.goodwy.smsmessenger.helpers.MessagesReader

class ExportMessagesDialog(
    private val activity: SimpleActivity,
    private val callback: (fileName: String) -> Unit,
) {
    private val config = activity.config
    private var dialog: AlertDialog? = null

    @SuppressLint("SetTextI18n")
    private val binding = DialogExportMessagesBinding.inflate(activity.layoutInflater).apply {
        exportSmsCheckbox.isChecked = config.exportSms
        exportMmsCheckbox.isChecked = config.exportMms
        exportMessagesFilename.setText(
            "${activity.getString(R.string.messages)}_${getCurrentFormattedDateTime()}"
        )
    }

    init {
        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok, null)
            .setNegativeButton(com.goodwy.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(
                    view = binding.root,
                    dialog = this,
                    titleId = R.string.export_messages
                ) { alertDialog ->
                    dialog = alertDialog
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        config.exportSms = binding.exportSmsCheckbox.isChecked
                        config.exportMms = binding.exportMmsCheckbox.isChecked
                        val filename = binding.exportMessagesFilename.value
                        when {
                            filename.isEmpty() -> activity.toast(com.goodwy.commons.R.string.empty_name)
                            filename.isAValidFilename() -> callback(filename)

                            else -> activity.toast(com.goodwy.commons.R.string.invalid_name)
                        }
                    }
                }
            }
    }

    fun exportMessages(uri: Uri) {
        dialog!!.apply {
            setCanceledOnTouchOutside(false)
            arrayOf(
                binding.exportMmsCheckbox,
                binding.exportSmsCheckbox,
                getButton(AlertDialog.BUTTON_POSITIVE),
                getButton(AlertDialog.BUTTON_NEGATIVE)
            ).forEach {
                it.isEnabled = false
                it.alpha = 0.6f
            }

            binding.exportProgress.setIndicatorColor(activity.getProperPrimaryColor())
            binding.exportProgress.post {
                binding.exportProgress.show()
            }
            export(uri)
        }
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun export(uri: Uri) {
        ensureBackgroundThread {
            var success = false
            try {
                MessagesReader(activity).getMessagesToExport(
                    getSms = config.exportSms,
                    getMms = config.exportMms
                ) { messagesToExport ->
                    if (messagesToExport.isEmpty()) {
                        activity.toast(com.goodwy.commons.R.string.no_entries_for_exporting)
                        dismiss()
                        return@getMessagesToExport
                    }
                    val json = Json { encodeDefaults = true }
                    activity.contentResolver.openOutputStream(uri)!!.buffered()
                        .use { outputStream ->
                            json.encodeToStream(messagesToExport, outputStream)
                        }
                    success = true
                    activity.toast(com.goodwy.commons.R.string.exporting_successful)
                }
            } catch (e: Throwable) {
                activity.showErrorToast(e.toString())
            } finally {
                if (!success) {
                    // delete the file to avoid leaving behind an empty/corrupt file
                    try {
                        DocumentsContract.deleteDocument(activity.contentResolver, uri)
                    } catch (_: Exception) {
                        // ignored because we don't want to show two error messages
                    }
                }

                dismiss()
            }
        }
    }

    private fun dismiss() = dialog?.dismiss()
}

package com.goodwy.smsmessenger.dialogs

import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.smsmessenger.databinding.DialogInvalidNumberBinding

class InvalidNumberDialog(val activity: BaseSimpleActivity, val text: String) {
    init {
        val binding = DialogInvalidNumberBinding.inflate(activity.layoutInflater).apply {
            dialogInvalidNumberDesc.text = text
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok) { _, _ -> }
            .apply {
                activity.setupDialogStuff(binding.root, this)
            }
    }
}

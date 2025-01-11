package com.goodwy.smsmessenger.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.goodwy.commons.dialogs.ExportBlockedNumbersDialog
import com.goodwy.commons.dialogs.FilePickerDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.interfaces.RefreshRecyclerViewListener
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.databinding.ActivityManageBlockedKeywordsBinding
import com.goodwy.smsmessenger.dialogs.AddBlockedKeywordDialog
import com.goodwy.smsmessenger.dialogs.ExportBlockedKeywordsDialog
import com.goodwy.smsmessenger.dialogs.ManageBlockedKeywordsAdapter
import com.goodwy.smsmessenger.extensions.config
import com.goodwy.smsmessenger.extensions.toArrayList
import com.goodwy.smsmessenger.helpers.BlockedKeywordsExporter
import com.goodwy.smsmessenger.helpers.BlockedKeywordsImporter
import java.io.FileOutputStream
import java.io.OutputStream

class ManageBlockedKeywordsActivity : SimpleActivity(), RefreshRecyclerViewListener {

    private val binding by viewBinding(ActivityManageBlockedKeywordsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateBlockedKeywords()
        setupOptionsMenu()

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.blockKeywordsCoordinator,
            nestedView = binding.manageBlockedKeywordsList,
            useTransparentNavigation = true,
            useTopSearchMenu = false
        )
        setupMaterialScrollListener(
            scrollingView = binding.manageBlockedKeywordsList,
            toolbar = binding.blockKeywordsToolbar
        )
        updateTextColors(binding.manageBlockedKeywordsWrapper)

        binding.manageBlockedKeywordsPlaceholder2.apply {
            underlineText()
            setTextColor(getProperPrimaryColor())
            setOnClickListener {
                addOrEditBlockedKeyword()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.blockKeywordsToolbar, NavigationIcon.Arrow)
    }

    private fun setupOptionsMenu() {
        binding.blockKeywordsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_blocked_keyword -> {
                    addOrEditBlockedKeyword()
                    true
                }

                R.id.export_blocked_keywords -> {
                    tryExportBlockedNumbers()
                    true
                }

                R.id.import_blocked_keywords -> {
                    tryImportBlockedKeywords()
                    true
                }

                else -> false
            }
        }
    }

    private val exportActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            try {
                val outputStream = uri?.let { contentResolver.openOutputStream(it) }
                if (outputStream != null) {
                    exportBlockedKeywordsTo(outputStream)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }

    private val importActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            try {
                if (uri != null) {
                    tryImportBlockedKeywordsFromFile(uri)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }

    private fun tryImportBlockedKeywords() {
        if (isQPlus()) {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                val mimeType = "text/plain"
                type = mimeType

                try {
                    importActivityResultLauncher.launch(mimeType)
                } catch (e: ActivityNotFoundException) {
                    toast(com.goodwy.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        } else {
            handlePermission(PERMISSION_READ_STORAGE) { isAllowed ->
                if (isAllowed) {
                    pickFileToImportBlockedKeywords()
                }
            }
        }
    }

    private fun pickFileToImportBlockedKeywords() {
        FilePickerDialog(this) {
            importBlockedKeywords(it)
        }
    }

    private fun tryImportBlockedKeywordsFromFile(uri: Uri) {
        when (uri.scheme) {
            "file" -> importBlockedKeywords(uri.path!!)
            "content" -> {
                val tempFile = getTempFile("blocked", "blocked_keywords.txt")
                if (tempFile == null) {
                    toast(com.goodwy.commons.R.string.unknown_error_occurred)
                    return
                }

                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val out = FileOutputStream(tempFile)
                    inputStream!!.copyTo(out)
                    importBlockedKeywords(tempFile.absolutePath)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }

            else -> toast(com.goodwy.commons.R.string.invalid_file_format)
        }
    }

    private fun importBlockedKeywords(path: String) {
        ensureBackgroundThread {
            val result = BlockedKeywordsImporter(this).importBlockedKeywords(path)
            toast(
                when (result) {
                    BlockedKeywordsImporter.ImportResult.IMPORT_OK -> com.goodwy.commons.R.string.importing_successful
                    BlockedKeywordsImporter.ImportResult.IMPORT_FAIL -> com.goodwy.commons.R.string.no_items_found
                }
            )
            updateBlockedKeywords()
        }
    }

    private fun exportBlockedKeywordsTo(outputStream: OutputStream?) {
        ensureBackgroundThread {
            val blockedKeywords = config.blockedKeywords.toArrayList()
            if (blockedKeywords.isEmpty()) {
                toast(com.goodwy.commons.R.string.no_entries_for_exporting)
            } else {
                BlockedKeywordsExporter.exportBlockedKeywords(blockedKeywords, outputStream) {
                    toast(
                        when (it) {
                            ExportResult.EXPORT_OK -> com.goodwy.commons.R.string.exporting_successful
                            else -> com.goodwy.commons.R.string.exporting_failed
                        }
                    )
                }
            }
        }
    }

    private fun tryExportBlockedNumbers() {
        if (isQPlus()) {
            ExportBlockedKeywordsDialog(this, config.lastBlockedKeywordExportPath, true) { file ->
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, file.name)
                    addCategory(Intent.CATEGORY_OPENABLE)

                    try {
                        exportActivityResultLauncher.launch(file.name)
                    } catch (e: ActivityNotFoundException) {
                        toast(
                            com.goodwy.commons.R.string.system_service_disabled,
                            Toast.LENGTH_LONG
                        )
                    } catch (e: Exception) {
                        showErrorToast(e)
                    }
                }
            }
        } else {
            handlePermission(PERMISSION_WRITE_STORAGE) { isAllowed ->
                if (isAllowed) {
                    ExportBlockedNumbersDialog(
                        this,
                        config.lastBlockedKeywordExportPath,
                        false
                    ) { file ->
                        getFileOutputStream(file.toFileDirItem(this), true) { out ->
                            exportBlockedKeywordsTo(out)
                        }
                    }
                }
            }
        }
    }

    override fun refreshItems() {
        updateBlockedKeywords()
    }

    private fun updateBlockedKeywords() {
        ensureBackgroundThread {
            val blockedKeywords = config.blockedKeywords.sorted().toArrayList()
            runOnUiThread {
                ManageBlockedKeywordsAdapter(
                    activity = this,
                    blockedKeywords = blockedKeywords,
                    listener = this,
                    recyclerView = binding.manageBlockedKeywordsList
                ) {
                    addOrEditBlockedKeyword(it as String)
                }.apply {
                    binding.manageBlockedKeywordsList.adapter = this
                }

                binding.manageBlockedKeywordsPlaceholder.beVisibleIf(blockedKeywords.isEmpty())
                binding.manageBlockedKeywordsPlaceholder2.beVisibleIf(blockedKeywords.isEmpty())
            }
        }
    }

    private fun addOrEditBlockedKeyword(keyword: String? = null) {
        AddBlockedKeywordDialog(this, keyword) {
            updateBlockedKeywords()
        }
    }
}

package com.goodwy.smsmessenger.activities

import android.os.Bundle
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.APP_ICON_IDS
import com.goodwy.commons.helpers.APP_LAUNCHER_NAME
import com.goodwy.commons.helpers.NavigationIcon
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.interfaces.RefreshRecyclerViewListener
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.databinding.ActivityManageBlockedKeywordsBinding
import com.goodwy.smsmessenger.dialogs.AddBlockedKeywordDialog
import com.goodwy.smsmessenger.dialogs.ManageBlockedKeywordsAdapter
import com.goodwy.smsmessenger.extensions.config
import com.goodwy.smsmessenger.extensions.toArrayList

class ManageBlockedKeywordsActivity : BaseSimpleActivity(), RefreshRecyclerViewListener {
    override fun getAppIconIDs() = intent.getIntegerArrayListExtra(APP_ICON_IDS) ?: ArrayList()

    override fun getAppLauncherName() = intent.getStringExtra(APP_LAUNCHER_NAME) ?: ""

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
        setupMaterialScrollListener(scrollingView = binding.manageBlockedKeywordsList, toolbar = binding.blockKeywordsToolbar)
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

                else -> false
            }
        }
    }

    override fun refreshItems() {
        updateBlockedKeywords()
    }

    private fun updateBlockedKeywords() {
        ensureBackgroundThread {
            val blockedKeywords = config.blockedKeywords
            runOnUiThread {
                ManageBlockedKeywordsAdapter(this, blockedKeywords.toArrayList(), this, binding.manageBlockedKeywordsList) {
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

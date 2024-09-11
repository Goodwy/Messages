package com.goodwy.smsmessenger.activities

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.goodwy.commons.activities.ManageBlockedNumbersActivity
import com.goodwy.commons.dialogs.*
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.helpers.rustore.RuStoreHelper
import com.goodwy.commons.helpers.rustore.model.StartPurchasesEvent
import com.goodwy.commons.models.FAQItem
import com.goodwy.commons.models.RadioItem
import com.goodwy.smsmessenger.BuildConfig
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.databinding.ActivitySettingsBinding
import com.goodwy.smsmessenger.dialogs.ExportMessagesDialog
import com.goodwy.smsmessenger.dialogs.MessageBubbleSettingDialog
import com.goodwy.smsmessenger.extensions.*
import com.goodwy.smsmessenger.helpers.*
import com.goodwy.smsmessenger.models.*
import com.mikhaellopez.rxanimation.RxAnimation
import com.mikhaellopez.rxanimation.shake
import kotlin.math.abs
import kotlin.system.exitProcess
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import ru.rustore.sdk.core.feature.model.FeatureAvailabilityResult
import java.util.Locale

class SettingsActivity : SimpleActivity() {
    private var blockedNumbersAtPause = -1
    private var recycleBinMessages = 0
    private val messagesFileType = "application/json"
    private val messageImportFileTypes = listOf("application/json", "application/xml", "text/xml", "application/octet-stream")
    private var wasProtectionHandled = false

    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    private val purchaseHelper = PurchaseHelper(this)
    private val ruStoreHelper = RuStoreHelper(this)
    private val productIdX1 = BuildConfig.PRODUCT_ID_X1
    private val productIdX2 = BuildConfig.PRODUCT_ID_X2
    private val productIdX3 = BuildConfig.PRODUCT_ID_X3
    private val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    private val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    private val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    private val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    private val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    private val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3
    private var ruStoreIsConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.settingsCoordinator,
            nestedView = binding.settingsHolder,
            useTransparentNavigation = false,
            useTopSearchMenu = false
        )
        setupMaterialScrollListener(scrollingView = binding.settingsNestedScrollview, toolbar = binding.settingsToolbar)
        // TODO TRANSPARENT Navigation Bar
        if (config.transparentNavigationBar) {
            setWindowTransparency(true) { _, _, leftNavigationBarSize, rightNavigationBarSize ->
                binding.settingsCoordinator.setPadding(leftNavigationBarSize, 0, rightNavigationBarSize, 0)
                updateNavigationBarColor(getProperBackgroundColor())
            }
        }

        if (isPlayStoreInstalled()) {
            //PlayStore
            purchaseHelper.initBillingClient()
            val iapList: ArrayList<String> = arrayListOf(productIdX1, productIdX2, productIdX3)
            val subList: ArrayList<String> = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3, subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3)
            purchaseHelper.retrieveDonation(iapList, subList)

            purchaseHelper.isIapPurchased.observe(this) {
                when (it) {
                    is Tipping.Succeeded -> {
                        config.isPro = true
                        updatePro()
                    }
                    is Tipping.NoTips -> {
                        config.isPro = false
                        updatePro()
                    }
                    is Tipping.FailedToLoad -> {
                    }
                }
            }

            purchaseHelper.isSupPurchased.observe(this) {
                when (it) {
                    is Tipping.Succeeded -> {
                        config.isProSubs = true
                        updatePro()
                    }
                    is Tipping.NoTips -> {
                        config.isProSubs = false
                        updatePro()
                    }
                    is Tipping.FailedToLoad -> {
                    }
                }
            }
        }
        if (isRuStoreInstalled()) {
            //RuStore
            ruStoreHelper.checkPurchasesAvailability()

            lifecycleScope.launch {
                ruStoreHelper.eventStart
                    .flowWithLifecycle(lifecycle)
                    .collect { event ->
                        handleEventStart(event)
                    }
            }

            lifecycleScope.launch {
                ruStoreHelper.statePurchased
                    .flowWithLifecycle(lifecycle)
                    .collect { state ->
                        //update of purchased
                        if (!state.isLoading && ruStoreIsConnected) {
                            baseConfig.isProRuStore = state.purchases.firstOrNull() != null
                            updatePro()
                        }
                    }
            }
        }

        wasProtectionHandled = intent.getBooleanExtra(WAS_PROTECTION_HANDLED, false)
        if (savedInstanceState == null) {
            if (!wasProtectionHandled) {
                handleAppPasswordProtection {
                    wasProtectionHandled = it
                    if (!it) finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.settingsToolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()

        setupCustomizeColors()
        setupMaterialDesign3()
        setupOverflowIcon()
        setupUseColoredContacts()
        setupContactsColorList()
        setupColorSimIcons()
        setupSimCardColorList()

        setupManageBlockedNumbers()
        setupManageBlockedKeywords()
        setupFontSize()
        setupChangeDateTimeFormat()
        setupUseEnglish()
        setupLanguage()

        setupUseSwipeToAction()
        setupSwipeVibration()
        setupSwipeRightAction()
        setupSwipeLeftAction()
        setupArchiveConfirmation()
        setupDeleteConfirmation()

        setupCustomizeNotifications()
        setupLockScreenVisibility()
        setupCopyNumberAndDelete()

        setupThreadTopStyle()
        setupMessageBubble()
        setupFontSizeMessage()
        setupActionOnMessageClick()

        setupSendOnEnter()
        setupShowSimSelectionDialog()
        setupEnableDeliveryReports()
        setupShowCharacterCounter()
        setupUseSimpleCharacters()
        setupSendLongMessageAsMMS()
        setupGroupMessageAsMMS()
        setupMMSFileSizeLimit()

        setupShowDividers()
        setupShowContactThumbnails()
        setupContactThumbnailsSize()
        setupUseRelativeDate()
        setupUnreadAtTop()
        setupLinesCount()
        setupUnreadIndicatorPosition()
        setupHideTopBarWhenScroll()

        setupUseRecycleBin()
        setupEmptyRecycleBin()

        setupAppPasswordProtection()

        setupMessagesExport()
        setupMessagesImport()

        setupTipJar()
        setupAbout()
        updateTextColors(binding.settingsNestedScrollview)

        if (blockedNumbersAtPause != -1 && blockedNumbersAtPause != getBlockedNumbers().hashCode()) {
            refreshMessages()
        }

        binding.apply {
            arrayOf(
                settingsAppearanceLabel,
                settingsGeneralLabel,
                settingsNotificationsLabel,
                settingsMessagesLabel,
                settingsOutgoingMessagesLabel,
                settingsListViewLabel,
                settingsSwipeGesturesLabel,
                settingsRecycleBinLabel,
                settingsSecurityLabel,
                settingsBackupsLabel,
                settingsOtherLabel
            ).forEach {
                it.setTextColor(getProperPrimaryColor())
            }

            arrayOf(
                settingsColorCustomizationHolder,
                settingsGeneralHolder,
                settingsNotificationsHolder,
                settingsMessagesHolder,
                settingsOutgoingMessagesHolder,
                settingsListViewHolder,
                settingsSwipeGesturesHolder,
                settingsRecycleBinHolder,
                settingsSecurityHolder,
                settingsBackupsHolder,
                settingsOtherHolder
            ).forEach {
                it.setCardBackgroundColor(getBottomNavigationBackgroundColor())
            }

            arrayOf(
                settingsCustomizeColorsChevron,
                settingsManageBlockedNumbersChevron,
                settingsManageBlockedKeywordsChevron,
                settingsChangeDateTimeFormatChevron,
                settingsCustomizeNotificationsChevron,
                settingsImportMessagesChevron,
                settingsExportMessagesChevron,
                settingsTipJarChevron,
                settingsAboutChevron
            ).forEach {
                it.applyColorFilter(getProperTextColor())
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(WAS_PROTECTION_HANDLED, wasProtectionHandled)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        wasProtectionHandled = savedInstanceState.getBoolean(WAS_PROTECTION_HANDLED, false)

        if (!wasProtectionHandled) {
            handleAppPasswordProtection {
                wasProtectionHandled = it
                if (!it) finish()
            }
        }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            MessagesImporter(this).importMessages(uri)
        }
    }

    private val saveDocument = registerForActivityResult(ActivityResultContracts.CreateDocument(messagesFileType)) { uri ->
        if (uri != null) {
            toast(com.goodwy.commons.R.string.exporting)
            exportMessages(uri)
        }
    }

    private fun setupMessagesExport() {
        binding.settingsExportMessagesHolder.setOnClickListener {
            ExportMessagesDialog(this) { fileName ->
                saveDocument.launch("$fileName.json")
            }
        }
    }

    private fun setupMessagesImport() {
        binding.settingsImportMessagesHolder.setOnClickListener {
            getContent.launch(messageImportFileTypes.toTypedArray())
        }
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun exportMessages(uri: Uri) {
        ensureBackgroundThread {
            var success = false
            try {
                MessagesReader(this).getMessagesToExport(config.exportSms, config.exportMms) { messagesToExport ->
                    if (messagesToExport.isEmpty()) {
                        toast(com.goodwy.commons.R.string.no_entries_for_exporting)
                        return@getMessagesToExport
                    }
                    val json = Json { encodeDefaults = true }
                    contentResolver.openOutputStream(uri)!!.buffered().use { outputStream ->
                        json.encodeToStream(messagesToExport, outputStream)
                    }
                    success = true
                    toast(com.goodwy.commons.R.string.exporting_successful)
                }
            } catch (e: Throwable) { // also catch OutOfMemoryError etc.
                showErrorToast(e.toString())
            } finally {
                if (!success) {
                    // delete the file to avoid leaving behind an empty/corrupt file
                    try {
                        DocumentsContract.deleteDocument(contentResolver, uri)
                    } catch (ignored: Exception) {
                        // ignored because we don't want to overwhelm the user with two error messages
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        blockedNumbersAtPause = getBlockedNumbers().hashCode()
    }

    private fun setupPurchaseThankYou() = binding.apply {
        settingsPurchaseThankYouHolder.beGoneIf(isPro())
        settingsPurchaseThankYouHolder.setOnClickListener {
            launchPurchase()
        }
        moreButton.setOnClickListener {
            launchPurchase()
        }
        val appDrawable = resources.getColoredDrawableWithColor(this@SettingsActivity, com.goodwy.commons.R.drawable.ic_plus_support, getProperPrimaryColor())
        purchaseLogo.setImageDrawable(appDrawable)
        val drawable = resources.getColoredDrawableWithColor(this@SettingsActivity, com.goodwy.commons.R.drawable.button_gray_bg, getProperPrimaryColor())
        moreButton.background = drawable
        moreButton.setTextColor(getProperBackgroundColor())
        moreButton.setPadding(2, 2, 2, 2)
    }

    private fun setupCustomizeColors() = binding.apply {
//        settingsCustomizeColorsLabel.text = if (isPro()) {
//            getString(com.goodwy.commons.R.string.customize_colors)
//        } else {
//            getString(com.goodwy.commons.R.string.customize_colors_locked)
//        }
        settingsCustomizeColorsHolder.setOnClickListener {
            startCustomizationActivity(
                showAccentColor = false,
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
    }

    @SuppressLint("NewApi")
    private fun setupCustomizeNotifications() = binding.apply {
        settingsCustomizeNotificationsHolder.beVisibleIf(isOreoPlus())

        if (settingsCustomizeNotificationsHolder.isGone()) {
            settingsLockScreenVisibilityHolder.background = resources.getDrawable(R.drawable.ripple_all_corners, theme)
        }

        settingsCustomizeNotificationsHolder.setOnClickListener {
            launchCustomizeNotificationsIntent()
        }
    }

    private fun setupMaterialDesign3() = binding.apply {
        settingsMaterialDesign3.isChecked = config.materialDesign3
        settingsMaterialDesign3Holder.setOnClickListener {
            settingsMaterialDesign3.toggle()
            config.materialDesign3 = settingsMaterialDesign3.isChecked
            config.tabsChanged = true
        }
    }

    private fun setupOverflowIcon() = binding.apply {
        settingsOverflowIcon.applyColorFilter(getProperTextColor())
        settingsOverflowIcon.setImageResource(getOverflowIcon(baseConfig.overflowIcon))
        settingsOverflowIconHolder.setOnClickListener {
            OverflowIconDialog(this@SettingsActivity) {
                settingsOverflowIcon.setImageResource(getOverflowIcon(baseConfig.overflowIcon))
            }
        }
    }

    private fun setupThreadTopStyle() = binding.apply {
        settingsThreadTopStyle.text = getThreadTopStyleText()
        settingsThreadTopStyleHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(THREAD_TOP_COMPACT, getString(com.goodwy.commons.R.string.small)),
                RadioItem(THREAD_TOP_LARGE, getString(com.goodwy.commons.R.string.large))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.threadTopStyle, R.string.chat_title_style_g) {
                config.threadTopStyle = it as Int
                settingsThreadTopStyle.text = getThreadTopStyleText()
            }
        }
    }

    private fun getThreadTopStyleText() = getString(
        when (config.threadTopStyle) {
            THREAD_TOP_COMPACT -> com.goodwy.commons.R.string.small
            THREAD_TOP_LARGE -> com.goodwy.commons.R.string.large
            else -> com.goodwy.commons.R.string.large
        }
    )

    private fun setupMessageBubble() = binding.apply {
        settingsMessageBubbleIcon.background = resources.getColoredDrawableWithColor(getMessageBubbleResource(config.bubbleStyle), getProperPrimaryColor())
        settingsMessageBubbleIcon.setPaddingBubble(this@SettingsActivity, config.bubbleStyle)
        settingsMessageBubbleHolder.setOnClickListener {
            MessageBubbleSettingDialog(this@SettingsActivity, isPro()) {
                settingsMessageBubbleIcon.background = resources.getColoredDrawableWithColor(getMessageBubbleResource(it), getProperPrimaryColor())
                settingsMessageBubbleIcon.setPaddingBubble(this@SettingsActivity, it)
            }
        }
    }

    private fun getMessageBubbleResource(bubbleStyle: Int): Int {
        return when (bubbleStyle) {
            BUBBLE_STYLE_ROUNDED -> R.drawable.item_received_rounded_background
            BUBBLE_STYLE_IOS_NEW -> R.drawable.item_received_ios_new_background
            BUBBLE_STYLE_IOS -> R.drawable.item_received_ios_background
            else -> R.drawable.item_received_background
        }
    }

    private fun setupFontSizeMessage() = binding.apply {
        settingsFontSizeMessage.text = getFontSizeMessageText()
        settingsFontSizeMessageHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(com.goodwy.commons.R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(com.goodwy.commons.R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(com.goodwy.commons.R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(com.goodwy.commons.R.string.extra_large))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.fontSizeMessage, com.goodwy.commons.R.string.font_size) {
                config.fontSizeMessage = it as Int
                settingsFontSizeMessage.text = getFontSizeMessageText()
            }
        }
    }

    private fun getFontSizeMessageText() = getString(
        when (config.fontSizeMessage) {
            FONT_SIZE_SMALL -> com.goodwy.commons.R.string.small
            FONT_SIZE_MEDIUM -> com.goodwy.commons.R.string.medium
            FONT_SIZE_LARGE -> com.goodwy.commons.R.string.large
            else -> com.goodwy.commons.R.string.extra_large
        }
    )

    private fun setupUseEnglish() = binding.apply {
        settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settingsUseEnglish.isChecked = config.useEnglish
        settingsUseEnglishHolder.setOnClickListener {
            settingsUseEnglish.toggle()
            config.useEnglish = settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    @SuppressLint("NewApi")
    private fun setupLanguage() = binding.apply {
        settingsLanguage.text = Locale.getDefault().displayLanguage
        settingsLanguageHolder.beVisibleIf(isTiramisuPlus())
        settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    // support for device-wise blocking came on Android 7, rely only on that
    @TargetApi(Build.VERSION_CODES.N)
    private fun setupManageBlockedNumbers() = binding.apply {
        settingsManageBlockedNumbersHolder.beVisibleIf(isNougatPlus())
        settingsManageBlockedNumbersCount.text = getBlockedNumbers().size.toString()

        val getProperTextColor = getProperTextColor()
        val red = resources.getColor(com.goodwy.commons.R.color.red_missed)
        val colorUnknown = if (baseConfig.blockUnknownNumbers) red else getProperTextColor
        val alphaUnknown = if (baseConfig.blockUnknownNumbers) 1f else 0.6f
        settingsManageBlockedNumbersIconUnknown.apply {
            applyColorFilter(colorUnknown)
            alpha = alphaUnknown
        }

        val colorHidden = if (baseConfig.blockHiddenNumbers) red else getProperTextColor
        val alphaHidden = if (baseConfig.blockHiddenNumbers) 1f else 0.6f
        settingsManageBlockedNumbersIconHidden.apply {
            applyColorFilter(colorHidden)
            alpha = alphaHidden
        }

        settingsManageBlockedNumbersHolder.setOnClickListener {
            Intent(this@SettingsActivity, ManageBlockedNumbersActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun setupManageBlockedKeywords() = binding.apply {
        settingsManageBlockedKeywordsCount.text = config.blockedKeywords.size.toString()
        settingsManageBlockedKeywordsHolder.setOnClickListener {
            Intent(this@SettingsActivity, ManageBlockedKeywordsActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun setupChangeDateTimeFormat() = binding.apply {
        settingsChangeDateTimeFormatHolder.setOnClickListener {
            ChangeDateTimeFormatDialog(this@SettingsActivity) {
                refreshMessages()
            }
        }
    }

    private fun setupFontSize() = binding.apply {
        settingsFontSize.text = getFontSizeText()
        settingsFontSizeHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(com.goodwy.commons.R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(com.goodwy.commons.R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(com.goodwy.commons.R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(com.goodwy.commons.R.string.extra_large))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.fontSize, com.goodwy.commons.R.string.font_size) {
                config.fontSize = it as Int
                settingsFontSize.text = getFontSizeText()
            }
        }
    }

    private fun setupShowCharacterCounter() = binding.apply {
        settingsShowCharacterCounter.isChecked = config.showCharacterCounter
        settingsShowCharacterCounterHolder.setOnClickListener {
            settingsShowCharacterCounter.toggle()
            config.showCharacterCounter = settingsShowCharacterCounter.isChecked
        }
    }

    @SuppressLint("PrivateResource")
    private fun setupActionOnMessageClick() = binding.apply {
        settingsActionOnMessageClick.text = getActionOnMessageClickText()
        settingsActionOnMessageClickHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(ACTION_COPY_CODE, getString(com.goodwy.strings.R.string.copy_code), icon = com.goodwy.commons.R.drawable.ic_copy_vector),
                RadioItem(ACTION_COPY_MESSAGE, getString(com.goodwy.commons.R.string.copy_to_clipboard), icon = com.goodwy.commons.R.drawable.ic_copy_vector),
                RadioItem(ACTION_SELECT_TEXT, getString(com.goodwy.commons.R.string.select_text), icon = R.drawable.ic_text_select),
                RadioItem(ACTION_NOTHING, getString(com.google.android.material.R.string.exposed_dropdown_menu_content_description), icon = R.drawable.ic_menu_open),
            )

            RadioGroupIconDialog(this@SettingsActivity, items, config.actionOnMessageClickSetting, com.goodwy.strings.R.string.action_on_message_click) {
                config.actionOnMessageClickSetting = it as Int
                settingsActionOnMessageClick.text = getActionOnMessageClickText()
            }
        }
    }

    @SuppressLint("PrivateResource")
    private fun getActionOnMessageClickText() = getString(
        when (config.actionOnMessageClickSetting) {
            ACTION_COPY_CODE -> com.goodwy.strings.R.string.copy_code
            ACTION_COPY_MESSAGE -> com.goodwy.commons.R.string.copy_to_clipboard
            ACTION_SELECT_TEXT -> com.goodwy.commons.R.string.select_text
            else -> com.google.android.material.R.string.exposed_dropdown_menu_content_description
        }
    )

    private fun setupUseSimpleCharacters() = binding.apply {
        settingsUseSimpleCharacters.isChecked = config.useSimpleCharacters
        settingsUseSimpleCharactersHolder.setOnClickListener {
            settingsUseSimpleCharacters.toggle()
            config.useSimpleCharacters = settingsUseSimpleCharacters.isChecked
        }
    }

    private fun setupSendOnEnter() = binding.apply {
        settingsSendOnEnter.isChecked = config.sendOnEnter
        settingsSendOnEnterHolder.setOnClickListener {
            settingsSendOnEnter.toggle()
            config.sendOnEnter = settingsSendOnEnter.isChecked
        }
    }

    private fun setupShowSimSelectionDialog() = binding.apply {
        settingsShowSimSelectionDialogHolder.beVisibleIf(areMultipleSIMsAvailable())
        settingsShowSimSelectionDialog.isChecked = config.showSimSelectionDialog
        settingsShowSimSelectionDialogHolder.setOnClickListener {
            settingsShowSimSelectionDialog.toggle()
            config.showSimSelectionDialog = settingsShowSimSelectionDialog.isChecked
        }
    }

    private fun setupEnableDeliveryReports() = binding.apply {
        settingsEnableDeliveryReports.isChecked = config.enableDeliveryReports
        settingsEnableDeliveryReportsHolder.setOnClickListener {
            settingsEnableDeliveryReports.toggle()
            config.enableDeliveryReports = settingsEnableDeliveryReports.isChecked
        }
    }

    private fun setupSendLongMessageAsMMS() = binding.apply {
        settingsSendLongMessageMms.isChecked = config.sendLongMessageMMS
        settingsSendLongMessageMmsHolder.setOnClickListener {
            settingsSendLongMessageMms.toggle()
            config.sendLongMessageMMS = settingsSendLongMessageMms.isChecked
        }
    }

    private fun setupGroupMessageAsMMS() = binding.apply {
        settingsSendGroupMessageMms.isChecked = config.sendGroupMessageMMS
        settingsSendGroupMessageMmsHolder.setOnClickListener {
            settingsSendGroupMessageMms.toggle()
            config.sendGroupMessageMMS = settingsSendGroupMessageMms.isChecked
        }
    }

    private fun setupLockScreenVisibility() = binding.apply {
        settingsLockScreenVisibility.text = getLockScreenVisibilityText()
        settingsLockScreenVisibilityHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(LOCK_SCREEN_SENDER_MESSAGE, getString(R.string.sender_and_message)),
                RadioItem(LOCK_SCREEN_SENDER, getString(R.string.sender_only)),
                RadioItem(LOCK_SCREEN_NOTHING, getString(com.goodwy.commons.R.string.nothing)),
            )

            RadioGroupDialog(this@SettingsActivity, items, config.lockScreenVisibilitySetting, R.string.lock_screen_visibility) {
                config.lockScreenVisibilitySetting = it as Int
                settingsLockScreenVisibility.text = getLockScreenVisibilityText()
            }
        }
    }

    private fun setupCopyNumberAndDelete() = binding.apply {
        settingsCopyNumberAndDelete.isChecked = config.copyNumberAndDelete
        settingsCopyNumberAndDeleteHolder.setOnClickListener {
            settingsCopyNumberAndDelete.toggle()
            config.copyNumberAndDelete = settingsCopyNumberAndDelete.isChecked
        }
    }

    private fun getLockScreenVisibilityText() = getString(
        when (config.lockScreenVisibilitySetting) {
            LOCK_SCREEN_SENDER_MESSAGE -> R.string.sender_and_message
            LOCK_SCREEN_SENDER -> R.string.sender_only
            else -> com.goodwy.commons.R.string.nothing
        }
    )

    private fun setupMMSFileSizeLimit() = binding.apply {
        settingsMmsFileSizeLimit.text = getMMSFileLimitText()
        settingsMmsFileSizeLimitHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(7, getString(R.string.mms_file_size_limit_none), FILE_SIZE_NONE),
                RadioItem(6, getString(R.string.mms_file_size_limit_2mb), FILE_SIZE_2_MB),
                RadioItem(5, getString(R.string.mms_file_size_limit_1mb), FILE_SIZE_1_MB),
                RadioItem(4, getString(R.string.mms_file_size_limit_600kb), FILE_SIZE_600_KB),
                RadioItem(3, getString(R.string.mms_file_size_limit_300kb), FILE_SIZE_300_KB),
                RadioItem(2, getString(R.string.mms_file_size_limit_200kb), FILE_SIZE_200_KB),
                RadioItem(1, getString(R.string.mms_file_size_limit_100kb), FILE_SIZE_100_KB),
            )

            val checkedItemId = items.find { it.value == config.mmsFileSizeLimit }?.id ?: 7
            RadioGroupDialog(this@SettingsActivity, items, checkedItemId, R.string.mms_file_size_limit) {
                config.mmsFileSizeLimit = it as Long
                settingsMmsFileSizeLimit.text = getMMSFileLimitText()
            }
        }
    }

    private fun setupUseSwipeToAction() {
        updateSwipeToActionVisible()
        binding.apply {
            settingsUseSwipeToAction.isChecked = config.useSwipeToAction
            settingsUseSwipeToActionHolder.setOnClickListener {
                settingsUseSwipeToAction.toggle()
                config.useSwipeToAction = settingsUseSwipeToAction.isChecked
                //config.tabsChanged = true
                updateSwipeToActionVisible()
            }
        }
    }

    private fun updateSwipeToActionVisible() {
        binding.apply {
            settingsSwipeVibrationHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeRightActionHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeLeftActionHolder.beVisibleIf(config.useSwipeToAction)
            settingsSkipArchiveConfirmationHolder.beVisibleIf(config.useSwipeToAction && (config.swipeLeftAction == SWIPE_ACTION_ARCHIVE || config.swipeRightAction == SWIPE_ACTION_ARCHIVE))
            settingsSkipDeleteConfirmationHolder.beVisibleIf(config.useSwipeToAction &&(config.swipeLeftAction == SWIPE_ACTION_DELETE || config.swipeRightAction == SWIPE_ACTION_DELETE))
        }
    }

    private fun setupSwipeVibration() {
        binding.apply {
            settingsSwipeVibration.isChecked = config.swipeVibration
            settingsSwipeVibrationHolder.setOnClickListener {
                settingsSwipeVibration.toggle()
                config.swipeVibration = settingsSwipeVibration.isChecked
                config.tabsChanged = true
            }
        }
    }

    private fun setupSwipeRightAction() = binding.apply {
        if (isRTLLayout) settingsSwipeRightActionLabel.text = getString(com.goodwy.strings.R.string.swipe_left_action)
        settingsSwipeRightAction.text = getSwipeActionText(false)
        settingsSwipeRightActionHolder.setOnClickListener {
            val items = if (isNougatPlus()) arrayListOf(
                RadioItem(SWIPE_ACTION_MARK_READ, getString(R.string.mark_as_read), icon = R.drawable.ic_mark_read),
                RadioItem(SWIPE_ACTION_DELETE, getString(com.goodwy.commons.R.string.delete), icon = com.goodwy.commons.R.drawable.ic_delete_outline),
                RadioItem(SWIPE_ACTION_ARCHIVE, getString(R.string.archive), icon = R.drawable.ic_archive_vector),
                RadioItem(SWIPE_ACTION_BLOCK, getString(com.goodwy.commons.R.string.block_number), icon = com.goodwy.commons.R.drawable.ic_block_vector),
                RadioItem(SWIPE_ACTION_CALL, getString(com.goodwy.commons.R.string.call), icon = com.goodwy.commons.R.drawable.ic_phone_vector),
                RadioItem(SWIPE_ACTION_MESSAGE, getString(com.goodwy.commons.R.string.send_sms), icon = com.goodwy.commons.R.drawable.ic_messages),
            ) else arrayListOf(
                RadioItem(SWIPE_ACTION_MARK_READ, getString(R.string.mark_as_read), icon = R.drawable.ic_mark_read),
                RadioItem(SWIPE_ACTION_DELETE, getString(com.goodwy.commons.R.string.delete), icon = com.goodwy.commons.R.drawable.ic_delete_outline),
                RadioItem(SWIPE_ACTION_ARCHIVE, getString(R.string.archive), icon = R.drawable.ic_archive_vector),
                RadioItem(SWIPE_ACTION_CALL, getString(com.goodwy.commons.R.string.call), icon = com.goodwy.commons.R.drawable.ic_phone_vector),
                RadioItem(SWIPE_ACTION_MESSAGE, getString(com.goodwy.commons.R.string.send_sms), icon = com.goodwy.commons.R.drawable.ic_messages),
            )

            val title =
                if (isRTLLayout) com.goodwy.strings.R.string.swipe_left_action else com.goodwy.strings.R.string.swipe_right_action
            RadioGroupIconDialog(this@SettingsActivity, items, config.swipeRightAction, title) {
                config.swipeRightAction = it as Int
                config.tabsChanged = true
                settingsSwipeRightAction.text = getSwipeActionText(false)
                settingsSkipArchiveConfirmationHolder.beVisibleIf(config.swipeLeftAction == SWIPE_ACTION_ARCHIVE || config.swipeRightAction == SWIPE_ACTION_ARCHIVE)
                settingsSkipDeleteConfirmationHolder.beVisibleIf(config.swipeLeftAction == SWIPE_ACTION_DELETE || config.swipeRightAction == SWIPE_ACTION_DELETE)
            }
        }
    }

    private fun setupSwipeLeftAction() = binding.apply {
        val stringId = if (isRTLLayout) com.goodwy.strings.R.string.swipe_right_action else com.goodwy.strings.R.string.swipe_left_action
        settingsSwipeLeftActionLabel.text = addLockedLabelIfNeeded(stringId, isPro())
        settingsSwipeLeftAction.text = getSwipeActionText(true)
        settingsSwipeLeftActionHolder.setOnClickListener {
            if (isPro()) {
                val items = if (isNougatPlus()) arrayListOf(
                    RadioItem(SWIPE_ACTION_MARK_READ, getString(R.string.mark_as_read), icon = R.drawable.ic_mark_read),
                    RadioItem(SWIPE_ACTION_DELETE, getString(com.goodwy.commons.R.string.delete), icon = com.goodwy.commons.R.drawable.ic_delete_outline),
                    RadioItem(SWIPE_ACTION_ARCHIVE, getString(R.string.archive), icon = R.drawable.ic_archive_vector),
                    RadioItem(SWIPE_ACTION_BLOCK, getString(com.goodwy.commons.R.string.block_number), icon = com.goodwy.commons.R.drawable.ic_block_vector),
                    RadioItem(SWIPE_ACTION_CALL, getString(com.goodwy.commons.R.string.call), icon = com.goodwy.commons.R.drawable.ic_phone_vector),
                    RadioItem(SWIPE_ACTION_MESSAGE, getString(com.goodwy.commons.R.string.send_sms), icon = com.goodwy.commons.R.drawable.ic_messages),
                ) else arrayListOf(
                    RadioItem(SWIPE_ACTION_MARK_READ, getString(R.string.mark_as_read), icon = R.drawable.ic_mark_read),
                    RadioItem(SWIPE_ACTION_DELETE, getString(com.goodwy.commons.R.string.delete), icon = com.goodwy.commons.R.drawable.ic_delete_outline),
                    RadioItem(SWIPE_ACTION_ARCHIVE, getString(R.string.archive), icon = R.drawable.ic_archive_vector),
                    RadioItem(SWIPE_ACTION_CALL, getString(com.goodwy.commons.R.string.call), icon = com.goodwy.commons.R.drawable.ic_phone_vector),
                    RadioItem(SWIPE_ACTION_MESSAGE, getString(com.goodwy.commons.R.string.send_sms), icon = com.goodwy.commons.R.drawable.ic_messages),
                )

                val title =
                    if (isRTLLayout) com.goodwy.strings.R.string.swipe_right_action else com.goodwy.strings.R.string.swipe_left_action
                RadioGroupIconDialog(this@SettingsActivity, items, config.swipeLeftAction, title) {
                    config.swipeLeftAction = it as Int
                    config.tabsChanged = true
                    settingsSwipeLeftAction.text = getSwipeActionText(true)
                    settingsSkipArchiveConfirmationHolder.beVisibleIf(config.swipeLeftAction == SWIPE_ACTION_ARCHIVE || config.swipeRightAction == SWIPE_ACTION_ARCHIVE)
                    settingsSkipDeleteConfirmationHolder.beVisibleIf(config.swipeLeftAction == SWIPE_ACTION_DELETE || config.swipeRightAction == SWIPE_ACTION_DELETE)
                }
            } else {
                launchPurchase()
            }
        }
    }

    private fun getSwipeActionText(left: Boolean) = getString(
        when (if (left) config.swipeLeftAction else config.swipeRightAction) {
            SWIPE_ACTION_DELETE -> com.goodwy.commons.R.string.delete
            SWIPE_ACTION_ARCHIVE -> R.string.archive
            SWIPE_ACTION_BLOCK -> com.goodwy.commons.R.string.block_number
            SWIPE_ACTION_CALL -> com.goodwy.commons.R.string.call
            SWIPE_ACTION_MESSAGE -> com.goodwy.commons.R.string.send_sms
            else -> R.string.mark_as_read
        }
    )

    private fun setupArchiveConfirmation() {
        binding.apply {
            //settingsSkipArchiveConfirmationHolder.beVisibleIf(config.swipeLeftAction == SWIPE_ACTION_ARCHIVE || config.swipeRightAction == SWIPE_ACTION_ARCHIVE)
            settingsSkipArchiveConfirmation.isChecked = config.skipArchiveConfirmation
            settingsSkipArchiveConfirmationHolder.setOnClickListener {
                settingsSkipArchiveConfirmation.toggle()
                config.skipArchiveConfirmation = settingsSkipArchiveConfirmation.isChecked
            }
        }
    }

    private fun setupDeleteConfirmation() {
        binding.apply {
            //settingsSkipDeleteConfirmationHolder.beVisibleIf(config.swipeLeftAction == SWIPE_ACTION_DELETE || config.swipeRightAction == SWIPE_ACTION_DELETE)
            settingsSkipDeleteConfirmation.isChecked = config.skipDeleteConfirmation
            settingsSkipDeleteConfirmationHolder.setOnClickListener {
                settingsSkipDeleteConfirmation.toggle()
                config.skipDeleteConfirmation = settingsSkipDeleteConfirmation.isChecked
            }
        }
    }

    private fun setupUseRecycleBin() = binding.apply {
        updateRecycleBinButtons()
        settingsUseRecycleBin.isChecked = config.useRecycleBin
        settingsUseRecycleBinHolder.setOnClickListener {
            settingsUseRecycleBin.toggle()
            config.useRecycleBin = settingsUseRecycleBin.isChecked
            updateRecycleBinButtons()
        }
    }

    private fun updateRecycleBinButtons() = binding.apply {
        settingsEmptyRecycleBinHolder.beVisibleIf(config.useRecycleBin)
    }

    private fun setupEmptyRecycleBin() = binding.apply {
        ensureBackgroundThread {
            recycleBinMessages = messagesDB.getArchivedCount()
            runOnUiThread {
                settingsEmptyRecycleBinSize.text =
                    resources.getQuantityString(R.plurals.delete_messages, recycleBinMessages, recycleBinMessages)
            }
        }

        settingsEmptyRecycleBinHolder.setOnClickListener {
            if (recycleBinMessages == 0) {
                toast(com.goodwy.commons.R.string.recycle_bin_empty)
            } else {
                ConfirmationDialog(
                    activity = this@SettingsActivity,
                    message = "",
                    messageId = R.string.empty_recycle_bin_messages_confirmation,
                    positive = com.goodwy.commons.R.string.yes,
                    negative = com.goodwy.commons.R.string.no
                ) {
                    ensureBackgroundThread {
                        emptyMessagesRecycleBin()
                    }
                    recycleBinMessages = 0
                    settingsEmptyRecycleBinSize.text =
                        resources.getQuantityString(R.plurals.delete_messages, recycleBinMessages, recycleBinMessages)
                }
            }
        }
    }

    private fun setupAppPasswordProtection() = binding.apply {
        settingsAppPasswordProtection.isChecked = config.isAppPasswordProtectionOn
        settingsAppPasswordProtectionHolder.setOnClickListener {
            val tabToShow = if (config.isAppPasswordProtectionOn) config.appProtectionType else SHOW_ALL_TABS
            SecurityDialog(this@SettingsActivity, config.appPasswordHash, tabToShow) { hash, type, success ->
                if (success) {
                    val hasPasswordProtection = config.isAppPasswordProtectionOn
                    settingsAppPasswordProtection.isChecked = !hasPasswordProtection
                    config.isAppPasswordProtectionOn = !hasPasswordProtection
                    config.appPasswordHash = if (hasPasswordProtection) "" else hash
                    config.appProtectionType = type

                    if (config.isAppPasswordProtectionOn) {
                        val confirmationTextId = if (config.appProtectionType == PROTECTION_FINGERPRINT) {
                            com.goodwy.commons.R.string.fingerprint_setup_successfully
                        } else {
                            com.goodwy.commons.R.string.protection_setup_successfully
                        }

                        ConfirmationDialog(this@SettingsActivity, "", confirmationTextId, com.goodwy.commons.R.string.ok, 0) { }
                    }
                }
            }
        }
    }

    private fun getMMSFileLimitText() = getString(
        when (config.mmsFileSizeLimit) {
            FILE_SIZE_100_KB -> R.string.mms_file_size_limit_100kb
            FILE_SIZE_200_KB -> R.string.mms_file_size_limit_200kb
            FILE_SIZE_300_KB -> R.string.mms_file_size_limit_300kb
            FILE_SIZE_600_KB -> R.string.mms_file_size_limit_600kb
            FILE_SIZE_1_MB -> R.string.mms_file_size_limit_1mb
            FILE_SIZE_2_MB -> R.string.mms_file_size_limit_2mb
            else -> R.string.mms_file_size_limit_none
        }
    )

    private fun setupShowDividers() = binding.apply {
        settingsShowDividers.isChecked = config.useDividers
        settingsShowDividersHolder.setOnClickListener {
            settingsShowDividers.toggle()
            config.useDividers = settingsShowDividers.isChecked
            config.tabsChanged = true
        }
    }

    private fun setupShowContactThumbnails() = binding.apply {
        settingsShowContactThumbnails.isChecked = config.showContactThumbnails
        settingsShowContactThumbnailsHolder.setOnClickListener {
            settingsShowContactThumbnails.toggle()
            config.showContactThumbnails = settingsShowContactThumbnails.isChecked
            settingsContactThumbnailsSizeHolder.beVisibleIf(config.showContactThumbnails)
            config.tabsChanged = true
        }
    }

    private fun setupContactThumbnailsSize() = binding.apply {
        val pro = isPro()
        settingsContactThumbnailsSizeHolder.beVisibleIf(config.showContactThumbnails)
        settingsContactThumbnailsSizeHolder.alpha = if (pro) 1f else 0.4f
        settingsContactThumbnailsSizeLabel.text = addLockedLabelIfNeeded(com.goodwy.strings.R.string.contact_thumbnails_size, pro)
        settingsContactThumbnailsSize.text = getContactThumbnailsSizeText()
        settingsContactThumbnailsSizeHolder.setOnClickListener {
            if (pro) {
                val items = arrayListOf(
                    RadioItem(FONT_SIZE_SMALL, getString(com.goodwy.commons.R.string.small), CONTACT_THUMBNAILS_SIZE_SMALL),
                    RadioItem(FONT_SIZE_MEDIUM, getString(com.goodwy.commons.R.string.medium), CONTACT_THUMBNAILS_SIZE_MEDIUM),
                    RadioItem(FONT_SIZE_LARGE, getString(com.goodwy.commons.R.string.large), CONTACT_THUMBNAILS_SIZE_LARGE),
                    RadioItem(FONT_SIZE_EXTRA_LARGE, getString(com.goodwy.commons.R.string.extra_large), CONTACT_THUMBNAILS_SIZE_EXTRA_LARGE)
                )

                RadioGroupDialog(this@SettingsActivity, items, config.contactThumbnailsSize, com.goodwy.strings.R.string.contact_thumbnails_size) {
                    config.contactThumbnailsSize = it as Int
                    settingsContactThumbnailsSize.text = getContactThumbnailsSizeText()
                    config.tabsChanged = true
                }
            } else {
                RxAnimation.from(settingsContactThumbnailsSizeHolder)
                    .shake(shakeTranslation = 2f)
                    .subscribe()

                showSnackbar(binding.root)
            }
        }
    }

    private fun getContactThumbnailsSizeText() = getString(
        when (baseConfig.contactThumbnailsSize) {
            CONTACT_THUMBNAILS_SIZE_SMALL -> com.goodwy.commons.R.string.small
            CONTACT_THUMBNAILS_SIZE_MEDIUM -> com.goodwy.commons.R.string.medium
            CONTACT_THUMBNAILS_SIZE_LARGE -> com.goodwy.commons.R.string.large
            else -> com.goodwy.commons.R.string.extra_large
        }
    )

    private fun setupUseRelativeDate() = binding.apply {
        settingsRelativeDate.isChecked = config.useRelativeDate
        settingsRelativeDateHolder.setOnClickListener {
            settingsRelativeDate.toggle()
            config.useRelativeDate = settingsRelativeDate.isChecked
            config.tabsChanged = true
        }
    }

    private fun setupUnreadAtTop() = binding.apply {
        settingsUnreadAtTop.isChecked = config.unreadAtTop
        settingsUnreadAtTopHolder.setOnClickListener {
            settingsUnreadAtTop.toggle()
            config.unreadAtTop = settingsUnreadAtTop.isChecked
            config.tabsChanged = true
        }
    }

    private fun setupLinesCount() = binding.apply {
        settingsLinesCount.text = config.linesCount.toString()
        settingsLinesCountHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(1, "1", icon = R.drawable.ic_lines_count_1),
                RadioItem(2, "2", icon = R.drawable.ic_lines_count_2),
                RadioItem(3, "3", icon = R.drawable.ic_lines_count_3),
                RadioItem(4, "4", icon = R.drawable.ic_lines_count_4)
            )

            RadioGroupIconDialog(this@SettingsActivity, items, config.linesCount, com.goodwy.strings.R.string.lines_count) {
                config.linesCount = it as Int
                settingsLinesCount.text = it.toString()
                config.tabsChanged = true
            }
        }
    }

    private fun setupUnreadIndicatorPosition() = binding.apply {
        settingsUnreadIndicatorPosition.text = getUnreadIndicatorPositionText()
        settingsUnreadIndicatorPositionHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(UNREAD_INDICATOR_START, getString(com.goodwy.strings.R.string.start), icon = R.drawable.ic_unread_start),
                RadioItem(UNREAD_INDICATOR_END, getString(com.goodwy.strings.R.string.end), icon = R.drawable.ic_unread_end)
            )

            RadioGroupIconDialog(this@SettingsActivity, items, config.unreadIndicatorPosition, com.goodwy.strings.R.string.unread_indicator_position) {
                config.unreadIndicatorPosition = it as Int
                settingsUnreadIndicatorPosition.text = getUnreadIndicatorPositionText()
                config.tabsChanged = true
            }
        }
    }

    private fun getUnreadIndicatorPositionText() = getString(
        when (config.unreadIndicatorPosition) {
            UNREAD_INDICATOR_START -> com.goodwy.strings.R.string.start
            else -> com.goodwy.strings.R.string.end
        }
    )

    private fun setupHideTopBarWhenScroll() = binding.apply {
        settingsHideBarWhenScroll.isChecked = config.hideTopBarWhenScroll
        settingsHideBarWhenScrollHolder.setOnClickListener {
            settingsHideBarWhenScroll.toggle()
            config.hideTopBarWhenScroll = settingsHideBarWhenScroll.isChecked
            config.tabsChanged = true
        }
    }

    private fun setupUseColoredContacts() = binding.apply {
        settingsColoredContacts.isChecked = config.useColoredContacts
        settingsColoredContactsHolder.setOnClickListener {
            settingsColoredContacts.toggle()
            config.useColoredContacts = settingsColoredContacts.isChecked
            settingsContactColorListHolder.beVisibleIf(config.useColoredContacts)
            config.tabsChanged = true
        }
    }

    private fun setupContactsColorList() = binding.apply {
        settingsContactColorListHolder.beVisibleIf(config.useColoredContacts)
        settingsContactColorListIcon.setImageResource(getContactsColorListIcon(config.contactColorList))
        settingsContactColorListHolder.setOnClickListener {
            ColorListDialog(this@SettingsActivity) {
                config.contactColorList = it as Int
                settingsContactColorListIcon.setImageResource(getContactsColorListIcon(it))
                config.tabsChanged = true
            }
        }
    }

    private fun setupColorSimIcons() = binding.apply {
        settingsColorSimCardIconsHolder.beGoneIf(!areMultipleSIMsAvailable())
        settingsColorSimCardIcons.isChecked = config.colorSimIcons
        settingsColorSimCardIconsHolder.setOnClickListener {
            settingsColorSimCardIcons.toggle()
            config.colorSimIcons = settingsColorSimCardIcons.isChecked
            settingsSimCardColorListHolder.beVisibleIf(config.colorSimIcons)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupSimCardColorList() = binding.apply {
        settingsSimCardColorListHolder.beVisibleIf(config.colorSimIcons && areMultipleSIMsAvailable())
        settingsSimCardColorListIcon1.setColorFilter(config.simIconsColors[1])
        settingsSimCardColorListIcon2.setColorFilter(config.simIconsColors[2])
        if (isPro()) {
            settingsSimCardColorListIcon1.setOnClickListener {
                ColorPickerDialog(
                    this@SettingsActivity,
                    config.simIconsColors[1],
                    addDefaultColorButton = true,
                    colorDefault = resources.getColor(com.goodwy.commons.R.color.ic_dialer),
                    title = resources.getString(com.goodwy.strings.R.string.color_sim_card_icons)
                ) { wasPositivePressed, color ->
                    if (wasPositivePressed) {
                        if (hasColorChanged(config.simIconsColors[1], color)) {
                            addSimCardColor(1, color)
                            settingsSimCardColorListIcon1.setColorFilter(color)
                        }
                    }
                }
            }
            settingsSimCardColorListIcon2.setOnClickListener {
                ColorPickerDialog(
                    this@SettingsActivity,
                    config.simIconsColors[2],
                    addDefaultColorButton = true,
                    colorDefault = resources.getColor(com.goodwy.commons.R.color.color_primary),
                    title = resources.getString(com.goodwy.strings.R.string.color_sim_card_icons)
                ) { wasPositivePressed, color ->
                    if (wasPositivePressed) {
                        if (hasColorChanged(config.simIconsColors[2], color)) {
                            addSimCardColor(2, color)
                            settingsSimCardColorListIcon2.setColorFilter(color)
                        }
                    }
                }
            }
        } else {
            settingsSimCardColorListLabel.text = "${getString(com.goodwy.commons.R.string.change_color)} (${getString(com.goodwy.commons.R.string.feature_locked)})"
            arrayOf(
                settingsSimCardColorListIcon1,
                settingsSimCardColorListIcon2
            ).forEach {
                it.setOnClickListener {
                    launchPurchase()
                }
            }
        }
    }

    private fun addSimCardColor(index: Int, color: Int) {
        val recentColors = config.simIconsColors

        recentColors.removeAt(index)
        recentColors.add(index, color)

        baseConfig.simIconsColors = recentColors
    }

    private fun hasColorChanged(old: Int, new: Int) = abs(old - new) > 1

    private fun setupTipJar() = binding.apply {
        settingsTipJarHolder.apply {
            beVisibleIf(isPro())
            background.applyColorFilter(getBottomNavigationBackgroundColor().lightenColor(4))
            setOnClickListener {
                launchPurchase()
            }
        }
    }

    private fun setupAbout() = binding.apply {
        settingsAboutVersion.text = "Version: " + BuildConfig.VERSION_NAME
        settingsAboutHolder.setOnClickListener {
            launchAbout()
        }
    }

    private fun launchAbout() {
        val licenses = LICENSE_EVENT_BUS or LICENSE_SMS_MMS or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_2_title, R.string.faq_2_text),
            FAQItem(R.string.faq_3_title, R.string.faq_3_text),
            FAQItem(com.goodwy.commons.R.string.faq_9_title_commons, com.goodwy.commons.R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(com.goodwy.commons.R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(com.goodwy.commons.R.string.faq_2_title_commons, com.goodwy.strings.R.string.faq_2_text_commons_g))
            //faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
        }

        startAboutActivity(
            appNameId = R.string.app_name_g,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true,
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

    private fun updatePro(isPro: Boolean = isPro()) {
        binding.apply {
            settingsPurchaseThankYouHolder.beGoneIf(isPro)
//            settingsCustomizeColorsLabel.text = if (isPro) {
//                getString(com.goodwy.commons.R.string.customize_colors)
//            } else {
//                getString(com.goodwy.commons.R.string.customize_colors_locked)
//            }
            settingsTipJarHolder.beVisibleIf(isPro)

            val stringId = if (isRTLLayout) com.goodwy.strings.R.string.swipe_right_action else com.goodwy.strings.R.string.swipe_left_action
            settingsSwipeLeftActionLabel.text = addLockedLabelIfNeeded(stringId, isPro)
        }
    }

    private fun updateProducts() {
        val productList: ArrayList<String> = arrayListOf(productIdX1, productIdX2, productIdX3, subscriptionIdX1, subscriptionIdX2, subscriptionIdX3, subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3)
        ruStoreHelper.getProducts(productList)
    }

    private fun handleEventStart(event: StartPurchasesEvent) {
        when (event) {
            is StartPurchasesEvent.PurchasesAvailability -> {
                when (event.availability) {
                    is FeatureAvailabilityResult.Available -> {
                        //Process purchases available
                        updateProducts()
                        ruStoreIsConnected = true
                    }

                    is FeatureAvailabilityResult.Unavailable -> {
                        //toast(event.availability.cause.message ?: "Process purchases unavailable", Toast.LENGTH_LONG)
                    }

                    else -> {}
                }
            }

            is StartPurchasesEvent.Error -> {
                //toast(event.throwable.message ?: "Process unknown error", Toast.LENGTH_LONG)
            }
        }
    }
}

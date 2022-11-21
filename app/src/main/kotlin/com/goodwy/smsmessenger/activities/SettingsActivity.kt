package com.goodwy.smsmessenger.activities

import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.annotation.TargetApi
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.goodwy.commons.activities.ManageBlockedNumbersActivity
import com.goodwy.commons.dialogs.ChangeDateTimeFormatDialog
import com.goodwy.commons.dialogs.FilePickerDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.dialogs.SettingsIconDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.FAQItem
import com.goodwy.commons.models.RadioItem
import com.goodwy.smsmessenger.App.Companion.isProVersion
import com.goodwy.smsmessenger.BuildConfig
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.dialogs.ExportMessagesDialog
import com.goodwy.smsmessenger.dialogs.ImportMessagesDialog
import com.goodwy.smsmessenger.dialogs.MessageBubbleSettingDialog
import com.goodwy.smsmessenger.extensions.config
import com.goodwy.smsmessenger.helpers.*
import kotlinx.android.synthetic.main.activity_settings.*
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*

class SettingsActivity : SimpleActivity() {
    private var blockedNumbersAtPause = -1
    private val PICK_IMPORT_SOURCE_INTENT = 11
    private val PICK_EXPORT_FILE_INTENT = 21
    private val smsExporter by lazy { MessagesExporter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(settings_toolbar, NavigationIcon.Arrow, appBarLayout = settings_app_bar_layout)
        updateNavigationBarColor(isColorPreview = true)

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupMaterialDesign3()
        setupSettingsIcon()
        setupThreadTopStyle()
        setupMessageBubble()

        setupImportMessages()
        setupExportMessages()
        setupManageBlockedNumbers()
        setupFontSize()
        setupChangeDateTimeFormat()
        setupUseEnglish()
        setupLanguage()

        setupCustomizeNotifications()
        setupLockScreenVisibility()

        setupActionOnMessageClick()
        setupEnableDeliveryReports()
        setupShowCharacterCounter()
        setupUseSimpleCharacters()
        setupSendLongMessageAsMMS()
        setupGroupMessageAsMMS()
        setupMMSFileSizeLimit()

        setupShowDividers()
        setupShowContactThumbnails()
        setupUseColoredContacts()

        setupTipJar()
        setupAbout()
        updateTextColors(settings_nested_scrollview)

        if (blockedNumbersAtPause != -1 && blockedNumbersAtPause != getBlockedNumbers().hashCode()) {
            refreshMessages()
        }

        arrayOf(divider_general, divider_notifications, divider_outgoing_messages, divider_list_view, divider_other).forEach {
            it.setBackgroundColor(getProperTextColor())
        }
        arrayOf(settings_appearance_label, settings_general_label, settings_notifications_label,
            settings_outgoing_messages_label, settings_list_view_label, settings_other_label).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    override fun onPause() {
        super.onPause()
        blockedNumbersAtPause = getBlockedNumbers().hashCode()
    }

    private fun setupPurchaseThankYou() {
        settings_purchase_thank_you_holder.beGoneIf(isOrWasThankYouInstalled() || isProVersion())
        settings_purchase_thank_you_holder.setOnClickListener {
            launchPurchase() //launchPurchaseThankYouIntent()
        }
        moreButton.setOnClickListener {
            launchPurchase()
        }
        val appDrawable = resources.getColoredDrawableWithColor(R.drawable.ic_plus_support, getProperPrimaryColor())
        purchase_logo.setImageDrawable(appDrawable)
        val drawable = resources.getColoredDrawableWithColor(R.drawable.button_gray_bg, getProperPrimaryColor())
        moreButton.background = drawable
        moreButton.setTextColor(getProperBackgroundColor())
        moreButton.setPadding(2,2,2,2)
    }

    private fun setupCustomizeColors() {
        settings_customize_colors_chevron.applyColorFilter(getProperTextColor())
        settings_customize_colors_label.text = if (isOrWasThankYouInstalled() || isProVersion()) {
            getString(R.string.customize_colors)
        } else {
            getString(R.string.customize_colors_locked)
        }
        settings_customize_colors_holder.setOnClickListener {
            //startCustomizationActivity()
            if (isOrWasThankYouInstalled() || isProVersion()) {
                startCustomizationActivity()
            } else {
                launchPurchase()
            }
        }
    }

    private fun setupCustomizeNotifications() {
        settings_customize_notifications_chevron.applyColorFilter(getProperTextColor())
        settings_customize_notifications_holder.beVisibleIf(isOreoPlus())

        if (settings_customize_notifications_holder.isGone()) {
            settings_lock_screen_visibility_holder.background = resources.getDrawable(R.drawable.ripple_all_corners, theme)
        }

        settings_customize_notifications_holder.setOnClickListener {
            launchCustomizeNotificationsIntent()
        }
    }

    private fun setupMaterialDesign3() {
        settings_material_design_3.isChecked = config.materialDesign3
        settings_material_design_3_holder.setOnClickListener {
            settings_material_design_3.toggle()
            config.materialDesign3 = settings_material_design_3.isChecked
            config.tabsChanged = true
        }
    }

    private fun setupSettingsIcon() {
        settings_icon.applyColorFilter(getProperTextColor())
        settings_icon.setImageResource(getSettingsIcon(config.settingsIcon))
        settings_icon_holder.setOnClickListener {
            SettingsIconDialog(this) {
                config.settingsIcon = it as Int
                settings_icon.setImageResource(getSettingsIcon(it))
            }
        }
    }

    private fun setupThreadTopStyle() {
        settings_thread_top_style.text = getThreadTopStyleText()
        settings_thread_top_style_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(THREAD_TOP_COMPACT, getString(R.string.small)),
                RadioItem(THREAD_TOP_LARGE, getString(R.string.large))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.threadTopStyle) {
                config.threadTopStyle = it as Int
                settings_thread_top_style.text = getThreadTopStyleText()
            }
        }
    }

    private fun getThreadTopStyleText() = getString(
        when (config.threadTopStyle) {
            THREAD_TOP_COMPACT -> R.string.small
            THREAD_TOP_LARGE -> R.string.large
            else -> R.string.large
        }
    )

    private fun setupMessageBubble() {
        settings_message_bubble_chevron.applyColorFilter(getProperTextColor())
        settings_message_bubble_holder.setOnClickListener {
            MessageBubbleSettingDialog(this) {
            }
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settings_use_english.isChecked = config.useEnglish
        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            System.exit(0)
        }
    }

    private fun setupLanguage() {
        settings_language.text = Locale.getDefault().displayLanguage
        settings_language_holder.beVisibleIf(isTiramisuPlus())

        settings_language_holder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    // support for device-wise blocking came on Android 7, rely only on that
    @TargetApi(Build.VERSION_CODES.N)
    private fun setupManageBlockedNumbers() {
        settings_manage_blocked_numbers_chevron.applyColorFilter(getProperTextColor())
        settings_manage_blocked_numbers_holder.beVisibleIf(isNougatPlus())
        settings_manage_blocked_numbers_holder.setOnClickListener {
            startActivity(Intent(this, ManageBlockedNumbersActivity::class.java))
        }
    }

    private fun setupChangeDateTimeFormat() {
        settings_change_date_time_format_chevron.applyColorFilter(getProperTextColor())
        settings_change_date_time_format_holder.setOnClickListener {
            ChangeDateTimeFormatDialog(this) {
                refreshMessages()
            }
        }
    }

    private fun setupFontSize() {
        settings_font_size.text = getFontSizeText()
        settings_font_size_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(R.string.extra_large))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                settings_font_size.text = getFontSizeText()
            }
        }
    }

    private fun setupShowCharacterCounter() {
        settings_show_character_counter.isChecked = config.showCharacterCounter
        settings_show_character_counter_holder.setOnClickListener {
            settings_show_character_counter.toggle()
            config.showCharacterCounter = settings_show_character_counter.isChecked
        }
    }

    private fun setupActionOnMessageClick() {
        settings_action_on_message_click.text = getActionOnMessageClickText()
        settings_action_on_message_click_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(ACTION_COPY_CODE, getString(R.string.copy_code)),
                RadioItem(ACTION_COPY_MESSAGE, getString(R.string.copy_to_clipboard)),
                RadioItem(ACTION_NOTHING, getString(R.string.nothing)),
            )

            RadioGroupDialog(this@SettingsActivity, items, config.actionOnMessageClickSetting) {
                config.actionOnMessageClickSetting = it as Int
                settings_action_on_message_click.text = getActionOnMessageClickText()
            }
        }
    }

    private fun getActionOnMessageClickText() = getString(
        when (config.actionOnMessageClickSetting) {
            ACTION_COPY_CODE -> R.string.copy_code
            ACTION_COPY_MESSAGE -> R.string.copy_to_clipboard
            else -> R.string.nothing
        }
    )

    private fun setupUseSimpleCharacters() {
        settings_use_simple_characters.isChecked = config.useSimpleCharacters
        settings_use_simple_characters_holder.setOnClickListener {
            settings_use_simple_characters.toggle()
            config.useSimpleCharacters = settings_use_simple_characters.isChecked
        }
    }

    private fun setupEnableDeliveryReports() {
        settings_enable_delivery_reports.isChecked = config.enableDeliveryReports
        settings_enable_delivery_reports_holder.setOnClickListener {
            settings_enable_delivery_reports.toggle()
            config.enableDeliveryReports = settings_enable_delivery_reports.isChecked
        }
    }

    private fun setupSendLongMessageAsMMS() {
        settings_send_long_message_mms.isChecked = config.sendLongMessageMMS
        settings_send_long_message_mms_holder.setOnClickListener {
            settings_send_long_message_mms.toggle()
            config.sendLongMessageMMS = settings_send_long_message_mms.isChecked
        }
    }

    private fun setupGroupMessageAsMMS() {
        settings_send_group_message_mms.isChecked = config.sendGroupMessageMMS
        settings_send_group_message_mms_holder.setOnClickListener {
            settings_send_group_message_mms.toggle()
            config.sendGroupMessageMMS = settings_send_group_message_mms.isChecked
        }
    }

    private fun setupLockScreenVisibility() {
        settings_lock_screen_visibility.text = getLockScreenVisibilityText()
        settings_lock_screen_visibility_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(LOCK_SCREEN_SENDER_MESSAGE, getString(R.string.sender_and_message)),
                RadioItem(LOCK_SCREEN_SENDER, getString(R.string.sender_only)),
                RadioItem(LOCK_SCREEN_NOTHING, getString(R.string.nothing)),
            )

            RadioGroupDialog(this@SettingsActivity, items, config.lockScreenVisibilitySetting) {
                config.lockScreenVisibilitySetting = it as Int
                settings_lock_screen_visibility.text = getLockScreenVisibilityText()
            }
        }
    }

    private fun getLockScreenVisibilityText() = getString(
        when (config.lockScreenVisibilitySetting) {
            LOCK_SCREEN_SENDER_MESSAGE -> R.string.sender_and_message
            LOCK_SCREEN_SENDER -> R.string.sender_only
            else -> R.string.nothing
        }
    )

    private fun setupMMSFileSizeLimit() {
        settings_mms_file_size_limit.text = getMMSFileLimitText()
        settings_mms_file_size_limit_holder.setOnClickListener {
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
            RadioGroupDialog(this@SettingsActivity, items, checkedItemId) {
                config.mmsFileSizeLimit = it as Long
                settings_mms_file_size_limit.text = getMMSFileLimitText()
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

    private fun setupShowDividers() {
        settings_show_dividers.isChecked = config.useDividers
        settings_show_dividers_holder.setOnClickListener {
            settings_show_dividers.toggle()
            config.useDividers = settings_show_dividers.isChecked
            config.tabsChanged = true
        }
    }

    private fun setupShowContactThumbnails() {
        settings_show_contact_thumbnails.isChecked = config.showContactThumbnails
        settings_show_contact_thumbnails_holder.setOnClickListener {
            settings_show_contact_thumbnails.toggle()
            config.showContactThumbnails = settings_show_contact_thumbnails.isChecked
            config.tabsChanged = true
        }
    }

    private fun launchPurchase() {
        startPurchaseActivity(R.string.app_name_g, BuildConfig.GOOGLE_PLAY_LICENSING_KEY, BuildConfig.PRODUCT_ID_X1, BuildConfig.PRODUCT_ID_X2, BuildConfig.PRODUCT_ID_X3)
    }

    private fun setupImportMessages() {
        settings_import_messages_chevron.applyColorFilter(getProperTextColor())
        settings_import_messages_holder.setOnClickListener { tryImportMessages() }
    }

    private fun setupExportMessages() {
        settings_export_messages_chevron.applyColorFilter(getProperTextColor())
        settings_export_messages_holder.setOnClickListener { tryToExportMessages() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == PICK_IMPORT_SOURCE_INTENT && resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            tryImportMessagesFromFile(resultData.data!!)
        } else if (requestCode == PICK_EXPORT_FILE_INTENT && resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            val outputStream = contentResolver.openOutputStream(resultData.data!!)
            exportMessagesTo(outputStream)
        }
    }

    private fun tryToExportMessages() {
        if (isQPlus()) {
            ExportMessagesDialog(this, config.lastExportPath, true) { file ->
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = EXPORT_MIME_TYPE
                    putExtra(Intent.EXTRA_TITLE, file.name)
                    addCategory(Intent.CATEGORY_OPENABLE)

                    try {
                        startActivityForResult(this, PICK_EXPORT_FILE_INTENT)
                    } catch (e: ActivityNotFoundException) {
                        toast(R.string.system_service_disabled, Toast.LENGTH_LONG)
                    } catch (e: Exception) {
                        showErrorToast(e)
                    }
                }
            }
        } else {
            handlePermission(PERMISSION_WRITE_STORAGE) {
                if (it) {
                    ExportMessagesDialog(this, config.lastExportPath, false) { file ->
                        getFileOutputStream(file.toFileDirItem(this), true) { outStream ->
                            exportMessagesTo(outStream)
                        }
                    }
                }
            }
        }
    }

    private fun exportMessagesTo(outputStream: OutputStream?) {
        toast(R.string.exporting)
        ensureBackgroundThread {
            smsExporter.exportMessages(outputStream) {
                val toastId = when (it) {
                    MessagesExporter.ExportResult.EXPORT_OK -> R.string.exporting_successful
                    else -> R.string.exporting_failed
                }

                toast(toastId)
            }
        }
    }

    private fun tryImportMessages() {
        if (isQPlus()) {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = EXPORT_MIME_TYPE

                try {
                    startActivityForResult(this, PICK_IMPORT_SOURCE_INTENT)
                } catch (e: ActivityNotFoundException) {
                    toast(R.string.system_service_disabled, Toast.LENGTH_LONG)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        } else {
            handlePermission(PERMISSION_READ_STORAGE) {
                if (it) {
                    importEvents()
                }
            }
        }
    }

    private fun importEvents() {
        FilePickerDialog(this) {
            showImportEventsDialog(it)
        }
    }

    private fun showImportEventsDialog(path: String) {
        ImportMessagesDialog(this, path)
    }

    private fun tryImportMessagesFromFile(uri: Uri) {
        when (uri.scheme) {
            "file" -> showImportEventsDialog(uri.path!!)
            "content" -> {
                val tempFile = getTempFile("messages", "backup.json")
                if (tempFile == null) {
                    toast(R.string.unknown_error_occurred)
                    return
                }

                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val out = FileOutputStream(tempFile)
                    inputStream!!.copyTo(out)
                    showImportEventsDialog(tempFile.absolutePath)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
            else -> toast(R.string.invalid_file_format)
        }
    }

    private fun setupUseColoredContacts() {
        settings_colored_contacts.isChecked = config.useColoredContacts
        settings_colored_contacts_holder.setOnClickListener {
            settings_colored_contacts.toggle()
            config.useColoredContacts = settings_colored_contacts.isChecked
            config.tabsChanged = true
        }
    }

    private fun setupTipJar() {
        settings_tip_jar_holder.beVisibleIf(isOrWasThankYouInstalled() || isProVersion())
        settings_tip_jar_chevron.applyColorFilter(getProperTextColor())
        settings_tip_jar_holder.setOnClickListener {
            launchPurchase()
        }
    }

    private fun setupAbout() {
        settings_about_chevron.applyColorFilter(getProperTextColor())
        settings_about_version.text = "Version: " + BuildConfig.VERSION_NAME
        settings_about_holder.setOnClickListener {
            launchAbout()
        }
    }

    private fun launchAbout() {
        val licenses = LICENSE_EVENT_BUS or LICENSE_SMS_MMS or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_2_title, R.string.faq_2_text),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons_g))
            //faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
        }

        startAboutActivity(R.string.app_name_g, licenses, BuildConfig.VERSION_NAME, faqItems, true,
            BuildConfig.GOOGLE_PLAY_LICENSING_KEY, BuildConfig.PRODUCT_ID_X1, BuildConfig.PRODUCT_ID_X2, BuildConfig.PRODUCT_ID_X3)
    }
}

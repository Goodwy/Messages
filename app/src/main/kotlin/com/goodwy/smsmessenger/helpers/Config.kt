package com.goodwy.smsmessenger.helpers

import android.content.Context
import com.goodwy.commons.helpers.BaseConfig
import com.goodwy.smsmessenger.extensions.getDefaultKeyboardHeight
import com.goodwy.smsmessenger.models.Conversation
import androidx.core.content.edit

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    fun saveUseSIMIdAtNumber(number: String, SIMId: Int) {
        prefs.edit { putInt(USE_SIM_ID_PREFIX + number, SIMId) }
    }

    fun getUseSIMIdAtNumber(number: String) = prefs.getInt(USE_SIM_ID_PREFIX + number, 0)

    var showCharacterCounter: Boolean
        get() = prefs.getBoolean(SHOW_CHARACTER_COUNTER, false)
        set(showCharacterCounter) = prefs.edit { putBoolean(SHOW_CHARACTER_COUNTER, showCharacterCounter) }

    var useSimpleCharacters: Boolean
        get() = prefs.getBoolean(USE_SIMPLE_CHARACTERS, false)
        set(useSimpleCharacters) = prefs.edit { putBoolean(USE_SIMPLE_CHARACTERS, useSimpleCharacters) }

    var sendOnEnter: Boolean
        get() = prefs.getBoolean(SEND_ON_ENTER, false)
        set(sendOnEnter) = prefs.edit { putBoolean(SEND_ON_ENTER, sendOnEnter) }

    var enableDeliveryReports: Boolean
        get() = prefs.getBoolean(ENABLE_DELIVERY_REPORTS, false)
        set(enableDeliveryReports) = prefs.edit { putBoolean(ENABLE_DELIVERY_REPORTS, enableDeliveryReports) }

    var sendLongMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_LONG_MESSAGE_MMS, false)
        set(sendLongMessageMMS) = prefs.edit { putBoolean(SEND_LONG_MESSAGE_MMS, sendLongMessageMMS) }

    var sendGroupMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_GROUP_MESSAGE_MMS, false)
        set(sendGroupMessageMMS) = prefs.edit { putBoolean(SEND_GROUP_MESSAGE_MMS, sendGroupMessageMMS) }

    var lockScreenVisibilitySetting: Int
        get() = prefs.getInt(LOCK_SCREEN_VISIBILITY, LOCK_SCREEN_SENDER_MESSAGE)
        set(lockScreenVisibilitySetting) = prefs.edit { putInt(LOCK_SCREEN_VISIBILITY, lockScreenVisibilitySetting) }

    var mmsFileSizeLimit: Long
        get() = prefs.getLong(MMS_FILE_SIZE_LIMIT, FILE_SIZE_600_KB)
        set(mmsFileSizeLimit) = prefs.edit { putLong(MMS_FILE_SIZE_LIMIT, mmsFileSizeLimit) }

    var pinnedConversations: Set<String>
        get() = prefs.getStringSet(PINNED_CONVERSATIONS, HashSet<String>())!!
        set(pinnedConversations) = prefs.edit { putStringSet(PINNED_CONVERSATIONS, pinnedConversations) }

    fun addPinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.plus(threadId.toString())
    }

    fun addPinnedConversations(conversations: List<Conversation>) {
        pinnedConversations = pinnedConversations.plus(conversations.map { it.threadId.toString() })
    }

    fun removePinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.minus(threadId.toString())
    }

    fun removePinnedConversations(conversations: List<Conversation>) {
        pinnedConversations = pinnedConversations.minus(conversations.map { it.threadId.toString() })
    }

    var blockedKeywords: Set<String>
        get() = prefs.getStringSet(BLOCKED_KEYWORDS, HashSet<String>())!!
        set(blockedKeywords) = prefs.edit { putStringSet(BLOCKED_KEYWORDS, blockedKeywords) }

    fun addBlockedKeyword(keyword: String) {
        blockedKeywords = blockedKeywords.plus(keyword)
    }

    fun removeBlockedKeyword(keyword: String) {
        blockedKeywords = blockedKeywords.minus(keyword)
    }

    var exportSms: Boolean
        get() = prefs.getBoolean(EXPORT_SMS, true)
        set(exportSms) = prefs.edit { putBoolean(EXPORT_SMS, exportSms) }

    var exportMms: Boolean
        get() = prefs.getBoolean(EXPORT_MMS, true)
        set(exportMms) = prefs.edit { putBoolean(EXPORT_MMS, exportMms) }

    var importSms: Boolean
        get() = prefs.getBoolean(IMPORT_SMS, true)
        set(importSms) = prefs.edit { putBoolean(IMPORT_SMS, importSms) }

    var importMms: Boolean
        get() = prefs.getBoolean(IMPORT_MMS, true)
        set(importMms) = prefs.edit { putBoolean(IMPORT_MMS, importMms) }

    var wasDbCleared: Boolean
        get() = prefs.getBoolean(WAS_DB_CLEARED, false)
        set(wasDbCleared) = prefs.edit { putBoolean(WAS_DB_CLEARED, wasDbCleared) }

    var keyboardHeight: Int
        get() = prefs.getInt(SOFT_KEYBOARD_HEIGHT, context.getDefaultKeyboardHeight())
        set(keyboardHeight) = prefs.edit { putInt(SOFT_KEYBOARD_HEIGHT, keyboardHeight) }


    var useRecycleBin: Boolean
        get() = prefs.getBoolean(USE_RECYCLE_BIN, true)
        set(useRecycleBin) = prefs.edit { putBoolean(USE_RECYCLE_BIN, useRecycleBin) }

    var lastRecycleBinCheck: Long
        get() = prefs.getLong(LAST_RECYCLE_BIN_CHECK, 0L)
        set(lastRecycleBinCheck) = prefs.edit { putLong(LAST_RECYCLE_BIN_CHECK, lastRecycleBinCheck) }

    var isArchiveAvailable: Boolean
        get() = prefs.getBoolean(IS_ARCHIVE_AVAILABLE, true)
        set(isArchiveAvailable) = prefs.edit { putBoolean(IS_ARCHIVE_AVAILABLE, isArchiveAvailable) }

    var customNotifications: Set<String>
        get() = prefs.getStringSet(CUSTOM_NOTIFICATIONS, HashSet<String>())!!
        set(customNotifications) = prefs.edit { putStringSet(CUSTOM_NOTIFICATIONS, customNotifications) }

    fun addCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.plus(threadId.toString())
    }

    fun removeCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.minus(threadId.toString())
    }

    var lastBlockedKeywordExportPath: String
        get() = prefs.getString(LAST_BLOCKED_KEYWORD_EXPORT_PATH, "")!!
        set(lastBlockedNumbersExportPath) = prefs.edit {
            putString(LAST_BLOCKED_KEYWORD_EXPORT_PATH, lastBlockedNumbersExportPath)
        }

    var keepConversationsArchived: Boolean
        get() = prefs.getBoolean(KEEP_CONVERSATIONS_ARCHIVED, false)
        set(keepConversationsArchived) = prefs.edit {
            putBoolean(KEEP_CONVERSATIONS_ARCHIVED, keepConversationsArchived)
        }

    //Goodwy
    var bubbleStyle: Int
        get() = prefs.getInt(BUBBLE_STYLE, BUBBLE_STYLE_IOS_NEW)
        set(bubbleStyle) = prefs.edit { putInt(BUBBLE_STYLE, bubbleStyle) }

    var bubbleInvertColor: Boolean
        get() = prefs.getBoolean(BUBBLE_INVERT_COLOR, false)
        set(bubbleInvertColor) = prefs.edit { putBoolean(BUBBLE_INVERT_COLOR, bubbleInvertColor) }

    var bubbleInContactColor: Boolean
        get() = prefs.getBoolean(BUBBLE_IN_CONTACT_COLOR, false)
        set(bubbleInContactColor) = prefs.edit { putBoolean(BUBBLE_IN_CONTACT_COLOR, bubbleInContactColor) }

    var actionOnMessageClickSetting: Int
        get() = prefs.getInt(ACTION_ON_MESSAGE_CLICK, ACTION_NOTHING)
        set(actionOnMessageClickSetting) = prefs.edit { putInt(ACTION_ON_MESSAGE_CLICK, actionOnMessageClickSetting) }

    var threadTopStyle: Int
        get() = prefs.getInt(THREAD_TOP_STYLE, THREAD_TOP_LARGE)
        set(threadTopStyle) = prefs.edit { putInt(THREAD_TOP_STYLE, threadTopStyle) }

    var unreadAtTop: Boolean
        get() = prefs.getBoolean(UNREAD_AT_TOP, false)
        set(unreadAtTop) = prefs.edit { putBoolean(UNREAD_AT_TOP, unreadAtTop) }

    var unreadIndicatorPosition: Int
        get() = prefs.getInt(UNREAD_INDICATOR_POSITION, UNREAD_INDICATOR_END)
        set(unreadIndicatorPosition) = prefs.edit { putInt(UNREAD_INDICATOR_POSITION, unreadIndicatorPosition) }

    var showSimSelectionDialog: Boolean
        get() = prefs.getBoolean(SHOW_SIM_SELECTION_DIALOG, false)
        set(showSimSelectionDialog) = prefs.edit { putBoolean(SHOW_SIM_SELECTION_DIALOG, showSimSelectionDialog) }

    var copyNumberAndDelete: Boolean
        get() = prefs.getBoolean(COPY_NUMBER_AND_DELETE_PREF, false)
        set(copyNumberAndDelete) = prefs.edit { putBoolean(COPY_NUMBER_AND_DELETE_PREF, copyNumberAndDelete) }

    var fontSizeMessage: Int
        get() = prefs.getInt(FONT_SIZE_MESSAGE, 1)
        set(fontSizeMessage) = prefs.edit { putInt(FONT_SIZE_MESSAGE, fontSizeMessage) }

    var soundOnOutGoingMessages: Boolean
        get() = prefs.getBoolean(SOUND_ON_OUT_GOING_MESSAGE, false)
        set(soundOnOutGoingMessages) = prefs.edit { putBoolean(SOUND_ON_OUT_GOING_MESSAGE, soundOnOutGoingMessages) }

    var notifyTurnsOnScreen: Boolean
        get() = prefs.getBoolean(NOTIFY_TURN_ON_SCREEN, false)
        set(notifyTurnsOnScreen) = prefs.edit { putBoolean(NOTIFY_TURN_ON_SCREEN, notifyTurnsOnScreen) }

    var initCallBlockingSetup: Boolean
        get() = prefs.getBoolean(INIT_CALL_BLOCKING_SETUP, true)
        set(initCallBlockingSetup) = prefs.edit { putBoolean(INIT_CALL_BLOCKING_SETUP, initCallBlockingSetup) }

    //Swipe
    var swipeRightAction: Int
        get() = prefs.getInt(SWIPE_RIGHT_ACTION, SWIPE_ACTION_MARK_READ)
        set(swipeRightAction) = prefs.edit { putInt(SWIPE_RIGHT_ACTION, swipeRightAction) }

    var swipeLeftAction: Int
        get() = prefs.getInt(SWIPE_LEFT_ACTION, SWIPE_ACTION_DELETE)
        set(swipeLeftAction) = prefs.edit { putInt(SWIPE_LEFT_ACTION, swipeLeftAction) }

    var swipeVibration: Boolean
        get() = prefs.getBoolean(SWIPE_VIBRATION, true)
        set(swipeVibration) = prefs.edit { putBoolean(SWIPE_VIBRATION, swipeVibration) }

    var swipeRipple: Boolean
        get() = prefs.getBoolean(SWIPE_RIPPLE, false)
        set(swipeRipple) = prefs.edit { putBoolean(SWIPE_RIPPLE, swipeRipple) }
}

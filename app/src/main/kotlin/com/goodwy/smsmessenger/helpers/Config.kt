package com.goodwy.smsmessenger.helpers

import android.content.Context
import com.goodwy.commons.helpers.BaseConfig
import com.goodwy.smsmessenger.models.Conversation

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    fun saveUseSIMIdAtNumber(number: String, SIMId: Int) {
        prefs.edit().putInt(USE_SIM_ID_PREFIX + number, SIMId).apply()
    }

    fun getUseSIMIdAtNumber(number: String) = prefs.getInt(USE_SIM_ID_PREFIX + number, 0)

    var showCharacterCounter: Boolean
        get() = prefs.getBoolean(SHOW_CHARACTER_COUNTER, false)
        set(showCharacterCounter) = prefs.edit().putBoolean(SHOW_CHARACTER_COUNTER, showCharacterCounter).apply()

    var useSimpleCharacters: Boolean
        get() = prefs.getBoolean(USE_SIMPLE_CHARACTERS, false)
        set(useSimpleCharacters) = prefs.edit().putBoolean(USE_SIMPLE_CHARACTERS, useSimpleCharacters).apply()

    var enableDeliveryReports: Boolean
        get() = prefs.getBoolean(ENABLE_DELIVERY_REPORTS, false)
        set(enableDeliveryReports) = prefs.edit().putBoolean(ENABLE_DELIVERY_REPORTS, enableDeliveryReports).apply()

    var sendLongMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_LONG_MESSAGE_MMS, false)
        set(sendLongMessageMMS) = prefs.edit().putBoolean(SEND_LONG_MESSAGE_MMS, sendLongMessageMMS).apply()

    var sendGroupMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_GROUP_MESSAGE_MMS, false)
        set(sendGroupMessageMMS) = prefs.edit().putBoolean(SEND_GROUP_MESSAGE_MMS, sendGroupMessageMMS).apply()

    var lockScreenVisibilitySetting: Int
        get() = prefs.getInt(LOCK_SCREEN_VISIBILITY, LOCK_SCREEN_SENDER_MESSAGE)
        set(lockScreenVisibilitySetting) = prefs.edit().putInt(LOCK_SCREEN_VISIBILITY, lockScreenVisibilitySetting).apply()

    var mmsFileSizeLimit: Long
        get() = prefs.getLong(MMS_FILE_SIZE_LIMIT, FILE_SIZE_600_KB)
        set(mmsFileSizeLimit) = prefs.edit().putLong(MMS_FILE_SIZE_LIMIT, mmsFileSizeLimit).apply()

    var pinnedConversations: Set<String>
        get() = prefs.getStringSet(PINNED_CONVERSATIONS, HashSet<String>())!!
        set(pinnedConversations) = prefs.edit().putStringSet(PINNED_CONVERSATIONS, pinnedConversations).apply()

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

    var lastExportPath: String
        get() = prefs.getString(LAST_EXPORT_PATH, "")!!
        set(lastExportPath) = prefs.edit().putString(LAST_EXPORT_PATH, lastExportPath).apply()

    var exportSms: Boolean
        get() = prefs.getBoolean(EXPORT_SMS, true)
        set(exportSms) = prefs.edit().putBoolean(EXPORT_SMS, exportSms).apply()

    var exportMms: Boolean
        get() = prefs.getBoolean(EXPORT_MMS, true)
        set(exportMms) = prefs.edit().putBoolean(EXPORT_MMS, exportMms).apply()

    var importSms: Boolean
        get() = prefs.getBoolean(IMPORT_SMS, true)
        set(importSms) = prefs.edit().putBoolean(IMPORT_SMS, importSms).apply()

    var importMms: Boolean
        get() = prefs.getBoolean(IMPORT_MMS, true)
        set(importMms) = prefs.edit().putBoolean(IMPORT_MMS, importMms).apply()

    var wasDbCleared: Boolean
        get() = prefs.getBoolean(WAS_DB_CLEARED, false)
        set(wasDbCleared) = prefs.edit().putBoolean(WAS_DB_CLEARED, wasDbCleared).apply()

    var bubbleStyle: Int
        get() = prefs.getInt(BUBBLE_STYLE, 1)
        set(bubbleStyle) = prefs.edit().putInt(BUBBLE_STYLE, bubbleStyle).apply()

    var bubbleInvertColor: Boolean
        get() = prefs.getBoolean(BUBBLE_INVERT_COLOR, false)
        set(bubbleInvertColor) = prefs.edit().putBoolean(BUBBLE_INVERT_COLOR, bubbleInvertColor).apply()

    var bubbleInContactColor: Boolean
        get() = prefs.getBoolean(BUBBLE_IN_CONTACT_COLOR, false)
        set(bubbleInContactColor) = prefs.edit().putBoolean(BUBBLE_IN_CONTACT_COLOR, bubbleInContactColor).apply()

    var showContactThumbnails: Boolean
        get() = prefs.getBoolean(SHOW_CONTACT_THUMBNAILS, true)
        set(showContactThumbnails) = prefs.edit().putBoolean(SHOW_CONTACT_THUMBNAILS, showContactThumbnails).apply()

    var actionOnMessageClickSetting: Int
        get() = prefs.getInt(ACTION_ON_MESSAGE_CLICK, ACTION_COPY_CODE)
        set(actionOnMessageClickSetting) = prefs.edit().putInt(ACTION_ON_MESSAGE_CLICK, actionOnMessageClickSetting).apply()

    var threadTopStyle: Int
        get() = prefs.getInt(THREAD_TOP_STYLE, THREAD_TOP_LARGE)
        set(threadTopStyle) = prefs.edit().putInt(THREAD_TOP_STYLE, threadTopStyle).apply()
}

package com.goodwy.smsmessenger.helpers

import com.goodwy.commons.extensions.checkWhatsNew
import com.goodwy.commons.models.Release
import com.goodwy.smsmessenger.BuildConfig
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.models.Events
import org.greenrobot.eventbus.EventBus
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import kotlin.math.abs
import kotlin.random.Random

const val THREAD_ID = "thread_id"
const val THREAD_TITLE = "thread_title"
const val THREAD_TEXT = "thread_text"
const val THREAD_NUMBER = "thread_number"
const val THREAD_URI = "thread_uri"
const val THREAD_ATTACHMENT_URI = "thread_attachment_uri"
const val THREAD_ATTACHMENT_URIS = "thread_attachment_uris"
const val SEARCHED_MESSAGE_ID = "searched_message_id"
const val USE_SIM_ID_PREFIX = "use_sim_id_"
const val NOTIFICATION_CHANNEL = "right_sms_messenger"
const val SHOW_CHARACTER_COUNTER = "show_character_counter"
const val USE_SIMPLE_CHARACTERS = "use_simple_characters"
const val SEND_ON_ENTER = "send_on_enter"
const val LOCK_SCREEN_VISIBILITY = "lock_screen_visibility"
const val ENABLE_DELIVERY_REPORTS = "enable_delivery_reports"
const val SEND_LONG_MESSAGE_MMS = "send_long_message_mms"
const val SEND_GROUP_MESSAGE_MMS = "send_group_message_mms"
const val MMS_FILE_SIZE_LIMIT = "mms_file_size_limit"
const val PINNED_CONVERSATIONS = "pinned_conversations"
const val BLOCKED_KEYWORDS = "blocked_keywords"
const val LAST_BLOCKED_KEYWORD_EXPORT_PATH = "last_blocked_keyword_export_path"
const val EXPORT_SMS = "export_sms"
const val EXPORT_MMS = "export_mms"
const val JSON_FILE_EXTENSION = ".json"
const val JSON_MIME_TYPE = "application/json"
const val XML_MIME_TYPE = "text/xml"
const val TXT_MIME_TYPE = "text/plain"
const val IMPORT_SMS = "import_sms"
const val IMPORT_MMS = "import_mms"
const val WAS_DB_CLEARED = "was_db_cleared_4"
const val EXTRA_VCARD_URI = "vcard"
const val SCHEDULED_MESSAGE_ID = "scheduled_message_id"
const val SOFT_KEYBOARD_HEIGHT = "soft_keyboard_height"
const val IS_MMS = "is_mms"
const val MESSAGE_ID = "message_id"
const val USE_RECYCLE_BIN = "use_recycle_bin"
const val LAST_RECYCLE_BIN_CHECK = "last_recycle_bin_check"
const val IS_RECYCLE_BIN = "is_recycle_bin"
const val IS_ARCHIVE_AVAILABLE = "is_archive_available"
const val CUSTOM_NOTIFICATIONS = "custom_notifications"
const val IS_LAUNCHED_FROM_SHORTCUT = "is_launched_from_shortcut"
const val KEEP_CONVERSATIONS_ARCHIVED = "keep_conversations_archived"
//Goodwy
const val SIM_TO_REPLY = "sim_to_reply"
const val SHOW_SIM_SELECTION_DIALOG = "show_sim_selection_dialog"
const val COPY_NUMBER_AND_DELETE_PREF = "copy_number_and_delete_pref"
const val FONT_SIZE_MESSAGE = "font_size_message"
const val SOUND_ON_OUT_GOING_MESSAGE = "sound_on_out_going_messages"
const val NOTIFY_TURN_ON_SCREEN = "notify_turns_on_screen"
const val INIT_CALL_BLOCKING_SETUP = "init_call_blocking_setup"

private const val PATH = "com.goodwy.smsmessenger.action."
const val MARK_AS_READ = PATH + "mark_as_read"
const val REPLY = PATH + "reply"

// view types for the thread list view
const val THREAD_DATE_TIME = 1
const val THREAD_RECEIVED_MESSAGE = 2
const val THREAD_SENT_MESSAGE = 3
const val THREAD_SENT_MESSAGE_ERROR = 4
const val THREAD_SENT_MESSAGE_SENT = 5
const val THREAD_SENT_MESSAGE_SENDING = 6
const val THREAD_TYPE_BITS = 3
const val THREAD_KEY_BITS = Long.SIZE_BITS - THREAD_TYPE_BITS
const val THREAD_TYPE_SHIFT = THREAD_KEY_BITS
const val THREAD_KEY_MASK = (1L shl THREAD_KEY_BITS) - 1

// view types for attachment list
const val ATTACHMENT_DOCUMENT = 7
const val ATTACHMENT_MEDIA = 8
const val ATTACHMENT_VCARD = 9

// lock screen visibility constants
const val LOCK_SCREEN_SENDER_MESSAGE = 1
const val LOCK_SCREEN_SENDER = 2
const val LOCK_SCREEN_NOTHING = 3

const val FILE_SIZE_NONE = -1L
const val FILE_SIZE_100_KB = 102_400L
const val FILE_SIZE_200_KB = 204_800L
const val FILE_SIZE_300_KB = 307_200L
const val FILE_SIZE_600_KB = 614_400L
const val FILE_SIZE_1_MB = 1_048_576L
const val FILE_SIZE_2_MB = 2_097_152L

const val MESSAGES_LIMIT = 50
const val MAX_MESSAGE_LENGTH = 5000

// intent launch request codes
const val PICK_PHOTO_INTENT = 42
const val PICK_VIDEO_INTENT = 49
const val PICK_SAVE_FILE_INTENT = 43
const val CAPTURE_PHOTO_INTENT = 44
const val CAPTURE_VIDEO_INTENT = 45
const val CAPTURE_AUDIO_INTENT = 46
const val PICK_DOCUMENT_INTENT = 47
const val PICK_CONTACT_INTENT = 48
const val PICK_SAVE_DIR_INTENT = 50

const val BLOCKED_KEYWORDS_EXPORT_DELIMITER = ","
const val BLOCKED_KEYWORDS_EXPORT_EXTENSION = ".txt"

fun refreshMessages() {
    EventBus.getDefault().post(Events.RefreshMessages())
}

fun refreshConversations() {
    EventBus.getDefault().post(Events.RefreshConversations())
}

/** Not to be used with real messages persisted in the telephony db. This is for internal use only (e.g. scheduled messages, notification ids etc). */
fun generateRandomId(length: Int = 9): Long {
    val millis = DateTime.now(DateTimeZone.UTC).millis
    val random = abs(Random(millis).nextLong())
    return random.toString().takeLast(length).toLong()
}

fun generateStableId(type: Int, key: Long): Long {
    require(type in 0 until (1 shl THREAD_TYPE_BITS))
    return (type.toLong() shl THREAD_TYPE_SHIFT) or (key and THREAD_KEY_MASK)
}

//Goodwy
const val BUBBLE_STYLE = "bubble_style"
const val BUBBLE_STYLE_ORIGINAL = 0
const val BUBBLE_STYLE_IOS_NEW = 1
const val BUBBLE_STYLE_IOS = 2
const val BUBBLE_STYLE_ROUNDED = 3
const val BUBBLE_INVERT_COLOR = "bubble_invert_color"
const val BUBBLE_IN_CONTACT_COLOR = "bubble_in_contact_color"
const val UNREAD_AT_TOP = "unread_at_top"

const val COPY_NUMBER = PATH + "copy_number"
const val COPY_NUMBER_AND_DELETE = PATH + "copy_number_and_delete"

// action on message click constants
const val ACTION_ON_MESSAGE_CLICK = "action_on_message_click"
const val ACTION_COPY_CODE = 1
const val ACTION_COPY_MESSAGE = 2
const val ACTION_NOTHING = 3
const val ACTION_SELECT_TEXT = 4

// thread title style
const val THREAD_TOP_STYLE = "thread_top_style"
const val THREAD_TOP_COMPACT = 1
const val THREAD_TOP_LARGE = 2

// unread indicator position
const val UNREAD_INDICATOR_POSITION = "unread_indicator_position"
const val UNREAD_INDICATOR_START = 1
const val UNREAD_INDICATOR_END = 2

// swiped left action
const val SWIPE_RIGHT_ACTION = "swipe_right_action"
const val SWIPE_LEFT_ACTION = "swipe_left_action"
const val SWIPE_ACTION_NONE = 0
const val SWIPE_ACTION_MARK_READ = 1
const val SWIPE_ACTION_DELETE = 2
const val SWIPE_ACTION_ARCHIVE = 3
const val SWIPE_ACTION_BLOCK = 4 //!! isNougatPlus()
const val SWIPE_ACTION_CALL = 5
const val SWIPE_ACTION_MESSAGE = 6
const val SWIPE_ACTION_EDIT = 7
const val SWIPE_ACTION_SHARE = 8
const val SWIPE_ACTION_OPEN = 9
const val SWIPE_ACTION_RESTORE = 10
const val SWIPE_VIBRATION = "swipe_vibration"
const val SWIPE_RIPPLE = "swipe_ripple"

fun whatsNewList(): ArrayList<Release> {
    return arrayListOf<Release>().apply {
        add(Release(420, R.string.release_420))
        add(Release(421, R.string.release_421))
        add(Release(500, R.string.release_500))
        add(Release(510, R.string.release_510))
        add(Release(511, R.string.release_511))
        add(Release(513, R.string.release_513))
        add(Release(515, R.string.release_515))
        add(Release(520, R.string.release_520))
        add(Release(521, R.string.release_521))
        add(Release(610, R.string.release_610))
        add(Release(620, R.string.release_620))
        add(Release(630, R.string.release_630))
        add(Release(631, R.string.release_631))
        add(Release(632, R.string.release_632))
        add(Release(633, R.string.release_633))
        add(Release(700, R.string.release_700))
        add(Release(701, R.string.release_701))
        add(Release(800, R.string.release_800))
    }
}

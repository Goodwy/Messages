package com.goodwy.smsmessenger.extensions

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.PhoneLookup
import android.provider.OpenableColumns
import android.provider.Telephony.Mms
import android.provider.Telephony.MmsSms
import android.provider.Telephony.Sms
import android.provider.Telephony.Threads
import android.provider.Telephony.ThreadsColumns
import android.telephony.SubscriptionManager
import android.text.TextUtils
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.mms.pdu_alt.PduHeaders
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.PhoneNumber
import com.goodwy.commons.models.SimpleContact
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.databases.MessagesDatabase
import com.goodwy.smsmessenger.helpers.Config
import com.goodwy.smsmessenger.helpers.FILE_SIZE_NONE
import com.goodwy.smsmessenger.helpers.MAX_MESSAGE_LENGTH
import com.goodwy.smsmessenger.helpers.MESSAGES_LIMIT
import com.goodwy.smsmessenger.helpers.NotificationHelper
import com.goodwy.smsmessenger.helpers.ShortcutHelper
import com.goodwy.smsmessenger.helpers.generateRandomId
import com.goodwy.smsmessenger.helpers.AttachmentUtils.parseAttachmentNames
import com.goodwy.smsmessenger.interfaces.AttachmentsDao
import com.goodwy.smsmessenger.interfaces.ConversationsDao
import com.goodwy.smsmessenger.interfaces.DraftsDao
import com.goodwy.smsmessenger.interfaces.MessageAttachmentsDao
import com.goodwy.smsmessenger.interfaces.MessagesDao
import com.goodwy.smsmessenger.messaging.MessagingUtils
import com.goodwy.smsmessenger.messaging.MessagingUtils.Companion.ADDRESS_SEPARATOR
import com.goodwy.smsmessenger.messaging.SmsSender
import com.goodwy.smsmessenger.messaging.isShortCodeWithLetters
import com.goodwy.smsmessenger.models.Attachment
import com.goodwy.smsmessenger.models.Conversation
import com.goodwy.smsmessenger.models.Draft
import com.goodwy.smsmessenger.models.Message
import com.goodwy.smsmessenger.models.MessageAttachment
import com.goodwy.smsmessenger.models.NamePhoto
import com.goodwy.smsmessenger.models.RecycleBinMessage
import org.xmlpull.v1.XmlPullParserException
import java.io.FileNotFoundException

val Context.config: Config
    get() = Config.newInstance(applicationContext)

fun Context.getMessagesDB() = MessagesDatabase.getInstance(this)

val Context.conversationsDB: ConversationsDao
    get() = getMessagesDB().ConversationsDao()

val Context.attachmentsDB: AttachmentsDao
    get() = getMessagesDB().AttachmentsDao()

val Context.messageAttachmentsDB: MessageAttachmentsDao
    get() = getMessagesDB().MessageAttachmentsDao()

val Context.messagesDB: MessagesDao
    get() = getMessagesDB().MessagesDao()

val Context.draftsDB: DraftsDao
    get() = getMessagesDB().DraftsDao()

val Context.notificationHelper
    get() = NotificationHelper(this)

val Context.messagingUtils
    get() = MessagingUtils(this)

val Context.smsSender
    get() = SmsSender.getInstance(applicationContext as Application)

val Context.shortcutHelper get() = ShortcutHelper(this)

fun Context.getMessages(
    threadId: Long,
    getImageResolutions: Boolean,
    dateFrom: Int = -1,
    includeScheduledMessages: Boolean = true,
    limit: Int = MESSAGES_LIMIT,
): ArrayList<Message> {
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms._ID,
        Sms.BODY,
        Sms.TYPE,
        Sms.ADDRESS,
        Sms.DATE,
        Sms.READ,
        Sms.THREAD_ID,
        Sms.SUBSCRIPTION_ID,
        Sms.STATUS
    )

    val rangeQuery = if (dateFrom == -1) "" else "AND ${Sms.DATE} < ${dateFrom.toLong() * 1000}"
    val selection = "${Sms.THREAD_ID} = ? $rangeQuery"
    val selectionArgs = arrayOf(threadId.toString())
    val sortOrder = "${Sms.DATE} DESC LIMIT $limit"

    val blockStatus = HashMap<String, Boolean>()
    val blockedNumbers = getBlockedNumbers()
    var messages = ArrayList<Message>()

    val privateCursor = getMyContactsCursor(false, true)
    var contacts = ArrayList<SimpleContact>()
    ensureBackgroundThread {
        SimpleContactsHelper(this).getAvailableContacts(false) {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            contacts = ArrayList(it + privateContacts)
        }
    }
    queryCursor(uri, projection, selection, selectionArgs, sortOrder, showErrors = true) { cursor ->
        val senderNumber = cursor.getStringValue(Sms.ADDRESS) ?: return@queryCursor

        val isNumberBlocked = if (blockStatus.containsKey(senderNumber)) {
            blockStatus[senderNumber]!!
        } else {
            val isBlocked = isNumberBlocked(senderNumber, blockedNumbers)
            blockStatus[senderNumber] = isBlocked
            isBlocked
        }

        if (isNumberBlocked && !config.showBlockedNumbers) {
            return@queryCursor
        }

        val id = cursor.getLongValue(Sms._ID)
        val body = cursor.getStringValue(Sms.BODY)
        val type = cursor.getIntValue(Sms.TYPE)
        val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
        val senderName = namePhoto.name
        val photoUri = namePhoto.photoUri ?: ""
        val date = (cursor.getLongValue(Sms.DATE) / 1000).toInt()
        val read = cursor.getIntValue(Sms.READ) == 1
        val thread = cursor.getLongValue(Sms.THREAD_ID)
        val subscriptionId = cursor.getIntValueOr(
            key = Sms.SUBSCRIPTION_ID,
            defaultValue = SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )

        val status = cursor.getIntValue(Sms.STATUS)
        val participants = senderNumber.split(ADDRESS_SEPARATOR).map { number ->
            if (contacts.isNotEmpty()) {
                val contact = contacts.firstOrNull { it.doesHavePhoneNumber(number) }
                if (contact != null) {
                    val phoneNumber = PhoneNumber(number, 0, "", number)
                    SimpleContact(
                        rawId = 0,
                        contactId = 0,
                        name = contact.name,
                        photoUri = contact.photoUri,
                        phoneNumbers = arrayListOf(phoneNumber),
                        birthdays = ArrayList(),
                        anniversaries = ArrayList(),
                        company = contact.company,
                        jobPosition = contact.jobPosition
                    )
                }
                else {
                    val phoneNumber = PhoneNumber(number, 0, "", number)
                    val participantPhoto = getNameAndPhotoFromPhoneNumber(number)
                    SimpleContact(
                        rawId = 0,
                        contactId = 0,
                        name = participantPhoto.name,
                        photoUri = photoUri,
                        phoneNumbers = arrayListOf(phoneNumber),
                        birthdays = ArrayList(),
                        anniversaries = ArrayList()
                    )
                }
            } else {
                val phoneNumber = PhoneNumber(number, 0, "", number)
                val participantPhoto = getNameAndPhotoFromPhoneNumber(number)
                SimpleContact(
                    rawId = 0,
                    contactId = 0,
                    name = participantPhoto.name,
                    photoUri = photoUri,
                    phoneNumbers = arrayListOf(phoneNumber),
                    birthdays = ArrayList(),
                    anniversaries = ArrayList()
                )
            }
        }
        val isMMS = false
        val message =
            Message(
                id = id,
                body = body,
                type = type,
                status = status,
                participants = ArrayList(participants),
                date = date,
                read = read,
                threadId = thread,
                isMMS = isMMS,
                attachment = null,
                senderPhoneNumber = senderNumber,
                senderName = senderName,
                senderPhotoUri = photoUri,
                subscriptionId = subscriptionId
            )
        messages.add(message)
    }

    messages.addAll(getMMS(threadId, getImageResolutions, sortOrder, dateFrom))

    if (includeScheduledMessages) {
        try {
            val scheduledMessages = messagesDB.getScheduledThreadMessages(threadId)
            messages.addAll(scheduledMessages)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    messages = messages
        .filter { it.participants.isNotEmpty() }
        .filterNot { it.isScheduled && it.millis() < System.currentTimeMillis() }
        .sortedWith(compareBy<Message> { it.date }.thenBy { it.id })
        .takeLast(limit)
        .toMutableList() as ArrayList<Message>

    return messages
}

// as soon as a message contains multiple recipients it counts as an MMS instead of SMS
fun Context.getMMS(
    threadId: Long? = null,
    getImageResolutions: Boolean = false,
    sortOrder: String? = null,
    dateFrom: Int = -1,
): ArrayList<Message> {
    val uri = Mms.CONTENT_URI
    val projection = arrayOf(
        Mms._ID,
        Mms.DATE,
        Mms.READ,
        Mms.MESSAGE_BOX,
        Mms.THREAD_ID,
        Mms.SUBSCRIPTION_ID,
        Mms.STATUS
    )

    var selection: String? = null
    var selectionArgs: Array<String>? = null

    if (threadId == null && dateFrom != -1) {
        // Should not multiply 1000 here, because date in mms's database is different from sms's.
        selection = "${Sms.DATE} < ${dateFrom.toLong()}"
    } else if (threadId != null && dateFrom == -1) {
        selection = "${Sms.THREAD_ID} = ?"
        selectionArgs = arrayOf(threadId.toString())
    } else if (threadId != null) {
        selection = "${Sms.THREAD_ID} = ? AND ${Sms.DATE} < ${dateFrom.toLong()}"
        selectionArgs = arrayOf(threadId.toString())
    }

    val messages = ArrayList<Message>()
    val contactsMap = HashMap<Int, SimpleContact>()
    val threadParticipants = HashMap<Long, ArrayList<SimpleContact>>()
    queryCursor(uri, projection, selection, selectionArgs, sortOrder, showErrors = true) { cursor ->
        val mmsId = cursor.getLongValue(Mms._ID)
        val type = cursor.getIntValue(Mms.MESSAGE_BOX)
        val date = cursor.getLongValue(Mms.DATE).toInt()
        val read = cursor.getIntValue(Mms.READ) == 1
        val threadId = cursor.getLongValue(Mms.THREAD_ID)
        val subscriptionId = cursor.getIntValue(Mms.SUBSCRIPTION_ID)
        val status = cursor.getIntValue(Mms.STATUS)
        val participants = if (threadParticipants.containsKey(threadId)) {
            threadParticipants[threadId]!!
        } else {
            val parts = getThreadParticipants(threadId, contactsMap)
            threadParticipants[threadId] = parts
            parts
        }

        val isMMS = true
        var senderNumber = ""
        val attachment = getMmsAttachment(mmsId, getImageResolutions)
        val body = attachment.text
        var senderName = ""
        var senderPhotoUri = ""

        if (type != Mms.MESSAGE_BOX_SENT && type != Mms.MESSAGE_BOX_FAILED) {
            senderNumber = getMMSSender(mmsId)
            val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
            senderName = namePhoto.name
            senderPhotoUri = namePhoto.photoUri ?: ""
        }

        val message =
            Message(
                id = mmsId,
                body = body,
                type = type,
                status = status,
                participants = participants,
                date = date,
                read = read,
                threadId = threadId,
                isMMS = isMMS,
                attachment = attachment,
                senderPhoneNumber = senderNumber,
                senderName = senderName,
                senderPhotoUri = senderPhotoUri,
                subscriptionId = subscriptionId
            )
        messages.add(message)

        participants.forEach {
            contactsMap[it.rawId] = it
        }
    }

    return messages
}

fun Context.getMMSSender(msgId: Long): String {
    val uri = "${Mms.CONTENT_URI}/$msgId/addr".toUri()
    val projection = arrayOf(
        Mms.Addr.ADDRESS
    )

    val selection = "${Mms.Addr.TYPE} = ?"
    val selectionArgs = arrayOf(PduHeaders.FROM.toString())

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getStringValue(Mms.Addr.ADDRESS)
            }
        }
    } catch (_: Exception) {
    }
    return ""
}

fun Context.getConversations(
    threadId: Long? = null,
    privateContacts: ArrayList<SimpleContact> = ArrayList(),
): ArrayList<Conversation> {
    val archiveAvailable = config.isArchiveAvailable
    val useRecycleBin = config.useRecycleBin

    val uri = "${Threads.CONTENT_URI}?simple=true".toUri()
    val projection = mutableListOf(
        Threads._ID,
        Threads.SNIPPET,
        Threads.DATE,
        Threads.READ,
        Threads.RECIPIENT_IDS,
    )

    if (archiveAvailable) {
        projection += Threads.ARCHIVED
    }

    var selection = "${Threads.MESSAGE_COUNT} > 0"
    var selectionArgs = arrayOf<String>()
    if (threadId != null) {
        selection += " AND ${Threads._ID} = ?"
        selectionArgs += threadId.toString()
    }

    val sortOrder = "${Threads.DATE} DESC"

    val conversations = ArrayList<Conversation>()
    val simpleContactHelper = SimpleContactsHelper(this)
    val blockedNumbers = getBlockedNumbers()
    try {
        SimpleContactsHelper(this).getAvailableContacts(false) { contacts ->
            queryCursorUnsafe(
                uri,
                projection.toTypedArray(),
                selection,
                selectionArgs,
                sortOrder
            ) { cursor ->
                val id = cursor.getLongValue(Threads._ID)
//            var snippet = cursor.getStringValue(Threads.SNIPPET) ?: ""
//            if (snippet.isEmpty()) {
//                snippet = getThreadSnippet(id)
//            }
                val snippet = getThreadSnippet(id)

                var date = cursor.getLongValue(Threads.DATE)
                if (date.toString().length > 10) {
                    date /= 1000
                }

                // drafts are stored locally they take priority over the original date
                val draft = draftsDB.getDraftById(id)
                if (draft != null) {
                    date = draft.date / 1000
                }

                val rawIds = cursor.getStringValue(Threads.RECIPIENT_IDS)
                val recipientIds =
                    rawIds.split(" ").filter { it.areDigitsOnly() }.map { it.toInt() }.toMutableList()
                val phoneNumbers = getThreadPhoneNumbers(recipientIds)
                val isBlocked = phoneNumbers.any { isNumberBlocked(it, blockedNumbers) }
                if (phoneNumbers.isEmpty() || (isBlocked && !config.showBlockedNumbers)) {
                    return@queryCursorUnsafe
                }

                val names = getThreadContactNames(phoneNumbers, privateContacts)
                val title = TextUtils.join(", ", names.toTypedArray())
                var photoUri =
                    if (phoneNumbers.size == 1) simpleContactHelper.getPhotoUriFromPhoneNumber(
                        phoneNumbers.first()
                    ) else ""
                val isGroupConversation = phoneNumbers.size > 1
                val read = cursor.getIntValue(Threads.READ) == 1
                val archived =
                    if (archiveAvailable) cursor.getIntValue(Threads.ARCHIVED) == 1 else false
                val deleted =
                    if (useRecycleBin) messagesDB.getNonRecycledThreadMessages(id).isEmpty()
                        && messagesDB.getThreadMessagesFromRecycleBin(id).isNotEmpty()
                    else false
                val unreadCount = messagesDB.getThreadUnreadMessages(id)

                var contact =
                    if (phoneNumbers.size == 1) contacts.firstOrNull { it.doesHavePhoneNumber(phoneNumbers.first()) }
                    else null
                if (contact == null && phoneNumbers.size == 1) {
                    contact = privateContacts.firstOrNull { it.doesHavePhoneNumber(phoneNumbers.first()) }

                    if (contact == null) {
                        contact = contacts.firstOrNull { it.phoneNumbers.map { it.value }.any { it == phoneNumbers.first() } }
                    }
                }
                if (photoUri == "" && contact != null) photoUri = contact.photoUri

                val isABusinessContact =
                    if (phoneNumbers.size == 1) contact?.isABusinessContact() ?: isShortCodeWithLetters(phoneNumbers.first())
                    else false

                val conversation = Conversation(
                    threadId = id,
                    snippet = snippet,
                    date = date.toInt(),
                    read = read,
                    title = title,
                    photoUri = photoUri,
                    isGroupConversation = isGroupConversation,
                    phoneNumber = phoneNumbers.first(),
                    isArchived = archived,
                    isDeleted = deleted,
                    unreadCount = unreadCount,
                    isCompany = isABusinessContact,
                    isBlocked = isBlocked
                )
                conversations.add(conversation)
            }
        }
    } catch (sqliteException: SQLiteException) {
        if (
            sqliteException.message?.contains("no such column: archived") == true
            && archiveAvailable
        ) {
            config.isArchiveAvailable = false
            return getConversations(threadId, privateContacts)
        } else {
            showErrorToast(sqliteException)
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    conversations.sortByDescending { it.date }
    return conversations
}

private fun Context.queryCursorUnsafe(
    uri: Uri,
    projection: Array<String>,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    sortOrder: String? = null,
    callback: (cursor: Cursor) -> Unit,
) {
    val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
    cursor?.use {
        if (cursor.moveToFirst()) {
            do {
                callback(cursor)
            } while (cursor.moveToNext())
        }
    }
}

fun Context.getConversationIds(): List<Long> {
    val uri = "${Threads.CONTENT_URI}?simple=true".toUri()
    val projection = arrayOf(Threads._ID)
    val selection = "${Threads.MESSAGE_COUNT} > 0"
    val sortOrder = "${Threads.DATE} ASC"
    val conversationIds = mutableListOf<Long>()
    queryCursor(uri, projection, selection, null, sortOrder, true) { cursor ->
        val id = cursor.getLongValue(Threads._ID)
        conversationIds.add(id)
    }
    return conversationIds
}

// based on https://stackoverflow.com/a/6446831/1967672
@SuppressLint("NewApi")
fun Context.getMmsAttachment(id: Long, getImageResolutions: Boolean): MessageAttachment {
    val uri = if (isQPlus()) {
        Mms.Part.CONTENT_URI
    } else {
        "content://mms/part".toUri()
    }

    val projection = arrayOf(
        Mms._ID,
        Mms.Part.CONTENT_TYPE,
        Mms.Part.TEXT
    )
    val selection = "${Mms.Part.MSG_ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    val messageAttachment = MessageAttachment(id, "", arrayListOf())

    var attachmentNames: List<String>? = null
    var attachmentCount = 0
    queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
        val partId = cursor.getLongValue(Mms._ID)
        val mimetype = cursor.getStringValue(Mms.Part.CONTENT_TYPE)
        if (mimetype == "text/plain") {
            messageAttachment.text = cursor
                .getStringValue(Mms.Part.TEXT)
                ?.take(MAX_MESSAGE_LENGTH)
                .orEmpty()
        } else if (mimetype.startsWith("image/") || mimetype.startsWith("video/")) {
            val fileUri = Uri.withAppendedPath(uri, partId.toString())
            var width = 0
            var height = 0

            if (getImageResolutions) {
                try {
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeStream(
                        contentResolver.openInputStream(fileUri),
                        null,
                        options
                    )
                    width = options.outWidth
                    height = options.outHeight
                } catch (_: Exception) {
                }
            }

            messageAttachment.attachments.add(
                Attachment(
                    id = partId,
                    messageId = id,
                    uriString = fileUri.toString(),
                    mimetype = mimetype,
                    width = width,
                    height = height,
                    filename = ""
                )
            )
        } else if (mimetype != "application/smil") {
            val attachmentName = attachmentNames?.getOrNull(attachmentCount) ?: ""
            val attachment = Attachment(
                id = partId,
                messageId = id,
                uriString = Uri.withAppendedPath(uri, partId.toString()).toString(),
                mimetype = mimetype,
                width = 0,
                height = 0,
                filename = attachmentName
            )
            messageAttachment.attachments.add(attachment)
            attachmentCount++
        } else {
            val text = cursor.getStringValue(Mms.Part.TEXT)
            attachmentNames = try {
                parseAttachmentNames(text)
            } catch (e: XmlPullParserException) {
                e.printStackTrace()
                null
            }
        }
    }

    return messageAttachment
}

fun Context.getLatestMMS(): Message? {
    val sortOrder = "${Mms.DATE} DESC LIMIT 1"
    return getMMS(sortOrder = sortOrder).firstOrNull()
}

fun Context.getThreadSnippet(threadId: Long): String {
    val sortOrder = "${Mms.DATE} DESC LIMIT 1"
    val latestMms = getMMS(threadId, false, sortOrder).firstOrNull()
    var snippet = latestMms?.body ?: ""

    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.BODY
    )

    val selection = "${Sms.THREAD_ID} = ? AND ${Sms.DATE} > ?"
    val selectionArgs = arrayOf(
        threadId.toString(),
        latestMms?.date?.toString() ?: "0"
    )
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor?.use {
            if (cursor.moveToFirst()) {
                snippet = cursor.getStringValue(Sms.BODY)
            }
        }
    } catch (_: Exception) {
    }
    return snippet
}

fun Context.getMessageRecipientAddress(messageId: Long): String {
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.ADDRESS
    )

    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(messageId.toString())

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getStringValue(Sms.ADDRESS)
            }
        }
    } catch (_: Exception) {
    }

    return ""
}

fun Context.getThreadParticipants(
    threadId: Long,
    contactsMap: HashMap<Int, SimpleContact>?,
): ArrayList<SimpleContact> {
    val uri = "${MmsSms.CONTENT_CONVERSATIONS_URI}?simple=true".toUri()
    val projection = arrayOf(
        ThreadsColumns.RECIPIENT_IDS
    )
    val selection = "${Mms._ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    val participants = ArrayList<SimpleContact>()
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                val address = cursor.getStringValue(ThreadsColumns.RECIPIENT_IDS)
                address.split(" ").filter { it.areDigitsOnly() }.forEach {
                    val addressId = it.toInt()
                    if (contactsMap?.containsKey(addressId) == true) {
                        participants.add(contactsMap[addressId]!!)
                        return@forEach
                    }

                    val number = getPhoneNumberFromAddressId(addressId)
                    val namePhoto = getNameAndPhotoFromPhoneNumber(number)
                    val name = namePhoto.name
                    val photoUri = namePhoto.photoUri ?: ""
                    val company = namePhoto.company
                    val jobPosition = namePhoto.jobPosition
                    val phoneNumber = PhoneNumber(number, 0, "", number)
                    val contact = SimpleContact(
                        rawId = addressId,
                        contactId = if (namePhoto.isContact) addressId else 0,
                        name = name,
                        photoUri = photoUri,
                        phoneNumbers = arrayListOf(phoneNumber),
                        birthdays = ArrayList(),
                        anniversaries = ArrayList(),
                        company = company,
                        jobPosition = jobPosition,
                    )
                    participants.add(contact)
                }
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
    return participants
}

fun Context.getThreadPhoneNumbers(recipientIds: List<Int>): ArrayList<String> {
    val numbers = ArrayList<String>()
    recipientIds.forEach {
        numbers.add(getPhoneNumberFromAddressId(it))
    }
    return numbers
}

fun Context.getThreadContactNames(
    phoneNumbers: List<String>,
    privateContacts: ArrayList<SimpleContact>,
): ArrayList<String> {
    val names = ArrayList<String>()
    phoneNumbers.forEach { number ->
        val name = SimpleContactsHelper(this).getNameFromPhoneNumber(number)
        if (name != number) {
            names.add(name)
        } else {
            val privateContact = privateContacts.firstOrNull { it.doesHavePhoneNumber(number) }
            if (privateContact == null) {
                names.add(name)
            } else {
                names.add(privateContact.name)
            }
        }
    }
    return names
}

fun Context.getPhoneNumberFromAddressId(canonicalAddressId: Int): String {
    val uri = Uri.withAppendedPath(MmsSms.CONTENT_URI, "canonical-addresses")
    val projection = arrayOf(
        Mms.Addr.ADDRESS
    )

    val selection = "${Mms._ID} = ?"
    val selectionArgs = arrayOf(canonicalAddressId.toString())
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getStringValue(Mms.Addr.ADDRESS)
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
    return ""
}

fun Context.getSuggestedContacts(
    privateContacts: ArrayList<SimpleContact>,
): ArrayList<SimpleContact> {
    val contacts = ArrayList<SimpleContact>()
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.ADDRESS
    )

    val sortOrder = "${Sms.DATE} DESC LIMIT 50"
    val blockedNumbers = getBlockedNumbers()

    queryCursor(uri, projection, null, null, sortOrder, showErrors = true) { cursor ->
        val senderNumber = cursor.getStringValue(Sms.ADDRESS) ?: return@queryCursor
        val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
        val senderName = namePhoto.name
        val photoUri = namePhoto.photoUri ?: ""
        var company = ""
        var jobPosition = ""
        if (isNumberBlocked(senderNumber, blockedNumbers) && !config.showBlockedNumbers) {
            return@queryCursor
        } else if (namePhoto.name == senderNumber) {
            if (privateContacts.isNotEmpty()) {
                val privateContact = privateContacts.firstOrNull {
                    it.phoneNumbers.first().normalizedNumber == senderNumber
                }
                if (privateContact != null) {
//                    senderName = privateContact.name
//                    photoUri = privateContact.photoUri
                    company = privateContact.company
                    jobPosition = privateContact.jobPosition
                } else {
                    return@queryCursor
                }
            } else {
                return@queryCursor
            }
        } else {
            if (privateContacts.isNotEmpty()) {
                val privateContact =
                    privateContacts.firstOrNull { it.phoneNumbers.first().normalizedNumber == senderNumber }
                if (privateContact != null) {
//                    senderName = privateContact.name
//                    photoUri = privateContact.photoUri
                    company = privateContact.company
                    jobPosition = privateContact.jobPosition
                }
            }
        }

        val phoneNumber = PhoneNumber(senderNumber, 0, "", senderNumber)
        val contact = SimpleContact(
            rawId = 0,
            contactId = 0,
            name = senderName,
            photoUri = photoUri,
            phoneNumbers = arrayListOf(phoneNumber),
            birthdays = ArrayList(),
            anniversaries = ArrayList(),
            company = company,
            jobPosition = jobPosition
        )
        if (!contacts.map { it.phoneNumbers.first().normalizedNumber.trimToComparableNumber() }
                .contains(senderNumber.trimToComparableNumber())) {
            contacts.add(contact)
        }
    }

    return contacts
}

fun Context.getNameAndPhotoFromPhoneNumber(number: String): NamePhoto {
    if (!hasPermission(PERMISSION_READ_CONTACTS)) {
        return NamePhoto(number, null)
    }

    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    val projection = arrayOf(
        PhoneLookup.DISPLAY_NAME,
        PhoneLookup.PHOTO_URI,
        PhoneLookup._ID
    )

    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor.use {
            if (cursor?.moveToFirst() == true) {
                val name = cursor.getStringValue(PhoneLookup.DISPLAY_NAME)
                val photoUri = cursor.getStringValue(PhoneLookup.PHOTO_URI)
                val contactId = cursor.getLongValue(PhoneLookup._ID)
                val (company, title) = getCompanyAndTitleByContactId(contactId)
                return NamePhoto(name, photoUri, company ?: "", title ?: "", isContact = true)
            }
        }
    } catch (_: Exception) {
    }

    return NamePhoto(number, null)
}

private fun Context.getCompanyAndTitleByContactId(contactId: Long): Pair<String?, String?> {
    val organizationUri = ContactsContract.Data.CONTENT_URI
    val organizationProjection = arrayOf(
        ContactsContract.CommonDataKinds.Organization.COMPANY,
        ContactsContract.CommonDataKinds.Organization.TITLE
    )

    val organizationSelection = """
        ${ContactsContract.Data.CONTACT_ID} = ? AND 
        ${ContactsContract.Data.MIMETYPE} = ?
    """.trimIndent()

    val organizationSelectionArgs = arrayOf(
        contactId.toString(),
        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
    )

    try {
        val cursor = contentResolver.query(
            organizationUri,
            organizationProjection,
            organizationSelection,
            organizationSelectionArgs,
            null
        )
        cursor.use {
            if (cursor?.moveToFirst() == true) {
                val company = cursor.getStringValue(ContactsContract.CommonDataKinds.Organization.COMPANY)
                val title = cursor.getStringValue(ContactsContract.CommonDataKinds.Organization.TITLE)
                return Pair(
                    if (company.isNullOrEmpty()) null else company,
                    if (title.isNullOrEmpty()) null else title
                )
            }
        }
    } catch (_: Exception) {
    }

    return Pair(null, null)
}

fun Context.insertNewSMS(
    address: String,
    subject: String,
    body: String,
    date: Long,
    read: Int,
    threadId: Long,
    type: Int,
    subscriptionId: Int,
): Long {
    val uri = Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.ADDRESS, address)
        put(Sms.SUBJECT, subject)
        put(Sms.BODY, body)
        put(Sms.DATE, date)
        put(Sms.READ, read)
        put(Sms.THREAD_ID, threadId)
        put(Sms.TYPE, type)
        put(Sms.SUBSCRIPTION_ID, subscriptionId)
    }

    return try {
        val newUri = contentResolver.insert(uri, contentValues)
        newUri?.lastPathSegment?.toLong() ?: 0L
    } catch (_: Exception) {
        0L
    }
}

fun Context.removeAllArchivedConversations(callback: (() -> Unit)? = null) {
    ensureBackgroundThread {
        try {
            for (conversation in conversationsDB.getAllArchived()) {
                deleteConversation(conversation.threadId)
            }
            toast(R.string.archive_emptied_successfully)
            callback?.invoke()
        } catch (_: Exception) {
            toast(com.goodwy.commons.R.string.unknown_error_occurred)
        }
    }
}

fun Context.deleteConversation(threadId: Long) {
    var uri = Sms.CONTENT_URI
    val selection = "${Sms.THREAD_ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    try {
        contentResolver.delete(uri, selection, selectionArgs)
    } catch (e: Exception) {
        showErrorToast(e)
    }

    uri = Mms.CONTENT_URI
    try {
        contentResolver.delete(uri, selection, selectionArgs)
    } catch (e: Exception) {
        e.printStackTrace()
    }

    conversationsDB.deleteThreadId(threadId)
    messagesDB.deleteThreadMessages(threadId)

    if (config.customNotifications.contains(threadId.toString())) {
        config.removeCustomNotificationsByThreadId(threadId)
        notificationManager.deleteNotificationChannel(threadId.hashCode().toString())
    }
    if (shortcutHelper.getShortcut(threadId) != null) {
        shortcutHelper.removeShortcutForThread(threadId)
    }
}

fun Context.checkAndDeleteOldRecycleBinMessages(callback: (() -> Unit)? = null) {
    if (
        config.useRecycleBin
        && config.lastRecycleBinCheck < System.currentTimeMillis() - DAY_SECONDS * 1000
    ) {
        config.lastRecycleBinCheck = System.currentTimeMillis()
        ensureBackgroundThread {
            try {
                messagesDB.getOldRecycleBinMessages(
                    timestamp = System.currentTimeMillis() - MONTH_SECONDS * 1000L
                ).forEach { message ->
                    deleteMessage(message.id, message.isMMS)
                }
                callback?.invoke()
            } catch (_: Exception) {
            }
        }
    }
}

fun Context.emptyMessagesRecycleBin() {
    val messages = messagesDB.getAllRecycleBinMessages()
    for (message in messages) {
        deleteMessage(message.id, message.isMMS)
    }
}

fun Context.emptyMessagesRecycleBinForConversation(threadId: Long) {
    val messages = messagesDB.getThreadMessagesFromRecycleBin(threadId)
    for (message in messages) {
        deleteMessage(message.id, message.isMMS)
    }
}

fun Context.restoreAllMessagesFromRecycleBinForConversation(threadId: Long) {
    messagesDB.deleteThreadMessagesFromRecycleBin(threadId)
}

fun Context.moveMessageToRecycleBin(id: Long) {
    try {
        messagesDB.insertRecycleBinEntry(RecycleBinMessage(id, System.currentTimeMillis()))
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.restoreMessageFromRecycleBin(id: Long) {
    try {
        messagesDB.deleteFromRecycleBin(id)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.updateConversationArchivedStatus(threadId: Long, archived: Boolean) {
    val uri = Threads.CONTENT_URI
    val values = ContentValues().apply {
        put(Threads.ARCHIVED, archived)
    }
    val selection = "${Threads._ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    try {
        contentResolver.update(uri, values, selection, selectionArgs)
    } catch (sqliteException: SQLiteException) {
        if (
            sqliteException.message?.contains("no such column: archived") == true
            && config.isArchiveAvailable
        ) {
            config.isArchiveAvailable = false
            return
        } else {
            throw sqliteException
        }
    }
    if (archived) {
        conversationsDB.moveToArchive(threadId)
    } else {
        conversationsDB.unarchive(threadId)
    }
}

fun Context.deleteMessage(id: Long, isMMS: Boolean) {
    val uri = if (isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    try {
        contentResolver.delete(uri, selection, selectionArgs)
        messagesDB.delete(id)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.deleteScheduledMessage(messageId: Long) {
    try {
        messagesDB.delete(messageId)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.markMessageRead(id: Long, isMMS: Boolean) {
    val uri = if (isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.READ, 1)
        put(Sms.SEEN, 1)
    }
    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    contentResolver.update(uri, contentValues, selection, selectionArgs)
    messagesDB.markRead(id)
}

fun Context.markThreadMessagesRead(threadId: Long) {
    arrayOf(Sms.CONTENT_URI, Mms.CONTENT_URI).forEach { uri ->
        val contentValues = ContentValues().apply {
            put(Sms.READ, 1)
            put(Sms.SEEN, 1)
        }
        val selection = "${Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())
        contentResolver.update(uri, contentValues, selection, selectionArgs)
    }
    messagesDB.markThreadRead(threadId)
}

fun Context.markThreadMessagesUnread(threadId: Long) {
    arrayOf(Sms.CONTENT_URI, Mms.CONTENT_URI).forEach { uri ->
        val contentValues = ContentValues().apply {
            put(Sms.READ, 0)
            put(Sms.SEEN, 0)
        }
        val selection = "${Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())
        contentResolver.update(uri, contentValues, selection, selectionArgs)
    }
}

@SuppressLint("NewApi")
fun Context.getThreadId(address: String): Long {
    return try {
        Threads.getOrCreateThreadId(this, address)
    } catch (_: Exception) {
        0L
    }
}

@SuppressLint("NewApi")
fun Context.getThreadId(addresses: Set<String>): Long {
    return try {
        Threads.getOrCreateThreadId(this, addresses)
    } catch (_: Exception) {
        0L
    }
}

fun Context.showReceivedMessageNotification(
        messageId: Long,
        address: String,
        body: String,
        threadId: Long,
        bitmap: Bitmap?,
        subscriptionId: Int?,
    ) {
    Handler(Looper.getMainLooper()).post {
    val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        try {
            getContactFromAddress(address) { simpleContact ->
                val senderName = getNameFromAddress(address, privateCursor)

                Handler(Looper.getMainLooper()).post {
                    notificationHelper.showMessageNotification(
                        messageId = messageId,
                        address = address,
                        body = body,
                        threadId = threadId,
                        bitmap = bitmap,
                        sender = senderName,
                        subscriptionId = subscriptionId,
                        contact = simpleContact
                    )
                }
            }
        } catch (_: Exception) {
            ensureBackgroundThread {
                val senderName = getNameFromAddress(address, privateCursor)

                Handler(Looper.getMainLooper()).post {
                    notificationHelper.showMessageNotification(
                        messageId = messageId,
                        address = address,
                        body = body,
                        threadId = threadId,
                        bitmap = bitmap,
                        sender = senderName,
                        subscriptionId = subscriptionId
                    )
                }
            }

        }
    }
}

fun Context.getNameFromAddress(address: String, privateCursor: Cursor?): String {
    var sender = getNameAndPhotoFromPhoneNumber(address).name
    if (address == sender) {
        val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
        sender = privateContacts.firstOrNull { it.doesHavePhoneNumber(address) }?.name ?: address
    }
    return sender
}

fun Context.getContactFromAddress(address: String, callback: ((contact: SimpleContact?) -> Unit)) {
    val privateCursor = getMyContactsCursor(false, true)
    SimpleContactsHelper(this).getAvailableContacts(false) {
        var contact = it.firstOrNull { it.doesHavePhoneNumber(address) }
        if (contact == null) {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val privateContact = privateContacts.firstOrNull { it.doesHavePhoneNumber(address) }
            contact = privateContact
        }
        if (contact == null) {
            contact = it.firstOrNull { it.phoneNumbers.map { it.value }.any { it == address } }
        }
        callback(contact)
    }
}

fun Context.getNotificationBitmap(photoUri: String): Bitmap? {
    val size = resources.getDimension(R.dimen.notification_large_icon_size).toInt()
    if (photoUri.isEmpty()) {
        return null
    }

    val options = RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        .centerCrop()

    return try {
        Glide.with(this)
            .asBitmap()
            .load(photoUri)
            .apply(options)
            .apply(RequestOptions.circleCropTransform())
            .into(size, size)
            .get()
    } catch (_: Exception) {
        null
    }
}

fun Context.removeDiacriticsIfNeeded(text: String): String {
    return if (config.useSimpleCharacters) text.normalizeString() else text
}

fun Context.getSmsDraft(threadId: Long): String {
    val draft = try {
        draftsDB.getDraftById(threadId)
    } catch (_: Exception) {
        null
    }

    return draft?.body.orEmpty()
}

fun Context.getAllDrafts(): HashMap<Long, String> {
    val drafts = HashMap<Long, String>()
    try {
        draftsDB.getAll().forEach {
            drafts[it.threadId] = it.body
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return drafts
}

fun Context.saveSmsDraft(body: String, threadId: Long) {
    val draft = Draft(
        threadId = threadId,
        body = body,
        date = System.currentTimeMillis()
    )

    try {
        draftsDB.insertOrUpdate(draft)
    } catch (e: Exception) {
        e.printStackTrace()
        showErrorToast(e)
    }
}

fun Context.deleteSmsDraft(threadId: Long) {
    try {
        draftsDB.delete(threadId)
    } catch (e: Exception) {
        e.printStackTrace()
        showErrorToast(e)
    }
}

fun Context.updateLastConversationMessage(threadId: Long) {
    updateLastConversationMessage(setOf(threadId))
}

fun Context.updateLastConversationMessage(threadIds: Iterable<Long>) {
    // update the date and the snippet of the threads, by triggering the
    // following Android code (which runs even if no messages are deleted):
    // https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/android14-release/src/com/android/providers/telephony/MmsSmsProvider.java#1409
    val uri = Threads.CONTENT_URI
    val selection =
        "1 = 0" // always-false condition, because we don't actually want to delete any messages
    try {
        contentResolver.delete(uri, selection, null)
        for (threadId in threadIds) {
            val newConversation = getConversations(threadId)[0]
            insertOrUpdateConversation(newConversation)
        }
    } catch (_: Exception) {
    }
}

fun Context.getFileSizeFromUri(uri: Uri): Long {
    val assetFileDescriptor = try {
        contentResolver.openAssetFileDescriptor(uri, "r")
    } catch (_: FileNotFoundException) {
        null
    }

    // uses ParcelFileDescriptor#getStatSize underneath if failed
    val length = assetFileDescriptor?.use { it.length } ?: FILE_SIZE_NONE
    if (length != -1L) {
        return length
    }

    // if "content://" uri scheme, try contentResolver table
    if (uri.scheme.equals(ContentResolver.SCHEME_CONTENT)) {
        return contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                // maybe shouldn't trust ContentResolver for size:
                // https://stackoverflow.com/questions/48302972/content-resolver-returns-wrong-size
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex == -1) {
                    return@use FILE_SIZE_NONE
                }
                cursor.moveToFirst()
                return try {
                    cursor.getLong(sizeIndex)
                } catch (_: Throwable) {
                    FILE_SIZE_NONE
                }
            } ?: FILE_SIZE_NONE
    } else {
        return FILE_SIZE_NONE
    }
}

// fix a glitch at enabling Release version minifying from 5.12.3
// reset messages in 5.14.3 again, as PhoneNumber is no longer minified
// reset messages in 5.19.1 again, as SimpleContact is no longer minified
fun Context.clearAllMessagesIfNeeded(callback: () -> Unit) {
    if (!config.wasDbCleared) {
        ensureBackgroundThread {
            messagesDB.deleteAll()
            config.wasDbCleared = true
            Handler(Looper.getMainLooper()).post(callback)
        }
    } else {
        callback()
    }
}

fun Context.subscriptionManagerCompat(): SubscriptionManager {
    return getSystemService(SubscriptionManager::class.java)
}

fun Context.insertOrUpdateConversation(
    conversation: Conversation,
    cachedConv: Conversation? = conversationsDB.getConversationWithThreadId(conversation.threadId),
) {
    var updatedConv = conversation
    if (cachedConv != null && cachedConv.usesCustomTitle) {
        updatedConv = updatedConv.copy(
            title = cachedConv.title,
            usesCustomTitle = true
        )
    }
    conversationsDB.insertOrUpdate(updatedConv)
}

fun Context.renameConversation(conversation: Conversation, newTitle: String): Conversation {
    val updatedConv = conversation.copy(title = newTitle, usesCustomTitle = true)
    try {
        conversationsDB.insertOrUpdate(updatedConv)
        ensureBackgroundThread {
            shortcutHelper.createOrUpdateShortcut(updatedConv)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return updatedConv
}

fun Context.createTemporaryThread(
    message: Message,
    threadId: Long = generateRandomId(),
    cachedConv: Conversation?,
) {
    val simpleContactHelper = SimpleContactsHelper(this)
    val privateCursor = getMyContactsCursor(false, true)
    simpleContactHelper.getAvailableContacts(false) { contacts ->
        val addresses = message.participants.getAddresses()
        var photoUri = if (addresses.size == 1) {
            simpleContactHelper.getPhotoUriFromPhoneNumber(addresses.first())
        } else {
            ""
        }

        val title = if (cachedConv != null && cachedConv.usesCustomTitle) {
            cachedConv.title
        } else {
            message.participants.getThreadTitle()
        }

        var contact =
            if (addresses.size == 1) contacts.firstOrNull { it.doesHavePhoneNumber(addresses.first()) }
            else null
        if (contact == null && addresses.size == 1) {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            contact = privateContacts.firstOrNull { it.doesHavePhoneNumber(addresses.first()) }

            if (contact == null) {
                contact = contacts.firstOrNull { it.phoneNumbers.map { it.value }.any { it == addresses.first() } }
            }
        }
        if (photoUri == "" && contact != null) photoUri = contact.photoUri

        val isABusinessContact =
            if (addresses.size == 1) contact?.isABusinessContact() ?: isShortCodeWithLetters(addresses.first())
            else false

        val conversation = Conversation(
            threadId = threadId,
            snippet = message.body,
            date = message.date,
            read = true,
            title = title,
            photoUri = photoUri,
            isGroupConversation = addresses.size > 1,
            phoneNumber = addresses.first(),
            isScheduled = true,
            usesCustomTitle = cachedConv?.usesCustomTitle == true,
            isArchived = false,
            isCompany = isABusinessContact,
            isBlocked = false
        )
        try {
            conversationsDB.insertOrUpdate(conversation)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun Context.updateScheduledMessagesThreadId(messages: List<Message>, newThreadId: Long) {
    val scheduledMessages = messages.map { it.copy(threadId = newThreadId) }.toTypedArray()
    messagesDB.insertMessages(*scheduledMessages)
}

fun Context.clearExpiredScheduledMessages(threadId: Long, messagesToDelete: List<Message>? = null) {
    val messages = messagesToDelete ?: messagesDB.getScheduledThreadMessages(threadId)
    val now = System.currentTimeMillis() + 500L

    try {
        messages.filter { it.isScheduled && it.millis() < now }.forEach { msg ->
            messagesDB.delete(msg.id)
        }
        if (messages.filterNot { it.isScheduled && it.millis() < now }.isEmpty()) {
            // delete empty temporary thread
            val conversation = conversationsDB.getConversationWithThreadId(threadId)
            if (conversation != null && conversation.isScheduled) {
                conversationsDB.deleteThreadId(threadId)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return
    }
}

fun Context.getDefaultKeyboardHeight(): Int {
    return resources.getDimensionPixelSize(R.dimen.default_keyboard_height)
}

fun Context.shouldUnarchive(): Boolean {
    return config.isArchiveAvailable && !config.keepConversationsArchived
}

fun Context.copyToUri(src: Uri, dst: Uri) {
    contentResolver.openInputStream(src)?.use { input ->
        contentResolver.openOutputStream(dst, "rwt")?.use { out ->
            input.copyTo(out)
        }
    }
}

//Goodwy
@SuppressLint("MissingPermission")
fun Context.areMultipleSIMsAvailable(): Boolean {
    return try {
        telecomManager.callCapablePhoneAccounts.size > 1
    } catch (_: Exception) {
        false
    }
}

@SuppressLint("UseCompatLoadingForDrawables")
fun Context.getPackageDrawable(packageName: String): Drawable {
    return resources.getDrawable(
        when (packageName) {
            TELEGRAM_PACKAGE -> R.drawable.ic_telegram_vector
            SIGNAL_PACKAGE -> R.drawable.ic_signal_vector
            WHATSAPP_PACKAGE -> R.drawable.ic_whatsapp_vector
            VIBER_PACKAGE -> R.drawable.ic_viber_vector
            else -> R.drawable.ic_threema_vector
        }, theme
    )
}

fun Context.getTextSizeMessage() = when (config.fontSizeMessage) {
    FONT_SIZE_SMALL -> resources.getDimension(com.goodwy.commons.R.dimen.normal_text_size)
    FONT_SIZE_MEDIUM -> resources.getDimension(com.goodwy.commons.R.dimen.bigger_text_size)
    FONT_SIZE_LARGE -> resources.getDimension(com.goodwy.commons.R.dimen.big_text_size)
    else -> resources.getDimension(com.goodwy.commons.R.dimen.extra_big_text_size)
}

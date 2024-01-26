package com.goodwy.smsmessenger.models

import android.net.Uri
import com.goodwy.smsmessenger.extensions.isImageMimeType
import com.goodwy.smsmessenger.extensions.isVCardMimeType
import com.goodwy.smsmessenger.extensions.isVideoMimeType
import com.goodwy.smsmessenger.helpers.ATTACHMENT_DOCUMENT
import com.goodwy.smsmessenger.helpers.ATTACHMENT_MEDIA
import com.goodwy.smsmessenger.helpers.ATTACHMENT_VCARD

data class AttachmentSelection(
    val id: String,
    val uri: Uri,
    val mimetype: String,
    val filename: String,
    var isPending: Boolean,
    val viewType: Int = getViewTypeForMimeType(mimetype)
) {
    companion object {
        fun getViewTypeForMimeType(mimetype: String): Int {
            return when {
                mimetype.isImageMimeType() || mimetype.isVideoMimeType() -> ATTACHMENT_MEDIA
                mimetype.isVCardMimeType() -> ATTACHMENT_VCARD
                else -> ATTACHMENT_DOCUMENT
            }
        }

        fun areItemsTheSame(first: AttachmentSelection, second: AttachmentSelection): Boolean {
            return first.id == second.id
        }

        fun areContentsTheSame(first: AttachmentSelection, second: AttachmentSelection): Boolean {
            return first.uri == second.uri && first.mimetype == second.mimetype && first.filename == second.filename
        }
    }
}

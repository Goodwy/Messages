package com.goodwy.smsmessenger.adapters

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.telephony.SubscriptionManager
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.helpers.letterBackgroundColors
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.activities.NewConversationActivity
import com.goodwy.smsmessenger.activities.SimpleActivity
import com.goodwy.smsmessenger.activities.ThreadActivity
import com.goodwy.smsmessenger.activities.VCardViewerActivity
import com.goodwy.smsmessenger.dialogs.SelectTextDialog
import com.goodwy.smsmessenger.extensions.*
import com.goodwy.smsmessenger.helpers.*
import com.goodwy.smsmessenger.models.*
import kotlinx.android.synthetic.main.item_attachment_image.view.*
import kotlinx.android.synthetic.main.item_attachment_vcard.view.*
import kotlinx.android.synthetic.main.item_received_message.view.*
import kotlinx.android.synthetic.main.item_received_unknown_attachment.view.*
import kotlinx.android.synthetic.main.item_sent_unknown_attachment.view.*
import kotlinx.android.synthetic.main.item_thread_date_time.view.*
import kotlinx.android.synthetic.main.item_thread_error.view.*
import kotlinx.android.synthetic.main.item_thread_sending.view.*
import kotlinx.android.synthetic.main.item_thread_success.view.*

class ThreadAdapter(
    activity: SimpleActivity, var messages: ArrayList<ThreadItem>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {
    private var fontSize = activity.getTextSize()
    private var fontSizeSmall = activity.getTextSizeSmall()

    @SuppressLint("MissingPermission")
    private val hasMultipleSIMCards = (SubscriptionManager.from(activity).activeSubscriptionInfoList?.size ?: 0) > 1

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_thread

    override fun prepareActionMode(menu: Menu) {
        val isOneItemSelected = isOneItemSelected()
        val selectedItem = getSelectedItems().firstOrNull() as? Message
        val hasText = selectedItem?.body != null && selectedItem.body != ""
        menu.apply {
            findItem(R.id.cab_copy_to_clipboard).isVisible = isOneItemSelected && hasText
            findItem(R.id.cab_save_as).isVisible = isOneItemSelected && selectedItem?.attachment?.attachments?.size == 1
            findItem(R.id.cab_share).isVisible = isOneItemSelected && hasText
            findItem(R.id.cab_forward_message).isVisible = isOneItemSelected
            findItem(R.id.cab_select_text).isVisible = isOneItemSelected && hasText
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_copy_to_clipboard -> copyToClipboard()
            R.id.cab_save_as -> saveAs()
            R.id.cab_share -> shareText()
            R.id.cab_forward_message -> forwardMessage()
            R.id.cab_select_text -> selectText()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = messages.filter { it is Message }.size

    override fun getIsItemSelectable(position: Int) = !isThreadDateTime(position)

    override fun getItemSelectionKey(position: Int) = (messages.getOrNull(position) as? Message)?.hashCode()

    override fun getItemKeyPosition(key: Int) = messages.indexOfFirst { (it as? Message)?.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            THREAD_DATE_TIME -> R.layout.item_thread_date_time
            THREAD_RECEIVED_MESSAGE -> R.layout.item_received_message
            THREAD_SENT_MESSAGE_ERROR -> R.layout.item_thread_error
            THREAD_SENT_MESSAGE_SENT -> R.layout.item_thread_success
            THREAD_SENT_MESSAGE_SENDING -> R.layout.item_thread_sending
            else -> R.layout.item_sent_message
        }
        return createViewHolder(layout, parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = messages[position]
        val isClickable = item is ThreadError || item is Message
        val isLongClickable = item is Message
        holder.bindView(item, true, isLongClickable) { itemView, layoutPosition ->
            when (item) {
                is ThreadDateTime -> setupDateTime(itemView, item)
                is ThreadSent -> setupThreadSuccess(itemView, item.delivered)
                is ThreadError -> setupThreadError(itemView)
                is ThreadSending -> setupThreadSending(itemView)
                else -> setupView(holder, itemView, item as Message)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int): Int {
        val item = messages[position]
        return when {
            item is ThreadDateTime -> THREAD_DATE_TIME
            (messages[position] as? Message)?.isReceivedMessage() == true -> THREAD_RECEIVED_MESSAGE
            item is ThreadError -> THREAD_SENT_MESSAGE_ERROR
            item is ThreadSent -> THREAD_SENT_MESSAGE_SENT
            item is ThreadSending -> THREAD_SENT_MESSAGE_SENDING
            else -> THREAD_SENT_MESSAGE
        }
    }

    private fun copyToClipboard() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        activity.copyToClipboard(firstItem.body)
    }

    private fun saveAs() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        val attachment = firstItem.attachment?.attachments?.first() ?: return
        (activity as ThreadActivity).saveMMS(attachment.mimetype, attachment.uriString)
    }

    private fun shareText() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        activity.shareTextIntent(firstItem.body)
    }

    private fun selectText() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        if (firstItem.body.trim().isNotEmpty()) {
            SelectTextDialog(activity, firstItem.body)
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size

        // not sure how we can get UnknownFormatConversionException here, so show the error and hope that someone reports it
        val items = try {
            resources.getQuantityString(R.plurals.delete_messages, itemsCnt, itemsCnt)
        } catch (e: Exception) {
            activity.showErrorToast(e)
            return
        }

        val baseString = R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteMessages()
            }
        }
    }

    private fun deleteMessages() {
        val messagesToRemove = getSelectedItems()
        if (messagesToRemove.isEmpty()) {
            return
        }

        val positions = getSelectedItemPositions()
        val threadId = (messagesToRemove.firstOrNull() as? Message)?.threadId ?: return
        messagesToRemove.forEach {
            activity.deleteMessage((it as Message).id, it.isMMS)
        }
        messages.removeAll(messagesToRemove)
        activity.updateLastConversationMessage(threadId)

        activity.runOnUiThread {
            if (messages.filter { it is Message }.isEmpty()) {
                activity.finish()
            } else {
                removeSelectedItems(positions)
            }
            refreshMessages()
        }
    }

    private fun forwardMessage() {
        val message = getSelectedItems().firstOrNull() as? Message ?: return
        val attachment = message.attachment?.attachments?.firstOrNull()
        Intent(activity, NewConversationActivity::class.java).apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message.body)

            if (attachment != null) {
                putExtra(Intent.EXTRA_STREAM, attachment.getUri())
            }

            activity.startActivity(this)
        }
    }

    private fun getSelectedItems() = messages.filter { selectedKeys.contains((it as? Message)?.hashCode() ?: 0) } as ArrayList<ThreadItem>

    private fun isThreadDateTime(position: Int) = messages.getOrNull(position) is ThreadDateTime

    fun updateMessages(newMessages: ArrayList<ThreadItem>, scrollPosition: Int = newMessages.size - 1) {
        val latestMessages = newMessages.clone() as ArrayList<ThreadItem>
        val oldHashCode = messages.hashCode()
        val newHashCode = latestMessages.hashCode()
        if (newHashCode != oldHashCode) {
            messages = latestMessages
            notifyDataSetChanged()
            recyclerView.scrollToPosition(scrollPosition)
        }
    }

    private fun setupView(holder: ViewHolder, view: View, message: Message) {
        view.apply {
            thread_message_holder.isSelected = selectedKeys.contains(message.hashCode())
            thread_message_body.apply {
                text = message.body
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }
            thread_message_body.beVisibleIf(message.body.isNotEmpty())

            val primaryOrSenderColor = if (context.config.bubbleInContactColor) letterBackgroundColors[Math.abs(message.senderName.hashCode().hashCode()) % letterBackgroundColors.size].toInt()
                                        else context.getProperPrimaryColor()

            if (message.isReceivedMessage()) {
                //thread_message_sender_photo.beVisible()
                thread_message_sender_photo.setOnClickListener {
                    val contact = message.participants.first()
                    context.getContactFromAddress(contact.phoneNumbers.first().normalizedNumber) {
                        if (it != null) {
                            activity.runOnUiThread {
                                (activity as ThreadActivity).startContactDetailsIntent(it)
                            }
                        }
                    }
                }
                thread_message_body.setTextColor(textColor)
                thread_message_body.setLinkTextColor(context.getProperPrimaryColor())

                if (!activity.isFinishing && !activity.isDestroyed) {
                    SimpleContactsHelper(context).loadContactImage(message.senderPhotoUri, thread_message_sender_photo, message.senderName)
                }

                val bubbleReceived = when (context.config.bubbleStyle) {
                    1 -> R.drawable.item_received_ios_background
                    else -> R.drawable.item_received_background
                }
                thread_message_body.background = resources.getDrawable(bubbleReceived)
                val paddingTop = context.resources.getDimensionPixelOffset(R.dimen.bubble_padding_top)
                val paddingBottom = context.resources.getDimensionPixelOffset(R.dimen.bubble_padding_bottom)
                val paddingLeft = context.resources.getDimensionPixelOffset(R.dimen.bubble_padding_left)
                val paddingRight = context.resources.getDimensionPixelOffset(R.dimen.bubble_padding_right)
                if (context.config.bubbleStyle == 1) thread_message_body.setPadding(paddingRight, paddingTop, paddingLeft, paddingBottom)

                val backgroundReceived = if (context.config.bubbleInvertColor) primaryOrSenderColor else context.getBottomNavigationBackgroundColor()
                thread_message_body.background.applyColorFilter(backgroundReceived)

                if (!context.config.bubbleInContactColor) {
                    val contrastColorReceived = backgroundReceived.getContrastColor()
                    thread_message_body.setTextColor(contrastColorReceived)
                    thread_message_body.setLinkTextColor(contrastColorReceived)
                }
            } else {
                thread_message_sender_photo?.beGone()

                val bubbleSender = when (context.config.bubbleStyle) {
                    1 -> R.drawable.item_sent_ios_background
                    else -> R.drawable.item_sent_background
                }
                thread_message_body.background = resources.getDrawable(bubbleSender)
                val paddingTop = context.resources.getDimensionPixelOffset(R.dimen.bubble_padding_top)
                val paddingBottom = context.resources.getDimensionPixelOffset(R.dimen.bubble_padding_bottom)
                val paddingLeft = context.resources.getDimensionPixelOffset(R.dimen.bubble_padding_left)
                val paddingRight = context.resources.getDimensionPixelOffset(R.dimen.bubble_padding_right)
                if (context.config.bubbleStyle == 1) thread_message_body.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)

                val background = if (context.config.bubbleInvertColor) context.getBottomNavigationBackgroundColor() else primaryOrSenderColor
                thread_message_body.background.applyColorFilter(background)

                if (!context.config.bubbleInContactColor) {
                    val contrastColor = background.getContrastColor()
                    thread_message_body.setTextColor(contrastColor)
                    thread_message_body.setLinkTextColor(contrastColor)
                }
            }

            thread_message_body.setOnLongClickListener {
                holder.viewLongClicked()
                true
            }

            thread_message_body.setOnClickListener {
                val selectedItem = getSelectedItems().firstOrNull()
                val numbers = Regex("[+0-9]+").findAll(message.body)
                    .map(MatchResult::value)
                    .toList()
                if (selectedItem != null) holder.viewClicked(message)
                else when (context.config.actionOnMessageClickSetting) {
                    ACTION_COPY_CODE -> {
                        if (numbers.firstOrNull() != null && numbers.first().count() > 3) {
                            activity.copyToClipboard(numbers.first())
                        }
                    }
                    ACTION_COPY_MESSAGE -> activity.copyToClipboard(message.body)
                    else -> return@setOnClickListener
                }
            }

            thread_mesage_attachments_holder.removeAllViews()
            if (message.attachment?.attachments?.isNotEmpty() == true) {
                for (attachment in message.attachment.attachments) {
                    val mimetype = attachment.mimetype
                    if (mimetype.isImageMimeType() || mimetype.startsWith("video/")) {
                        setupImageView(holder, view, message, attachment)
                    } else if (mimetype.isVCardMimeType()) {
                        setupVCardView(holder, view, message, attachment)
                    } else {
                        setupFileView(holder, view, message, attachment)
                    }

                    thread_message_play_outline.beVisibleIf(mimetype.startsWith("video/"))
                }
            }
        }
    }

    private fun setupImageView(holder: ViewHolder, parent: View, message: Message, attachment: Attachment) {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()
        parent.apply {
            val imageView = layoutInflater.inflate(R.layout.item_attachment_image, null)
            thread_mesage_attachments_holder.addView(imageView)

            val placeholderDrawable = ColorDrawable(Color.TRANSPARENT)
            val isTallImage = attachment.height > attachment.width
            val transformation = if (isTallImage) CenterCrop() else FitCenter()
            val options = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .placeholder(placeholderDrawable)
                .transform(transformation)

            var builder = Glide.with(context)
                .load(uri)
                .transition(DrawableTransitionOptions.withCrossFade())
                .apply(options)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                        thread_message_play_outline.beGone()
                        thread_mesage_attachments_holder.removeView(imageView)
                        return false
                    }

                    override fun onResourceReady(dr: Drawable?, a: Any?, t: Target<Drawable>?, d: DataSource?, i: Boolean) =
                        false
                })

            builder = if (isTallImage) {
                builder.override(attachment.width, attachment.width)
            } else {
                builder.override(attachment.width, attachment.height)
            }

            builder.into(imageView.attachment_image)
            imageView.attachment_image.setOnClickListener {
                if (actModeCallback.isSelectable) {
                    holder.viewClicked(message)
                } else {
                    launchViewIntent(uri, mimetype, attachment.filename)
                }
            }
            imageView.setOnLongClickListener {
                holder.viewLongClicked()
                true
            }
        }
    }

    private fun setupVCardView(holder: ViewHolder, parent: View, message: Message, attachment: Attachment) {
        val uri = attachment.getUri()
        parent.apply {
            val vCardView = layoutInflater.inflate(R.layout.item_attachment_vcard, null).apply {
                background.applyColorFilter(backgroundColor.getContrastColor())
                vcard_title.setTextColor(textColor)
                vcard_subtitle.setTextColor(textColor)
                view_contact_details.setTextColor(properPrimaryColor)
            }
            thread_mesage_attachments_holder.addView(vCardView)

            parseVCardFromUri(context, uri) { vCards ->
                val title = vCards.firstOrNull()?.parseNameFromVCard()
                val imageIcon = if (title != null) {
                    SimpleContactsHelper(context).getContactLetterIcon(title)
                } else {
                    null
                }
                activity.runOnUiThread {
                    vCardView.apply {
                        vcard_title.text = title
                        vcard_photo.setImageBitmap(imageIcon)

                        if (vCards.size > 1) {
                            vcard_subtitle.beVisible()
                            val quantity = vCards.size - 1
                            vcard_subtitle.text = resources.getQuantityString(R.plurals.and_other_contacts, quantity, quantity)
                        } else {
                            vcard_subtitle.beGone()
                        }

                        setOnClickListener {
                            if (actModeCallback.isSelectable) {
                                holder.viewClicked(message)
                            } else {
                                val intent = Intent(context, VCardViewerActivity::class.java).also {
                                    it.putExtra(EXTRA_VCARD_URI, uri)
                                }
                                context.startActivity(intent)
                            }
                        }
                        setOnLongClickListener {
                            holder.viewLongClicked()
                            true
                        }
                    }
                }
            }
        }
    }

    private fun setupFileView(holder: ViewHolder, parent: View, message: Message, attachment: Attachment) {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()
        parent.apply {
            if (message.isReceivedMessage()) {
                val attachmentView = layoutInflater.inflate(R.layout.item_received_unknown_attachment, null).apply {
                    thread_received_attachment_label.apply {
                        if (attachment.filename.isNotEmpty()) {
                            thread_received_attachment_label.text = attachment.filename
                        }
                        setTextColor(textColor)
                        setOnClickListener {
                            if (actModeCallback.isSelectable) {
                                holder.viewClicked(message)
                            } else {
                                launchViewIntent(uri, mimetype, attachment.filename)
                            }
                        }
                        setOnLongClickListener {
                            holder.viewLongClicked()
                            true
                        }
                    }
                }
                thread_mesage_attachments_holder.addView(attachmentView)
            } else {
                val background = context.getProperPrimaryColor()
                val attachmentView = layoutInflater.inflate(R.layout.item_sent_unknown_attachment, null).apply {
                    thread_sent_attachment_label.apply {
                        this.background.applyColorFilter(background)
                        setTextColor(background.getContrastColor())
                        if (attachment.filename.isNotEmpty()) {
                            thread_sent_attachment_label.text = attachment.filename
                        }
                        setOnClickListener {
                            if (actModeCallback.isSelectable) {
                                holder.viewClicked(message)
                            } else {
                                launchViewIntent(uri, mimetype, attachment.filename)
                            }
                        }
                        setOnLongClickListener {
                            holder.viewLongClicked()
                            true
                        }
                    }
                }
                thread_mesage_attachments_holder.addView(attachmentView)
            }
        }
    }

    private fun launchViewIntent(uri: Uri, mimetype: String, filename: String) {
        Intent().apply {
            action = Intent.ACTION_VIEW
            setDataAndType(uri, mimetype.toLowerCase())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            try {
                activity.hideKeyboard()
                activity.startActivity(this)
            } catch (e: ActivityNotFoundException) {
                val newMimetype = filename.getMimeType()
                if (newMimetype.isNotEmpty() && mimetype != newMimetype) {
                    launchViewIntent(uri, newMimetype, filename)
                } else {
                    activity.toast(R.string.no_app_found)
                }
            } catch (e: Exception) {
                activity.showErrorToast(e)
            }
        }
    }

    private fun setupDateTime(view: View, dateTime: ThreadDateTime) {
        view.apply {
            thread_date_time.apply {
                text = dateTime.date.formatDateOrTime(context, false, false)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizeSmall)
            }
            thread_date_time.setTextColor(textColor)

            thread_sim_icon.beVisibleIf(hasMultipleSIMCards)
            thread_sim_number.beVisibleIf(hasMultipleSIMCards)
            if (hasMultipleSIMCards) {
                thread_sim_number.text = dateTime.simID
                thread_sim_number.setTextColor(textColor.getContrastColor())
                thread_sim_icon.applyColorFilter(textColor)
            }
        }
    }

    private fun setupThreadSuccess(view: View, isDelivered: Boolean) {
        view.thread_success.setImageResource(if (isDelivered) R.drawable.ic_check_double_vector else R.drawable.ic_check_vector)
        view.thread_success.applyColorFilter(textColor)
    }

    private fun setupThreadError(view: View) {
        view.thread_error.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize - 4)
    }

    private fun setupThreadSending(view: View) {
        view.thread_sending.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            setTextColor(textColor)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing && holder.itemView.thread_message_sender_photo != null) {
            Glide.with(activity).clear(holder.itemView.thread_message_sender_photo)
        }
    }
}

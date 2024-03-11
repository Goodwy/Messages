package com.goodwy.smsmessenger.adapters

import android.graphics.Typeface
import android.graphics.drawable.LayerDrawable
import android.os.Parcelable
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.goodwy.commons.adapters.MyRecyclerViewListAdapter
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.activities.SimpleActivity
import com.goodwy.smsmessenger.databinding.ItemConversationBinding
import com.goodwy.smsmessenger.extensions.*
import com.goodwy.smsmessenger.helpers.*
import com.goodwy.smsmessenger.models.Conversation
import me.thanel.swipeactionview.SwipeActionView
import me.thanel.swipeactionview.SwipeDirection
import me.thanel.swipeactionview.SwipeGestureListener
import org.jetbrains.annotations.NotNull
import kotlin.math.abs
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@Suppress("LeakingThis")
abstract class BaseConversationsAdapter(
    activity: SimpleActivity, recyclerView: MyRecyclerView, onRefresh: () -> Unit, itemClick: (Any) -> Unit,
    var isArchived: Boolean = false
) : MyRecyclerViewListAdapter<Conversation>(activity, recyclerView, ConversationDiffCallback(), itemClick, onRefresh),
    RecyclerViewFastScroller.OnPopupTextUpdate {
    private var fontSize = activity.getTextSize()
    private var drafts = HashMap<Long, String?>()
    private var showContactThumbnails = activity.config.showContactThumbnails

    private var recyclerViewState: Parcelable? = null

    init {
        setupDragListener(true)
        ensureBackgroundThread {
            fetchDrafts(drafts)
        }
        setHasStableIds(true)

        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = restoreRecyclerViewState()
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = restoreRecyclerViewState()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = restoreRecyclerViewState()
        })
    }

    fun updateFontSize() {
        fontSize = activity.getTextSize()
        notifyDataSetChanged()
    }

    fun updateConversations(newConversations: ArrayList<Conversation>, commitCallback: (() -> Unit)? = null) {
        saveRecyclerViewState()
        submitList(newConversations.toList(), commitCallback)
    }

    fun updateDrafts() {
        ensureBackgroundThread {
            val newDrafts = HashMap<Long, String?>()
            fetchDrafts(newDrafts)
            if (drafts.hashCode() != newDrafts.hashCode()) {
                drafts = newDrafts
                activity.runOnUiThread {
                    notifyDataSetChanged()
                }
            }
        }
    }

    override fun getSelectableItemCount() = itemCount

    protected fun getSelectedItems() = currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position == currentList.lastIndex){
            val params = holder.itemView.layoutParams as RecyclerView.LayoutParams
            val margin = activity.resources.getDimension(com.goodwy.commons.R.dimen.shortcut_size).toInt()
            params.bottomMargin = margin
            holder.itemView.layoutParams = params
        } else {
            val params = holder.itemView.layoutParams as RecyclerView.LayoutParams
            params.bottomMargin = 0
            holder.itemView.layoutParams = params
        }

        val conversation = getItem(position)
        holder.bindView(conversation, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, conversation)
        }
        bindViewHolder(holder)
    }

    override fun getItemId(position: Int) = getItem(position).threadId

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val itemView = ItemConversationBinding.bind(holder.itemView)
            Glide.with(activity).clear(itemView.conversationImage)
        }
    }

    private fun fetchDrafts(drafts: HashMap<Long, String?>) {
        drafts.clear()
        for ((threadId, draft) in activity.getAllDrafts()) {
            drafts[threadId] = draft
        }
    }

    private fun setupView(view: View, conversation: Conversation) {
        ItemConversationBinding.bind(view).apply {
            conversationFrameSelect.setupViewBackground(activity)
            val smsDraft = drafts[conversation.threadId]
            draftIndicator.beVisibleIf(smsDraft != null)
            draftIndicator.setTextColor(properPrimaryColor)
            conversationFrame.setBackgroundColor(backgroundColor)

            if (activity.config.unreadIndicatorPosition == UNREAD_INDICATOR_START) {
                unreadIndicatorEnd.beGone()
                unreadIndicator.beInvisibleIf(conversation.read)
                unreadIndicator.setColorFilter(properPrimaryColor)
                pinIndicator.beVisibleIf(activity.config.pinnedConversations.contains(conversation.threadId.toString()))
                pinIndicator.applyColorFilter(properPrimaryColor)
            } else {
                unreadIndicator.beGone()
                unreadIndicatorEnd.beInvisibleIf(conversation.read)
                unreadIndicatorEnd.setColorFilter(properPrimaryColor)
                pinIndicator.beVisibleIf(activity.config.pinnedConversations.contains(conversation.threadId.toString()) && conversation.read)
                pinIndicator.applyColorFilter(properPrimaryColor)

            }

            conversationChevron.applyColorFilter(textColor)
            divider.setBackgroundColor(textColor)
            if (currentList.last() == conversation || !activity.config.useDividers) divider.beInvisible() else divider.beVisible()

            swipeView.isSelected = selectedKeys.contains(conversation.hashCode())

            val title = conversation.title
            conversationAddress.apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.2f)
            }

            conversationBodyShort.apply {
                text = smsDraft ?: conversation.snippet
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.9f)
                maxLines = activity.config.linesCount
            }

            conversationDate.apply {
                text = if (activity.config.useRelativeDate) {
                    DateUtils.getRelativeDateTimeString(
                        context,
                        conversation.date * 1000L,
                        1.minutes.inWholeMilliseconds,
                        2.days.inWholeMilliseconds,
                        0,
                    )
                } else {
                    conversation.date.formatDateOrTime(context, true, false)
                }
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            val style = if (conversation.read) {
                conversationBodyShort.alpha = 0.7f
                if (conversation.isScheduled) Typeface.ITALIC else Typeface.NORMAL
            } else {
                conversationBodyShort.alpha = 1f
                if (conversation.isScheduled) Typeface.BOLD_ITALIC else Typeface.BOLD

            }
            conversationAddress.setTypeface(null, style)
            conversationBodyShort.setTypeface(null, style)

            arrayListOf(conversationAddress, conversationBodyShort, conversationDate).forEach {
                it.setTextColor(textColor)
            }

            // at group conversations we use an icon as the placeholder, not any letter
            val placeholder = if (conversation.isGroupConversation) {
                SimpleContactsHelper(activity).getColoredGroupIcon(title)
            } else {
                null
            }

            conversationImage.beGoneIf(!showContactThumbnails)
            //SimpleContactsHelper(activity).loadContactImage(conversation.photoUri, conversationImage, title, placeholder)
            if (title == conversation.phoneNumber) {
                val drawable = ResourcesCompat.getDrawable(resources, R.drawable.placeholder_contact, activity.theme)
                if (baseConfig.useColoredContacts) {
                    val letterBackgroundColors = activity.getLetterBackgroundColors()
                    val color = letterBackgroundColors[abs(conversation.phoneNumber.hashCode()) % letterBackgroundColors.size].toInt()
                    (drawable as LayerDrawable).findDrawableByLayerId(R.id.placeholder_contact_background).applyColorFilter(color)
                }
                conversationImage.setImageDrawable(drawable)
            } else {
                SimpleContactsHelper(activity).loadContactImage(conversation.photoUri, conversationImage, title, placeholder)
            }

            //swipe
            val isRTL = activity.isRTLLayout
            val swipeLeftAction = if (isRTL) activity.config.swipeRightAction else activity.config.swipeLeftAction
            swipeLeftIcon.setImageResource(swipeActionImageResource(swipeLeftAction))
            swipeLeftIconHolder.setBackgroundColor(swipeActionColor(swipeLeftAction))

            val swipeRightAction = if (isRTL) activity.config.swipeLeftAction else activity.config.swipeRightAction
            swipeRightIcon.setImageResource(swipeActionImageResource(swipeRightAction))
            swipeRightIconHolder.setBackgroundColor(swipeActionColor(swipeRightAction))

            swipeView.setRippleColor(SwipeDirection.Left, swipeActionColor(swipeLeftAction))
            swipeView.setRippleColor(SwipeDirection.Right, swipeActionColor(swipeRightAction))

            if (isArchived) {
                if (swipeLeftAction == SWIPE_ACTION_BLOCK) swipeView.setDirectionEnabled(SwipeDirection.Left, false)
                if (swipeRightAction == SWIPE_ACTION_BLOCK) swipeView.setDirectionEnabled(SwipeDirection.Right, false)
            }

            arrayOf(
                swipeRightIcon, swipeRightIcon
            ).forEach {
                it.setColorFilter(properPrimaryColor.getContrastColor())
            }

            swipeView.swipeGestureListener = object : SwipeGestureListener {
                override fun onSwipedLeft(swipeActionView: SwipeActionView): Boolean {
                    swipedLeft(conversation)
                    if (activity.config.swipeVibration) swipeView.performHapticFeedback()
                    return true
                }

                override fun onSwipedRight(swipeActionView: SwipeActionView): Boolean {
                    swipedRight(conversation)
                    if (activity.config.swipeVibration) swipeView.performHapticFeedback()
                    return true
                }
            }
        }
    }

    override fun onChange(position: Int) = currentList.getOrNull(position)?.title ?: ""

    private fun saveRecyclerViewState() {
        recyclerViewState = recyclerView.layoutManager?.onSaveInstanceState()
    }

    private fun restoreRecyclerViewState() {
        recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
    }

    private class ConversationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return Conversation.areItemsTheSame(oldItem, newItem)
        }

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return Conversation.areContentsTheSame(oldItem, newItem)
        }
    }

    abstract fun swipedLeft(conversation: Conversation)

    abstract fun swipedRight(conversation: Conversation)

    private fun swipeActionImageResource(swipeAction: Int): Int {
        return when (swipeAction) {
            SWIPE_ACTION_DELETE -> com.goodwy.commons.R.drawable.ic_delete_outline
            SWIPE_ACTION_ARCHIVE -> if (isArchived) R.drawable.ic_unarchive_vector else R.drawable.ic_archive_vector
            SWIPE_ACTION_BLOCK -> com.goodwy.commons.R.drawable.ic_block_vector
            SWIPE_ACTION_CALL -> com.goodwy.commons.R.drawable.ic_phone_vector
            SWIPE_ACTION_MESSAGE -> com.goodwy.commons.R.drawable.ic_messages
            else -> R.drawable.ic_mark_read
        }
    }

    private fun swipeActionColor(swipeAction: Int): Int {
        return when (swipeAction) {
            SWIPE_ACTION_DELETE -> resources.getColor(R.color.red_call, activity.theme)
            SWIPE_ACTION_ARCHIVE -> resources.getColor(R.color.swipe_purple, activity.theme)
            SWIPE_ACTION_BLOCK -> resources.getColor(com.goodwy.commons.R.color.red_700, activity.theme)
            SWIPE_ACTION_CALL -> resources.getColor(R.color.green_call, activity.theme)
            SWIPE_ACTION_MESSAGE -> resources.getColor(com.goodwy.commons.R.color.ic_messages, activity.theme)
            else -> properPrimaryColor
        }
    }
}

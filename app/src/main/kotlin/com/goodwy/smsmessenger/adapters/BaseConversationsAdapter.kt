package com.goodwy.smsmessenger.adapters

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Parcelable
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.behaviorule.arturdumchev.library.pixels
import com.bumptech.glide.Glide
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.goodwy.commons.adapters.MyRecyclerViewListAdapter
import com.goodwy.commons.extensions.applyColorFilter
import com.goodwy.commons.extensions.beGone
import com.goodwy.commons.extensions.beGoneIf
import com.goodwy.commons.extensions.beInvisible
import com.goodwy.commons.extensions.beInvisibleIf
import com.goodwy.commons.extensions.beVisible
import com.goodwy.commons.extensions.beVisibleIf
import com.goodwy.commons.extensions.formatDateOrTime
import com.goodwy.commons.extensions.getContrastColor
import com.goodwy.commons.extensions.getTextSize
import com.goodwy.commons.extensions.isDynamicTheme
import com.goodwy.commons.extensions.isRTLLayout
import com.goodwy.commons.extensions.isSystemInDarkMode
import com.goodwy.commons.extensions.setHeightAndWidth
import com.goodwy.commons.extensions.setupViewBackground
import com.goodwy.commons.extensions.slideLeft
import com.goodwy.commons.extensions.slideLeftReturn
import com.goodwy.commons.extensions.slideRight
import com.goodwy.commons.extensions.slideRightReturn
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.activities.SimpleActivity
import com.goodwy.smsmessenger.databinding.ItemConversationBinding
import com.goodwy.smsmessenger.extensions.config
import com.goodwy.smsmessenger.extensions.deleteSmsDraft
import com.goodwy.smsmessenger.extensions.getAllDrafts
import com.goodwy.smsmessenger.helpers.*
import com.goodwy.smsmessenger.models.Conversation
import me.thanel.swipeactionview.SwipeActionView
import me.thanel.swipeactionview.SwipeDirection
import me.thanel.swipeactionview.SwipeGestureListener
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@Suppress("LeakingThis")
abstract class BaseConversationsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    onRefresh: () -> Unit,
    itemClick: (Any) -> Unit,
    var isArchived: Boolean = false,
    var isRecycleBin: Boolean = false,
) : MyRecyclerViewListAdapter<Conversation>(
    activity = activity,
    recyclerView = recyclerView,
    diffUtil = ConversationDiffCallback(),
    itemClick = itemClick,
    onRefresh = onRefresh
),
    RecyclerViewFastScroller.OnPopupTextUpdate {

    companion object {
        private const val MAX_UNREAD_BADGE_COUNT = 99
    }

    private var fontSize = activity.getTextSize()
    private var drafts = HashMap<Long, String>()
    private var showContactThumbnails = activity.config.showContactThumbnails

    private var recyclerViewState: Parcelable? = null

    init {
        setupDragListener(true)
        setHasStableIds(true)
        updateDrafts()

        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = restoreRecyclerViewState()
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) =
                restoreRecyclerViewState()

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) =
                restoreRecyclerViewState()
        })
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateFontSize() {
        fontSize = activity.getTextSize()
        notifyDataSetChanged()
    }

    fun updateConversations(
        newConversations: ArrayList<Conversation>,
        commitCallback: (() -> Unit)? = null,
    ) {
        saveRecyclerViewState()
        submitList(newConversations.toList(), commitCallback)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateDrafts() {
        ensureBackgroundThread {
            val newDrafts = HashMap<Long, String>()
            fetchDrafts(newDrafts)
            activity.runOnUiThread {
                if (drafts.hashCode() != newDrafts.hashCode()) {
                    drafts = newDrafts
                    notifyDataSetChanged()
                }
            }
        }
    }

    override fun getSelectableItemCount() = itemCount

    protected fun getSelectedItems() = currentList.filter {
        selectedKeys.contains(it.hashCode())
    } as ArrayList<Conversation>

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
        //Add a bottom margin for the last element so that it does not block the floating button
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
        holder.bindView(
            conversation,
            allowSingleClick = true,
            allowLongClick = true
        ) { itemView, _ ->
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

    private fun fetchDrafts(drafts: HashMap<Long, String>) {
        drafts.clear()
        for ((threadId, draft) in activity.getAllDrafts()) {
            drafts[threadId] = draft
        }
    }

    private fun setupView(view: View, conversation: Conversation) {
        ItemConversationBinding.bind(view).apply {
            conversationFrameSelect.setupViewBackground(activity)
            val smsDraft = drafts[conversation.threadId]
            draftIndicator.beVisibleIf(!smsDraft.isNullOrEmpty())
            draftIndicator.setTextColor(properPrimaryColor)

            if (activity.isDynamicTheme() && !activity.isSystemInDarkMode()) {
                conversationFrame.setBackgroundColor(surfaceColor)
            } else conversationFrame.setBackgroundColor(backgroundColor)

            draftClear.apply {
                beVisibleIf(smsDraft != null)
                setColorFilter(properPrimaryColor)
                setOnClickListener {
                    ensureBackgroundThread {
                        context.deleteSmsDraft(conversation.threadId)
                        updateDrafts()
                    }
                }
            }

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
                text = formatConversationDate(conversation.date)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            val isUnread = !conversation.read
            val style = if (isUnread) {
                conversationBodyShort.alpha = 1f
                if (conversation.isScheduled) Typeface.BOLD_ITALIC else Typeface.BOLD
            } else {
                conversationBodyShort.alpha = 0.7f
                if (conversation.isScheduled) Typeface.ITALIC else Typeface.NORMAL
            }
            conversationAddress.setTypeface(null, style)
            conversationBodyShort.setTypeface(null, style)
            conversationDate.setTypeface(null, style)


            if (conversation.isBlocked) {
                val colorRed = resources.getColor(R.color.red_call, activity.theme)
                conversationChevron.applyColorFilter(colorRed)
                arrayListOf(conversationAddress, conversationBodyShort, conversationDate).forEach {
                    it.setTextColor(colorRed)
                }
            } else {
                conversationChevron.applyColorFilter(textColor)
                arrayListOf(conversationAddress, conversationBodyShort, conversationDate).forEach {
                    it.setTextColor(textColor)
                }
            }

            if (activity.config.unreadIndicatorPosition == UNREAD_INDICATOR_START) {
                unreadCountBadge.beGone()
                unreadIndicator.beInvisibleIf(!isUnread)
                unreadIndicator.setColorFilter(properPrimaryColor)
                pinIndicator.beVisibleIf(activity.config.pinnedConversations.contains(conversation.threadId.toString()))
                pinIndicator.applyColorFilter(properPrimaryColor)
            } else {
                unreadIndicator.beGone()
                setupBadgeCount(unreadCountBadge, isUnread, conversation.unreadCount)
                pinIndicator.beVisibleIf(
                    activity.config.pinnedConversations.contains(conversation.threadId.toString())
                        && conversation.read
                )
                pinIndicator.applyColorFilter(properPrimaryColor)
            }

            divider.setBackgroundColor(textColor)
            conversationImage.beGoneIf(!showContactThumbnails)
            if (showContactThumbnails) {
                val size = (root.context.pixels(com.goodwy.commons.R.dimen.normal_icon_size) * contactThumbnailsSize).toInt()
                conversationImage.setHeightAndWidth(size)
                if ((title == conversation.phoneNumber || conversation.isCompany) && conversation.photoUri == "") {
                    val drawable =
                        if (conversation.isCompany) SimpleContactsHelper(activity).getColoredCompanyIcon(conversation.title)
                        else SimpleContactsHelper(activity).getColoredContactIcon(conversation.title)
                    conversationImage.setImageDrawable(drawable)
                } else {
                    // at group conversations we use an icon as the placeholder, not any letter
                    val placeholder = if (conversation.isGroupConversation) {
                        SimpleContactsHelper(activity).getColoredGroupIcon(title)
                    } else {
                        null
                    }

                    SimpleContactsHelper(activity).loadContactImage(
                        path = conversation.photoUri,
                        imageView = conversationImage,
                        placeholderName = title,
                        placeholderImage = placeholder
                    )
                }
            }

            //swipe
            val isRTL = activity.isRTLLayout
            if (isRecycleBin) {
                val swipeLeftResource =
                    if (isRTL) R.drawable.ic_delete_restore else com.goodwy.commons.R.drawable.ic_delete_outline
                swipeLeftIcon.setImageResource(swipeLeftResource)
                val swipeLeftColor =
                    if (isRTL) resources.getColor(R.color.swipe_purple, activity.theme) else resources.getColor(R.color.red_call, activity.theme)
                swipeLeftIconHolder.setBackgroundColor(swipeLeftColor)

                val swipeRightResource =
                    if (isRTL) com.goodwy.commons.R.drawable.ic_delete_outline else R.drawable.ic_delete_restore
                swipeRightIcon.setImageResource(swipeRightResource)
                val swipeRightColor =
                    if (isRTL) resources.getColor(R.color.red_call, activity.theme) else resources.getColor(R.color.swipe_purple, activity.theme)
                swipeRightIconHolder.setBackgroundColor(swipeRightColor)

                if (activity.config.swipeRipple) {
                    swipeView.setRippleColor(SwipeDirection.Left, swipeLeftColor)
                    swipeView.setRippleColor(SwipeDirection.Right, swipeRightColor)
                }
            } else {
                val swipeLeftAction = if (isRTL) activity.config.swipeRightAction else activity.config.swipeLeftAction
                swipeLeftIcon.setImageResource(swipeActionImageResource(swipeLeftAction, conversation.read))
                swipeLeftIconHolder.setBackgroundColor(swipeActionColor(swipeLeftAction))

                val swipeRightAction = if (isRTL) activity.config.swipeLeftAction else activity.config.swipeRightAction
                swipeRightIcon.setImageResource(swipeActionImageResource(swipeRightAction, conversation.read))
                swipeRightIconHolder.setBackgroundColor(swipeActionColor(swipeRightAction))

                if (!activity.config.useSwipeToAction) {
                    swipeView.setDirectionEnabled(SwipeDirection.Left, false)
                    swipeView.setDirectionEnabled(SwipeDirection.Right, false)
                } else if (isArchived) {
                    if (swipeLeftAction == SWIPE_ACTION_BLOCK || swipeLeftAction == SWIPE_ACTION_NONE) swipeView.setDirectionEnabled(
                        SwipeDirection.Left,
                        false
                    )
                    if (swipeRightAction == SWIPE_ACTION_BLOCK || swipeRightAction == SWIPE_ACTION_NONE) swipeView.setDirectionEnabled(
                        SwipeDirection.Right,
                        false
                    )
                } else {
                    if (swipeLeftAction == SWIPE_ACTION_NONE) swipeView.setDirectionEnabled(SwipeDirection.Left, false)
                    if (swipeRightAction == SWIPE_ACTION_NONE) swipeView.setDirectionEnabled(SwipeDirection.Right, false)
                }

                if (activity.config.swipeRipple) {
                    swipeView.setRippleColor(SwipeDirection.Left, swipeActionColor(swipeLeftAction))
                    swipeView.setRippleColor(SwipeDirection.Right, swipeActionColor(swipeRightAction))
                }
            }

            arrayOf(
                swipeLeftIcon, swipeRightIcon
            ).forEach {
                it.setColorFilter(properPrimaryColor.getContrastColor())
            }

            swipeView.useHapticFeedback = activity.config.swipeVibration
            swipeView.swipeGestureListener = object : SwipeGestureListener {
                override fun onSwipedLeft(swipeActionView: SwipeActionView): Boolean {
                    swipeLeftIcon.slideLeftReturn(swipeLeftIconHolder)
                    swipedLeft(conversation)
                    return true
                }

                override fun onSwipedRight(swipeActionView: SwipeActionView): Boolean {
                    swipeRightIcon.slideRightReturn(swipeRightIconHolder)
                    swipedRight(conversation)
                    return true
                }

                override fun onSwipedActivated(swipedRight: Boolean) {
                    if (swipedRight) swipeRightIcon.slideRight(swipeRightIconHolder)
                    else swipeLeftIcon.slideLeft()
                }

                override fun onSwipedDeactivated(swipedRight: Boolean) {
                    if (swipedRight) swipeRightIcon.slideRightReturn(swipeRightIconHolder)
                    else swipeLeftIcon.slideLeftReturn(swipeLeftIconHolder)
                }
            }
        }
    }

    private fun setupBadgeCount(view: TextView, isUnread: Boolean, count: Int) {
        view.apply {
            beInvisibleIf(!isUnread)
            if (isUnread) {
                text = when {
                    count > MAX_UNREAD_BADGE_COUNT -> "$MAX_UNREAD_BADGE_COUNT+"
                    count == 0 -> ""
                    else -> count.toString()
                }
                setTextColor(properPrimaryColor.getContrastColor())
                background?.applyColorFilter(properPrimaryColor)
            }
        }
    }

    private fun formatConversationDate(date: Int?): String {
        return if (date != null) {
            if (activity.config.useRelativeDate) {
                DateUtils.getRelativeDateTimeString(
                    activity,
                    date * 1000L,
                    1.minutes.inWholeMilliseconds,
                    2.days.inWholeMilliseconds,
                    0,
                )
            } else {
                (date * 1000L).formatDateOrTime(
                    context = activity,
                    hideTimeOnOtherDays = true,
                    showCurrentYear = false
                )
            }.toString()
        } else "No date"
    }

//    override fun onChange(position: Int) = currentList.getOrNull(position)?.title ?: ""
    override fun onChange(position: Int) = formatConversationDate(currentList.getOrNull(position)?.date)

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

    private fun swipeActionImageResource(swipeAction: Int, read: Boolean): Int {
        return when (swipeAction) {
            SWIPE_ACTION_DELETE -> com.goodwy.commons.R.drawable.ic_delete_outline
            SWIPE_ACTION_ARCHIVE -> if (isArchived) R.drawable.ic_unarchive_vector else R.drawable.ic_archive_vector
            SWIPE_ACTION_BLOCK -> com.goodwy.commons.R.drawable.ic_block_vector
            SWIPE_ACTION_CALL -> com.goodwy.commons.R.drawable.ic_phone_vector
            SWIPE_ACTION_MESSAGE -> R.drawable.ic_messages
            else -> if (read) R.drawable.ic_mark_unread else R.drawable.ic_mark_read
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

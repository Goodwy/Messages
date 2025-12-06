package com.goodwy.smsmessenger.adapters

import android.view.Menu
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.baseConfig
import com.goodwy.commons.extensions.isRTLLayout
import com.goodwy.commons.extensions.launchCallIntent
import com.goodwy.commons.extensions.notificationManager
import com.goodwy.commons.extensions.toast
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.smsmessenger.BuildConfig
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.activities.SimpleActivity
import com.goodwy.smsmessenger.extensions.config
import com.goodwy.smsmessenger.extensions.conversationsDB
import com.goodwy.smsmessenger.extensions.deleteConversation
import com.goodwy.smsmessenger.extensions.markLastMessageUnread
import com.goodwy.smsmessenger.extensions.markThreadMessagesRead
import com.goodwy.smsmessenger.extensions.updateConversationArchivedStatus
import com.goodwy.smsmessenger.helpers.SWIPE_ACTION_ARCHIVE
import com.goodwy.smsmessenger.helpers.SWIPE_ACTION_BLOCK
import com.goodwy.smsmessenger.helpers.SWIPE_ACTION_CALL
import com.goodwy.smsmessenger.helpers.SWIPE_ACTION_DELETE
import com.goodwy.smsmessenger.helpers.SWIPE_ACTION_MESSAGE
import com.goodwy.smsmessenger.helpers.refreshConversations
import com.goodwy.smsmessenger.messaging.isShortCodeWithLetters
import com.goodwy.smsmessenger.models.Conversation

class ArchivedConversationsAdapter(
    activity: SimpleActivity, recyclerView: MyRecyclerView, onRefresh: () -> Unit, itemClick: (Any) -> Unit,
) : BaseConversationsAdapter(activity, recyclerView, onRefresh, itemClick, isArchived = true) {
    override fun getActionMenuId() = R.menu.cab_archived_conversations

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_unarchive -> unarchiveConversation()
            R.id.cab_select_all -> selectAll()
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)

        val baseString = com.goodwy.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteConversations()
            }
        }
    }

    private fun deleteConversations() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsToRemove = currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        conversationsToRemove.forEach {
            activity.deleteConversation(it.threadId)
            activity.notificationManager.cancel(it.threadId.hashCode())
        }

        removeConversationsFromList(conversationsToRemove)
    }

    private fun unarchiveConversation() {
        if (selectedKeys.isEmpty()) {
            return
        }

        ensureBackgroundThread {
            val conversationsToUnarchive = currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
            conversationsToUnarchive.forEach {
                activity.updateConversationArchivedStatus(it.threadId, false)
            }

            removeConversationsFromList(conversationsToUnarchive)
        }
    }

    private fun removeConversationsFromList(removedConversations: List<Conversation>) {
        val newList = try {
            currentList.toMutableList().apply { removeAll(removedConversations) }
        } catch (ignored: Exception) {
            currentList.toMutableList()
        }

        activity.runOnUiThread {
            if (newList.none { selectedKeys.contains(it.hashCode()) }) {
                refreshConversations()
                finishActMode()
            } else {
                submitList(newList)
                if (newList.isEmpty()) {
                    refreshConversations()
                }
            }
        }
    }

    override fun swipedLeft(conversation: Conversation) {
        val swipeLeftAction = if (activity.isRTLLayout) activity.config.swipeRightAction else activity.config.swipeLeftAction
        swipeAction(swipeLeftAction, conversation)
    }

    override fun swipedRight(conversation: Conversation) {
        val swipeRightAction = if (activity.isRTLLayout) activity.config.swipeLeftAction else activity.config.swipeRightAction
        swipeAction(swipeRightAction, conversation)
    }

    private fun swipeAction(swipeAction: Int, conversation: Conversation) {
        when (swipeAction) {
            SWIPE_ACTION_DELETE -> swipedDelete(conversation)
            SWIPE_ACTION_ARCHIVE -> swipedArchive(conversation)
            SWIPE_ACTION_BLOCK -> activity.toast(com.goodwy.commons.R.string.no)
            SWIPE_ACTION_CALL -> swipedCall(conversation)
            SWIPE_ACTION_MESSAGE -> swipedSMS(conversation)
            else -> swipedMarkRead(conversation)
        }
    }

    private fun swipedArchive(conversation: Conversation) {
        ensureBackgroundThread {
            val conversationsToUnarchive = ArrayList<Conversation>()
            conversationsToUnarchive.add(conversation)
            conversationsToUnarchive.forEach {
                activity.updateConversationArchivedStatus(it.threadId, false)
            }

            swipedRemoveConversationsFromList(conversationsToUnarchive)
        }
    }

    private fun swipedRemoveConversationsFromList(removedConversations: List<Conversation>) {
        val newList = try {
            currentList.toMutableList().apply { removeAll(removedConversations) }
        } catch (ignored: Exception) {
            currentList.toMutableList()
        }

        activity.runOnUiThread {
            submitList(newList)
            if (newList.isEmpty()) {
                refreshConversations()
            }
        }
    }

    private fun swipedDelete(conversation: Conversation) {
        if (activity.baseConfig.skipDeleteConfirmation) {
            ensureBackgroundThread {
                swipedDeleteConversations(conversation)
            }
        } else {
            val item = conversation.title
            val baseString = com.goodwy.commons.R.string.deletion_confirmation
            val question = String.format(resources.getString(baseString), item)

            ConfirmationDialog(activity, question) {
                ensureBackgroundThread {
                    swipedDeleteConversations(conversation)
                }
            }
        }
    }

    private fun swipedDeleteConversations(conversation: Conversation) {
        val conversationsToRemove = ArrayList<Conversation>()
        conversationsToRemove.add(conversation)
        conversationsToRemove.forEach {
            activity.deleteConversation(it.threadId)
            activity.notificationManager.cancel(it.threadId.hashCode())
        }

        val newList = try {
            currentList.toMutableList().apply { removeAll(conversationsToRemove) }
        } catch (ignored: Exception) {
            currentList.toMutableList()
        }

        activity.runOnUiThread {
            submitList(newList)
            if (newList.isEmpty()) {
                refreshConversations()
            }
        }
    }

    private fun swipedMarkRead(conversation: Conversation) {
        ensureBackgroundThread {
            if (conversation.read) {
                activity.conversationsDB.markUnread(conversation.threadId)
//                activity.markThreadMessagesUnread(conversation.threadId)
                activity.markLastMessageUnread(conversation.threadId)
            } else {
                activity.conversationsDB.markRead(conversation.threadId)
                activity.markThreadMessagesRead(conversation.threadId)
            }

            activity.runOnUiThread {
                refreshConversations()
            }
        }
    }

    private fun swipedSMS(conversation: Conversation) {
        itemClick.invoke(conversation)
    }

    private fun swipedCall(conversation: Conversation) {
        if (conversation.isGroupConversation || isShortCodeWithLetters(conversation.phoneNumber)) activity.toast(com.goodwy.commons.R.string.no_phone_number_found)
        else {
            activity.launchCallIntent(conversation.phoneNumber, key = BuildConfig.RIGHT_APP_KEY)
            finishActMode()
        }
    }
}

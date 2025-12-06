package com.goodwy.smsmessenger.adapters

import android.view.Menu
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.isRTLLayout
import com.goodwy.commons.extensions.notificationManager
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.activities.SimpleActivity
import com.goodwy.smsmessenger.extensions.config
import com.goodwy.smsmessenger.extensions.createTemporaryThread
import com.goodwy.smsmessenger.extensions.deleteConversation
import com.goodwy.smsmessenger.extensions.deleteMessage
import com.goodwy.smsmessenger.extensions.deleteScheduledMessage
import com.goodwy.smsmessenger.extensions.messagesDB
import com.goodwy.smsmessenger.extensions.restoreAllMessagesFromRecycleBinForConversation
import com.goodwy.smsmessenger.extensions.updateLastConversationMessage
import com.goodwy.smsmessenger.extensions.updateScheduledMessagesThreadId
import com.goodwy.smsmessenger.helpers.SWIPE_ACTION_DELETE
import com.goodwy.smsmessenger.helpers.SWIPE_ACTION_RESTORE
import com.goodwy.smsmessenger.helpers.generateRandomId
import com.goodwy.smsmessenger.helpers.refreshConversations
import com.goodwy.smsmessenger.messaging.cancelScheduleSendPendingIntent
import com.goodwy.smsmessenger.models.Conversation
import com.goodwy.smsmessenger.models.Message
import kotlin.collections.forEach

class RecycleBinConversationsAdapter(
    activity: SimpleActivity, recyclerView: MyRecyclerView, onRefresh: () -> Unit, itemClick: (Any) -> Unit
) : BaseConversationsAdapter(activity, recyclerView, onRefresh, itemClick, isRecycleBin = true) {
    override fun getActionMenuId() = R.menu.cab_recycle_bin_conversations

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_restore -> askConfirmRestore()
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
            // You need to delete only the messages from the recycle bin, not the entire conversation
            //activity.deleteConversation(it.threadId)
            deleteMessages(it)
            activity.notificationManager.cancel(it.threadId.hashCode())
        }

        removeConversationsFromList(conversationsToRemove)
    }

    private fun askConfirmRestore() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)

        val baseString = R.string.restore_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                restoreConversations()
            }
        }
    }

    private fun restoreConversations() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsToRemove = currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        conversationsToRemove.forEach {
            activity.restoreAllMessagesFromRecycleBinForConversation(it.threadId)
        }

        removeConversationsFromList(conversationsToRemove)
    }

    private fun removeConversationsFromList(removedConversations: List<Conversation>) {
        val newList = try {
            currentList.toMutableList().apply { removeAll(removedConversations) }
        } catch (_: Exception) {
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
        val swipeLeftAction = if (activity.isRTLLayout) SWIPE_ACTION_RESTORE else SWIPE_ACTION_DELETE
        swipeAction(swipeLeftAction, conversation)
    }

    override fun swipedRight(conversation: Conversation) {
        val swipeRightAction = if (activity.isRTLLayout) SWIPE_ACTION_DELETE else SWIPE_ACTION_RESTORE
        swipeAction(swipeRightAction, conversation)
    }

    private fun swipeAction(swipeAction: Int, conversation: Conversation) {
        when (swipeAction) {
            SWIPE_ACTION_DELETE -> swipedDelete(conversation)
            else -> swipedRestore(conversation)
        }
    }

    private fun swipedDelete(conversation: Conversation) {
        val item = conversation.title
        val baseString = com.goodwy.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), item)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                swipedDeleteConversations(conversation)
            }
        }
    }

    private fun swipedDeleteConversations(conversation: Conversation) {
        // You need to delete only the messages from the recycle bin, not the entire conversation
        //activity.deleteConversation(conversation.threadId)
        deleteMessages(conversation)
        activity.notificationManager.cancel(conversation.threadId.hashCode())

        val conversationsToRemove = ArrayList<Conversation>()
        conversationsToRemove.add(conversation)
        removeConversationsFromList(conversationsToRemove)
    }


    private fun deleteMessages(
        conversation: Conversation,
    ) {
        val threadId = conversation.threadId
        val messagesToRemove = try {
            // We only delete messages from the recycle bin
            activity.messagesDB.getThreadMessagesFromRecycleBin(threadId).toMutableList() as ArrayList<Message>
        } catch (_: Exception) {
            ArrayList()
        }

        messagesToRemove.forEach { message ->
            val messageId = message.id
            if (message.isScheduled) {
                activity.deleteScheduledMessage(messageId)
                activity.cancelScheduleSendPendingIntent(messageId)
            } else {
                activity.deleteMessage(messageId, message.isMMS)
            }
        }

        // move all scheduled messages to a temporary thread when there are no real messages left
        if (messagesToRemove.isNotEmpty() && messagesToRemove.all { it.isScheduled }) {
            val scheduledMessage = messagesToRemove.last()
            val fakeThreadId = generateRandomId()
            activity.createTemporaryThread(scheduledMessage, fakeThreadId, conversation)
            activity.updateScheduledMessagesThreadId(messagesToRemove, fakeThreadId)
        }
    }

    private fun swipedRestore(conversation: Conversation) {
        val item = conversation.title
        val baseString = R.string.restore_confirmation
        val question = String.format(resources.getString(baseString), item)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                swipedRestoreConversations(conversation)
            }
        }
    }

    private fun swipedRestoreConversations(conversation: Conversation) {
        activity.restoreAllMessagesFromRecycleBinForConversation(conversation.threadId)

        val conversationsToRemove = ArrayList<Conversation>()
        conversationsToRemove.add(conversation)
        removeConversationsFromList(conversationsToRemove)
    }
}

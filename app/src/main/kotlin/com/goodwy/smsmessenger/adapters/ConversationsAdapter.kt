package com.goodwy.smsmessenger.adapters

import android.content.Intent
import android.text.TextUtils
import android.view.Menu
import com.goodwy.commons.dialogs.CallConfirmationDialog
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.activities.SimpleActivity
import com.goodwy.smsmessenger.dialogs.RenameConversationDialog
import com.goodwy.smsmessenger.extensions.*
import com.goodwy.smsmessenger.helpers.*
import com.goodwy.smsmessenger.messaging.isShortCodeWithLetters
import com.goodwy.smsmessenger.models.Conversation

class ConversationsAdapter(
    activity: SimpleActivity, recyclerView: MyRecyclerView, onRefresh: () -> Unit, itemClick: (Any) -> Unit
) : BaseConversationsAdapter(activity, recyclerView, onRefresh, itemClick) {
    override fun getActionMenuId() = R.menu.cab_conversations

    override fun prepareActionMode(menu: Menu) {
        val selectedItems = getSelectedItems()
        val isSingleSelection = isOneItemSelected()
        val selectedConversation = selectedItems.firstOrNull() ?: return
        val isGroupConversation = selectedConversation.isGroupConversation
        val archiveAvailable = activity.config.isArchiveAvailable

        menu.apply {
            //findItem(R.id.cab_block_number).title = activity.addLockedLabelIfNeeded(com.goodwy.commons.R.string.block_number)
            findItem(R.id.cab_block_number).isVisible = isNougatPlus()
            findItem(R.id.cab_add_number_to_contact).isVisible = isSingleSelection && !isGroupConversation
            findItem(R.id.cab_dial_number).isVisible = isSingleSelection && !isGroupConversation && !isShortCodeWithLetters(selectedConversation.phoneNumber)
            findItem(R.id.cab_copy_number).isVisible = isSingleSelection && !isGroupConversation
            findItem(R.id.cab_rename_conversation).isVisible = isSingleSelection && isGroupConversation
            findItem(R.id.cab_mark_as_read).isVisible = selectedItems.any { !it.read }
            findItem(R.id.cab_mark_as_unread).isVisible = selectedItems.any { it.read }
            findItem(R.id.cab_archive).isVisible = archiveAvailable
            checkPinBtnVisibility(this)
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_add_number_to_contact -> addNumberToContact()
            R.id.cab_block_number -> askConfirmBlock()
            R.id.cab_dial_number -> dialNumber()
            R.id.cab_copy_number -> copyNumberToClipboard()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_archive -> askConfirmArchive()
            R.id.cab_rename_conversation -> renameConversation(getSelectedItems().first())
            R.id.cab_mark_as_read -> markAsRead()
            R.id.cab_mark_as_unread -> markAsUnread()
            R.id.cab_pin_conversation -> pinConversation(true)
            R.id.cab_unpin_conversation -> pinConversation(false)
            R.id.cab_select_all -> selectAll()
        }
    }

//    private fun tryBlocking() {
//        if (activity.isOrWasThankYouInstalled()) {
//            askConfirmBlock()
//        } else {
//            FeatureLockedDialog(activity) { }
//        }
//    }

    private fun askConfirmBlock() {
        val numbers = getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber }
        val numbersString = TextUtils.join(", ", numbers)
        val question = String.format(resources.getString(com.goodwy.commons.R.string.block_confirmation), numbersString)

        ConfirmationDialog(activity, question) {
            blockNumbers()
        }
    }

    private fun blockNumbers() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val numbersToBlock = getSelectedItems()
        val newList = currentList.toMutableList().apply { removeAll(numbersToBlock) }

        ensureBackgroundThread {
            numbersToBlock.map { it.phoneNumber }.forEach { number ->
                activity.addBlockedNumber(number)
            }

            activity.runOnUiThread {
                submitList(newList)
                selectedKeys.clear()
                finishActMode()
            }
        }
    }

    private fun dialNumber() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        activity.dialNumber(conversation.phoneNumber) {
            finishActMode()
        }
    }

    private fun copyNumberToClipboard() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        activity.copyToClipboard(conversation.phoneNumber)
        finishActMode()
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

    private fun askConfirmArchive() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)

        val baseString = R.string.archive_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                archiveConversations()
            }
        }
    }

    private fun archiveConversations() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsToRemove = currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        conversationsToRemove.forEach {
            activity.updateConversationArchivedStatus(it.threadId, true)
            activity.notificationManager.cancel(it.threadId.hashCode())
        }

        val newList = try {
            currentList.toMutableList().apply { removeAll(conversationsToRemove) }
        } catch (ignored: Exception) {
            currentList.toMutableList()
        }

        activity.runOnUiThread {
            if (newList.none { selectedKeys.contains(it.hashCode()) }) {
                refreshMessages()
                finishActMode()
            } else {
                submitList(newList)
                if (newList.isEmpty()) {
                    refreshMessages()
                }
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

        val newList = try {
            currentList.toMutableList().apply { removeAll(conversationsToRemove) }
        } catch (ignored: Exception) {
            currentList.toMutableList()
        }

        activity.runOnUiThread {
            if (newList.none { selectedKeys.contains(it.hashCode()) }) {
                refreshMessages()
                finishActMode()
            } else {
                submitList(newList)
                if (newList.isEmpty()) {
                    refreshMessages()
                }
            }
        }
    }

    private fun renameConversation(conversation: Conversation) {
        RenameConversationDialog(activity, conversation) {
            ensureBackgroundThread {
                val updatedConv = activity.renameConversation(conversation, newTitle = it)
                activity.runOnUiThread {
                    finishActMode()
                    currentList.toMutableList().apply {
                        set(indexOf(conversation), updatedConv)
                        updateConversations(this as ArrayList<Conversation>)
                    }
                }
            }
        }
    }

    private fun markAsRead() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsMarkedAsRead = currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        ensureBackgroundThread {
            conversationsMarkedAsRead.filter { conversation -> !conversation.read }.forEach {
                activity.conversationsDB.markRead(it.threadId)
                activity.markThreadMessagesRead(it.threadId)
            }

            refreshConversations()
        }
    }

    private fun markAsUnread() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsMarkedAsUnread = currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        ensureBackgroundThread {
            conversationsMarkedAsUnread.filter { conversation -> conversation.read }.forEach {
                activity.conversationsDB.markUnread(it.threadId)
                activity.markThreadMessagesUnread(it.threadId)
            }

            refreshConversations()
        }
    }

    private fun addNumberToContact() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, conversation.phoneNumber)
            activity.launchActivityIntent(this)
        }
    }

    private fun pinConversation(pin: Boolean) {
        val conversations = getSelectedItems()
        if (conversations.isEmpty()) {
            return
        }

        if (pin) {
            activity.config.addPinnedConversations(conversations)
        } else {
            activity.config.removePinnedConversations(conversations)
        }

        getSelectedItemPositions().forEach {
            notifyItemChanged(it)
        }
        refreshConversations()
    }

    private fun checkPinBtnVisibility(menu: Menu) {
        val pinnedConversations = activity.config.pinnedConversations
        val selectedConversations = getSelectedItems()
        menu.findItem(R.id.cab_pin_conversation).isVisible = selectedConversations.any { !pinnedConversations.contains(it.threadId.toString()) }
        menu.findItem(R.id.cab_unpin_conversation).isVisible = selectedConversations.any { pinnedConversations.contains(it.threadId.toString()) }
    }

    private fun refreshConversations() {
        activity.runOnUiThread {
            refreshMessages()
            finishActMode()
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
            SWIPE_ACTION_BLOCK -> swipedBlock(conversation)
            SWIPE_ACTION_CALL -> swipedCall(conversation)
            SWIPE_ACTION_MESSAGE -> swipedSMS(conversation)
            else -> swipedMarkRead(conversation)
        }
    }

    private fun swipedArchive(conversation: Conversation) {
        if (activity.baseConfig.skipArchiveConfirmation) {
            ensureBackgroundThread {
                swipedArchiveConversations(conversation)
            }
        } else {
            val item = conversation.title
            val baseString = R.string.archive_confirmation
            val question = String.format(resources.getString(baseString), item)

            ConfirmationDialog(activity, question) {
                ensureBackgroundThread {
                    swipedArchiveConversations(conversation)
                }
            }
        }
    }

    private fun swipedArchiveConversations(conversation: Conversation) {
        val conversationsToArchive = ArrayList<Conversation>()
        conversationsToArchive.add(conversation)
        conversationsToArchive.forEach {
            activity.updateConversationArchivedStatus(it.threadId, true)
            activity.notificationManager.cancel(it.threadId.hashCode())
        }

        val newList = try {
            currentList.toMutableList().apply { removeAll(conversationsToArchive) }
        } catch (ignored: Exception) {
            currentList.toMutableList()
        }

        activity.runOnUiThread {
            submitList(newList)
            if (newList.isEmpty()) {
                refreshMessages()
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
                refreshMessages()
            }
        }
    }

    private fun swipedMarkRead(conversation: Conversation) {
        ensureBackgroundThread {
            if (conversation.read) {
                activity.conversationsDB.markUnread(conversation.threadId)
                activity.markThreadMessagesUnread(conversation.threadId)
            } else {
                activity.conversationsDB.markRead(conversation.threadId)
                activity.markThreadMessagesRead(conversation.threadId)
            }

            refreshMessages()
        }
    }

    private fun swipedBlock(conversation: Conversation) {
        if (!isNougatPlus()) return
        selectedKeys.add(conversation.hashCode())
        askConfirmBlock()
    }

    private fun swipedSMS(conversation: Conversation) {
        itemClick.invoke(conversation)
    }

    private fun swipedCall(conversation: Conversation) {
        if (conversation.isGroupConversation) activity.toast(com.goodwy.commons.R.string.no_phone_number_found)
        else {
            activity.dialNumber(conversation.phoneNumber) {
                finishActMode()
            }
        }
    }
}

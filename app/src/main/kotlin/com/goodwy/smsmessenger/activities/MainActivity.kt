package com.goodwy.smsmessenger.activities

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.Telephony
import android.speech.RecognizerIntent
import android.text.TextUtils
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.RecyclerView
import com.goodwy.commons.dialogs.PermissionRequiredDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.Release
import com.goodwy.smsmessenger.BuildConfig
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.adapters.ConversationsAdapter
import com.goodwy.smsmessenger.adapters.SearchResultsAdapter
import com.goodwy.smsmessenger.databinding.ActivityMainBinding
import com.goodwy.smsmessenger.extensions.*
import com.goodwy.smsmessenger.helpers.SEARCHED_MESSAGE_ID
import com.goodwy.smsmessenger.helpers.THREAD_ID
import com.goodwy.smsmessenger.helpers.THREAD_TITLE
import com.goodwy.smsmessenger.models.Conversation
import com.goodwy.smsmessenger.models.Events
import com.goodwy.smsmessenger.models.Message
import com.goodwy.smsmessenger.models.SearchResult
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

class MainActivity : SimpleActivity() {
    private val MAKE_DEFAULT_APP_REQUEST = 1

    private var storedPrimaryColor = 0
    private var storedTextColor = 0
    private var storedBackgroundColor = 0
    private var storedFontSize = 0
    private var lastSearchedText = ""
    private var bus: EventBus? = null
    private var isSpeechToTextAvailable = false

    private val binding by viewBinding(ActivityMainBinding::inflate)

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.mainCoordinator,
            nestedView = binding.conversationsList,
            useTransparentNavigation = true,
            useTopSearchMenu = true
        )
        if (config.changeColourTopBar) {
            val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
            setupSearchMenuScrollListener(
                scrollingView = binding.conversationsList,
                searchMenu = binding.mainMenu,
                surfaceColor = useSurfaceColor
            )
        }

        checkAndDeleteOldRecycleBinMessages()
        clearAllMessagesIfNeeded {
            loadMessages()
        }

        binding.mainMenu.updateTitle(getString(R.string.messages))
    }

    @SuppressLint("UnsafeIntentLaunch")
    override fun onResume() {
        super.onResume()

        if (config.tabsChanged || storedBackgroundColor != getProperBackgroundColor()) {
            finish()
            startActivity(intent)
            return
        }

        updateMenuColors()
        refreshMenuItems()

        getOrCreateConversationsAdapter().apply {
            if (storedPrimaryColor != getProperPrimaryColor()) {
                updatePrimaryColor()
            }

            if (storedTextColor != getProperTextColor()) {
                updateTextColor(getProperTextColor())
            }

            if (storedBackgroundColor != getProperBackgroundColor()) {
                updateBackgroundColor(getProperBackgroundColor())
            }

            if (storedFontSize != config.fontSize) {
                updateFontSize()
            }

            updateDrafts()
        }

        updateTextColors(binding.mainCoordinator)
        binding.searchHolder.setBackgroundColor(getProperBackgroundColor())

        val properPrimaryColor = getProperPrimaryColor()
        binding.noConversationsPlaceholder2.setTextColor(properPrimaryColor)
        binding.noConversationsPlaceholder2.underlineText()
        binding.conversationsFastscroller.updateColors(properPrimaryColor)
        binding.conversationsProgressBar.setIndicatorColor(properPrimaryColor)
        binding.conversationsProgressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)
        checkShortcut()
        binding.conversationsFastscroller.trackMarginEnd = navigationBarHeight
        (binding.conversationsFab.layoutParams as? CoordinatorLayout.LayoutParams)?.bottomMargin =
            navigationBarHeight + resources.getDimension(com.goodwy.commons.R.dimen.activity_margin).toInt()


//        if (baseConfig.isUsingSystemTheme) {
//            (search_bar.layoutParams as RelativeLayout.Lay outParams).apply {
//                topMargin=12
//            }
//        }
//        search_bar.setBackgroundResource(R.drawable.search_bg)
//        search_bar.setTextColor(getProperTextColor().adjustAlpha(0.4f))
//        val searchDrawable = resources.getColoredDrawableWithColor(R.drawable.ic_search_top_padding, getProperTextColor(), 102)
//        search_bar.setCompoundDrawablesWithIntrinsicBounds(searchDrawable, null, null, null)
//        search_bar.backgroundTintList = ColorStateList.valueOf(getBottomNavigationBackgroundColor())
//        search_bar.setOnClickListener { launchSearch() }

        binding.conversationsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                hideKeyboard()
            }
        })

        binding.searchResultsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                hideKeyboard()
            }
        })
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onDestroy() {
        super.onDestroy()
        config.tabsChanged = false
        bus?.unregister(this)
    }

    override fun onBackPressed() {
        if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
        } else {
            appLockManager.lock()
            super.onBackPressed()
        }
    }

    private fun setupOptionsMenu() {
        binding.apply {
            mainMenu.getToolbar().inflateMenu(R.menu.menu_main)
            mainMenu.toggleHideOnScroll(config.hideTopBarWhenScroll)

            if (baseConfig.useSpeechToText) {
                isSpeechToTextAvailable = isSpeechToTextAvailable()
                mainMenu.showSpeechToText = isSpeechToTextAvailable
            }
            mainMenu.setupMenu()

            mainMenu.onSpeechToTextClickListener = {
                speechToText()
            }

            mainMenu.onSearchClosedListener = {
                fadeOutSearch()
            }

            mainMenu.onSearchTextChangedListener = { text ->
                if (text.isNotEmpty()) {
                    if (binding.searchHolder.alpha < 1f) {
                        binding.searchHolder.fadeIn()
                    }
                } else {
                    fadeOutSearch()
                }
                searchTextChanged(text)
                mainMenu.clearSearch()
            }

            mainMenu.getToolbar().setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.show_recycle_bin -> launchRecycleBin()
                    R.id.show_archived -> launchArchivedConversations()
                    R.id.show_blocked_numbers -> showBlockedNumbers()
                    R.id.settings -> launchSettings()
                    R.id.about -> launchAbout()
                    else -> return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener true
            }

            mainMenu.clearSearch()
        }
    }

    private fun refreshMenuItems() {
        binding.mainMenu.getToolbar().menu.apply {
            findItem(R.id.show_recycle_bin).isVisible = config.useRecycleBin
            findItem(R.id.show_archived).isVisible = config.isArchiveAvailable
            findItem(R.id.show_blocked_numbers).title =
                if (config.showBlockedNumbers) getString(com.goodwy.strings.R.string.hide_blocked_numbers)
                else getString(com.goodwy.strings.R.string.show_blocked_numbers)
        }
    }

    private fun showBlockedNumbers() {
        config.showBlockedNumbers = !config.showBlockedNumbers
        binding.mainMenu.getToolbar().menu.findItem(R.id.show_blocked_numbers).title =
            if (config.showBlockedNumbers) getString(com.goodwy.strings.R.string.hide_blocked_numbers)
            else getString(com.goodwy.strings.R.string.show_blocked_numbers)
//        runOnUiThread {
//            getRecentsFragment()?.refreshItems()
//        }
        initMessenger()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == MAKE_DEFAULT_APP_REQUEST) {
            if (resultCode == RESULT_OK) {
                askPermissions()
            } else {
                finish()
            }
        } else if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK) {
            if (resultData != null) {
                val res: ArrayList<String> =
                    resultData.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as ArrayList<String>

                val speechToText =  Objects.requireNonNull(res)[0]
                if (speechToText.isNotEmpty()) {
                    binding.mainMenu.setText(speechToText)
                }
            }
        }
    }

    private fun storeStateVariables() {
        storedPrimaryColor = getProperPrimaryColor()
        storedTextColor = getProperTextColor()
        storedBackgroundColor = getProperBackgroundColor()
        storedFontSize = config.fontSize
        config.tabsChanged = false
    }

    private fun updateMenuColors() {
        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val backgroundColor = if (useSurfaceColor) getSurfaceColor() else getProperBackgroundColor()
        val statusBarColor = if (config.changeColourTopBar) getRequiredStatusBarColor(useSurfaceColor) else backgroundColor

        updateStatusbarColor(backgroundColor)
        binding.mainMenu.updateColors(statusBarColor, scrollingView?.computeVerticalScrollOffset() ?: 0)
    }

    private fun loadMessages() {
        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager!!.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    askPermissions()
                } else {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
                }
            } else {
                toast(com.goodwy.commons.R.string.unknown_error_occurred)
                finish()
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                askPermissions()
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
            }
        }
    }

    // while SEND_SMS and READ_SMS permissions are mandatory, READ_CONTACTS is optional.
    // If we don't have it, we just won't be able to show the contact name in some cases
    private fun askPermissions() {
        handlePermission(PERMISSION_READ_SMS) {
            if (it) {
                handlePermission(PERMISSION_SEND_SMS) {
                    if (it) {
                        handlePermission(PERMISSION_READ_CONTACTS) {
                            handleNotificationPermission { granted ->
                                if (!granted) {
                                    PermissionRequiredDialog(
                                        activity = this,
                                        textId = com.goodwy.commons.R.string.allow_notifications_incoming_messages,
                                        positiveActionCallback = { openNotificationSettings() })
                                }
                            }

                            initMessenger()
                            bus = EventBus.getDefault()
                            try {
                                bus!!.register(this)
                            } catch (_: Exception) {
                            }
                        }
                    } else {
                        finish()
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun initMessenger() {
        checkWhatsNewDialog()
        storeStateVariables()
        getCachedConversations()
        binding.noConversationsPlaceholder2.setOnClickListener {
            launchNewConversation()
        }

        binding.conversationsFab.setOnClickListener {
            launchNewConversation()
        }
    }

    private fun getCachedConversations() {
        ensureBackgroundThread {
            val conversations = try {
                conversationsDB.getNonArchived().toMutableList() as ArrayList<Conversation>
            } catch (_: Exception) {
                ArrayList()
            }

            val archived = try {
                conversationsDB.getAllArchived()
            } catch (_: Exception) {
                listOf()
            }

            runOnUiThread {
                setupConversations(conversations, cached = true)
                getNewConversations(
                    (conversations + archived).toMutableList() as ArrayList<Conversation>
                )
            }
            conversations.forEach {
                clearExpiredScheduledMessages(it.threadId)
            }
        }
    }

    private fun getNewConversations(cachedConversations: ArrayList<Conversation>) {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val conversations = getConversations(privateContacts = privateContacts)

            conversations.forEach { clonedConversation ->
                val threadIds = cachedConversations.map { it.threadId }
                if (!threadIds.contains(clonedConversation.threadId)) {
                    conversationsDB.insertOrUpdate(clonedConversation)
                    cachedConversations.add(clonedConversation)
                }
            }

            cachedConversations.forEach { cachedConversation ->
                val threadId = cachedConversation.threadId

                val isTemporaryThread = cachedConversation.isScheduled
                val isConversationDeleted = !conversations.map { it.threadId }.contains(threadId)
                if (isConversationDeleted && !isTemporaryThread) {
                    conversationsDB.deleteThreadId(threadId)
                }

                val newConversation =
                    conversations.find { it.phoneNumber == cachedConversation.phoneNumber }
                if (isTemporaryThread && newConversation != null) {
                    // delete the original temporary thread and move any scheduled messages
                    // to the new thread
                    conversationsDB.deleteThreadId(threadId)
                    messagesDB.getScheduledThreadMessages(threadId)
                        .forEach { message ->
                            messagesDB.insertOrUpdate(
                                message.copy(threadId = newConversation.threadId)
                            )
                        }
                    insertOrUpdateConversation(newConversation, cachedConversation)
                }
            }

            cachedConversations.forEach { cachedConv ->
                val conv = conversations.find {
                    it.threadId == cachedConv.threadId && !Conversation.areContentsTheSame(
                        old = cachedConv, new = it
                    )
                }
                if (conv != null) {
                    // FIXME: Scheduled message date is being reset here. Conversations with
                    //  scheduled messages will have their original date.
                    insertOrUpdateConversation(conv)
                }
            }

            val allConversations = conversationsDB.getNonArchived() as ArrayList<Conversation>
            runOnUiThread {
                setupConversations(allConversations)
            }

            if (config.appRunCount == 1) {
                conversations.map { it.threadId }.forEach { threadId ->
                    val messages = getMessages(
                        threadId = threadId,
                        getImageResolutions = false,
                        includeScheduledMessages = false
                    )
                    messages.chunked(30).forEach { currentMessages ->
                        messagesDB.insertMessages(*currentMessages.toTypedArray())
                    }
                }
            }
        }
    }

    private fun getOrCreateConversationsAdapter(): ConversationsAdapter {
        if (isDynamicTheme() && !isSystemInDarkMode()) {
            binding.conversationsList.setBackgroundColor(getSurfaceColor())
        }

        var currAdapter = binding.conversationsList.adapter
        if (currAdapter == null) {
            hideKeyboard()
            currAdapter = ConversationsAdapter(
                activity = this,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )

            binding.conversationsList.adapter = currAdapter
            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
            }
        }
        return currAdapter as ConversationsAdapter
    }

    private fun setupConversations(
        conversations: ArrayList<Conversation>,
        cached: Boolean = false,
    ) {
        val sortedConversations = if (config.unreadAtTop) {
            conversations.sortedWith(
                compareByDescending<Conversation> {
                    config.pinnedConversations.contains(it.threadId.toString())
                }
                    .thenBy { it.read }
                    .thenByDescending { it.date }
            ).toMutableList() as ArrayList<Conversation>
        } else {
            conversations.sortedWith(
                compareByDescending<Conversation> {
                    config.pinnedConversations.contains(it.threadId.toString())
                }
                    .thenByDescending { it.date }
                    .thenByDescending { it.isGroupConversation } // Group chats at the top
            ).toMutableList() as ArrayList<Conversation>
        }

        if (cached && config.appRunCount == 1) {
            // there are no cached conversations on the first run so we show the
            // loading placeholder and progress until we are done loading from telephony
            showOrHideProgress(conversations.isEmpty())
        } else {
            showOrHideProgress(false)
            showOrHidePlaceholder(conversations.isEmpty())
        }

        try {
            getOrCreateConversationsAdapter().apply {
                updateConversations(sortedConversations) {
                    if (!cached) {
                        showOrHidePlaceholder(currentList.isEmpty())
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun showOrHideProgress(show: Boolean) {
        if (show) {
            binding.conversationsProgressBar.show()
            binding.noConversationsPlaceholder.beVisible()
            binding.noConversationsPlaceholder.text = getString(R.string.loading_messages)
        } else {
            binding.conversationsProgressBar.hide()
            binding.noConversationsPlaceholder.beGone()
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        binding.conversationsFastscroller.beGoneIf(show)
        binding.noConversationsPlaceholder.beVisibleIf(show)
        binding.noConversationsPlaceholder.text = getString(R.string.no_conversations_found)
        binding.noConversationsPlaceholder2.beVisibleIf(show)
    }

    private fun fadeOutSearch() {
        binding.searchHolder.animate()
            .alpha(0f)
            .setDuration(SHORT_ANIMATION_DURATION)
            .withEndAction {
                binding.searchHolder.beGone()
                searchTextChanged("", true)
            }.start()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        getOrCreateConversationsAdapter().notifyDataSetChanged()
    }

    private fun handleConversationClick(any: Any) {
        Intent(this, ThreadActivity::class.java).apply {
            val conversation = any as Conversation
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            startActivity(this)
        }
    }

    private fun launchNewConversation() {
        hideKeyboard()
        Intent(this, NewConversationActivity::class.java).apply {
            startActivity(this)
        }
    }

    private fun checkShortcut() {
        val iconColor = getProperPrimaryColor()
        if (config.lastHandledShortcutColor != iconColor) {
            val newConversation = getCreateNewContactShortcut(iconColor)

            val manager = getSystemService(ShortcutManager::class.java)
            try {
                manager.dynamicShortcuts = listOf(newConversation)
                config.lastHandledShortcutColor = iconColor
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(iconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.new_conversation)
        val drawable =
            AppCompatResources.getDrawable(this, R.drawable.shortcut_plus)

        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_plus_background)
            .applyColorFilter(iconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, NewConversationActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "new_conversation")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .setRank(0)
            .build()
    }

    private fun searchTextChanged(text: String, forceUpdate: Boolean = false) {
        if (!binding.mainMenu.isSearchOpen && !forceUpdate) {
            return
        }

        lastSearchedText = text
        binding.searchPlaceholder2.beGoneIf(text.length >= 2)
        if (text.length >= 2) {
            ensureBackgroundThread {
                val searchQuery = "%$text%"
                val messages = messagesDB.getMessagesWithText(searchQuery)
                val conversations = conversationsDB.getConversationsWithText(searchQuery)
                if (text == lastSearchedText) {
                    showSearchResults(messages, conversations, text)
                }
            }
        } else {
            binding.searchPlaceholder.beVisible()
            binding.searchResultsList.beGone()
        }
        binding.mainMenu.clearSearch()
    }

    private fun showSearchResults(
        messages: List<Message>,
        conversations: List<Conversation>,
        searchedText: String,
    ) {
        val searchResults = ArrayList<SearchResult>()
        conversations.forEach { conversation ->
            val date = (conversation.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )

            val searchResult = SearchResult(
                messageId = -1,
                title = conversation.title,
                phoneNumber = conversation.phoneNumber,
                snippet = conversation.phoneNumber,
                date = date,
                threadId = conversation.threadId,
                photoUri = conversation.photoUri,
                isCompany = conversation.isCompany,
                isBlocked = conversation.isBlocked
            )
            searchResults.add(searchResult)
        }

        messages.sortedByDescending { it.id }.forEach { message ->
            var recipient = message.senderName
            if (recipient.isEmpty() && message.participants.isNotEmpty()) {
                val participantNames = message.participants.map { it.name }
                recipient = TextUtils.join(", ", participantNames)
            }

            val phoneNumber = message.participants.firstOrNull()!!.phoneNumbers.firstOrNull()!!.normalizedNumber
            val date = (message.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )
            val isCompany =
                if (message.participants.size == 1) message.participants.first().isABusinessContact() else false

            val searchResult = SearchResult(
                messageId = message.id,
                title = recipient,
                phoneNumber = phoneNumber,
                snippet = message.body,
                date = date,
                threadId = message.threadId,
                photoUri = message.senderPhotoUri,
                isCompany = isCompany
            )
            searchResults.add(searchResult)
        }

        runOnUiThread {
            binding.searchResultsList.beVisibleIf(searchResults.isNotEmpty())
            binding.searchPlaceholder.beVisibleIf(searchResults.isEmpty())

            val currAdapter = binding.searchResultsList.adapter
            if (currAdapter == null) {
                SearchResultsAdapter(this, searchResults, binding.searchResultsList, searchedText) {
                hideKeyboard()
                    Intent(this, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, (it as SearchResult).threadId)
                        putExtra(THREAD_TITLE, it.title)
                        putExtra(SEARCHED_MESSAGE_ID, it.messageId)
                        startActivity(this)
                    }
                }.apply {
                    binding.searchResultsList.adapter = this
                }
            } else {
                (currAdapter as SearchResultsAdapter).updateItems(searchResults, searchedText)
            }
        }
    }

    private fun launchRecycleBin() {
        hideKeyboard()
        startActivity(Intent(applicationContext, RecycleBinConversationsActivity::class.java))
    }

    private fun launchArchivedConversations() {
        hideKeyboard()
        startActivity(Intent(applicationContext, ArchivedConversationsActivity::class.java))
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshMessages(event: Events.RefreshMessages) {
        initMessenger()
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            add(Release(420, R.string.release_420))
            add(Release(421, R.string.release_421))
            add(Release(423, R.string.release_423))
            add(Release(500, R.string.release_500))
            add(Release(501, R.string.release_501))
            add(Release(510, R.string.release_510))
            add(Release(511, R.string.release_511))
            add(Release(512, R.string.release_512))
            add(Release(513, R.string.release_513))
            add(Release(514, R.string.release_514))
            add(Release(515, R.string.release_515))
            add(Release(520, R.string.release_520))
            add(Release(521, R.string.release_521))
            add(Release(610, R.string.release_610))
            add(Release(611, R.string.release_611))
            add(Release(620, R.string.release_620))
            add(Release(630, R.string.release_630))
            add(Release(631, R.string.release_631))
            add(Release(632, R.string.release_632))
            add(Release(633, R.string.release_633))
            add(Release(690, R.string.release_690))
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}

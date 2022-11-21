package com.goodwy.smsmessenger.activities

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.adapters.SearchResultsAdapter
import com.goodwy.smsmessenger.extensions.conversationsDB
import com.goodwy.smsmessenger.extensions.messagesDB
import com.goodwy.smsmessenger.helpers.SEARCHED_MESSAGE_ID
import com.goodwy.smsmessenger.helpers.THREAD_ID
import com.goodwy.smsmessenger.helpers.THREAD_TITLE
import com.goodwy.smsmessenger.models.Conversation
import com.goodwy.smsmessenger.models.Message
import com.goodwy.smsmessenger.models.SearchResult
import kotlinx.android.synthetic.main.activity_search.*

class SearchActivity : SimpleActivity() {
    private var mIsSearchOpen = false
    private var mLastSearchedText = ""
    private var mSearchMenuItem: MenuItem? = null

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        updateTextColors(search_holder)
        search_placeholder.setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize())
        search_placeholder_2.setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize())
        setupSearch(search_toolbar.menu)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(search_toolbar, searchMenuItem = mSearchMenuItem, appBarLayout = search_bar_layout)
        updateNavigationBarColor(isColorPreview = true)
        search_bar_layout.setBackgroundColor(getProperStatusBarColor())
        search_toolbar.setBackgroundResource(R.drawable.search_bg)
        search_toolbar.backgroundTintList = ColorStateList.valueOf(getBottomNavigationBackgroundColor())
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        mSearchMenuItem = menu.findItem(R.id.search)

        MenuItemCompat.setOnActionExpandListener(mSearchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                mIsSearchOpen = true
                return true
            }

            // this triggers on device rotation too, avoid doing anything
            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                if (mIsSearchOpen) {
                    mIsSearchOpen = false
                    mLastSearchedText = ""
                    finish()
                }
                return true
            }
        })

        mSearchMenuItem?.expandActionView()
        (mSearchMenuItem?.actionView as? SearchView)?.apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (mIsSearchOpen) {
                        mLastSearchedText = newText
                        textChanged(newText)
                    }
                    return true
                }
            })
        }
    }

    private fun textChanged(text: String) {
        search_placeholder_2.beGoneIf(text.length >= 2)
        if (text.length >= 2) {
            ensureBackgroundThread {
                val searchQuery = "%$text%"
                val messages = messagesDB.getMessagesWithText(searchQuery)
                val conversations = conversationsDB.getConversationsWithText(searchQuery)
                if (text == mLastSearchedText) {
                    showSearchResults(messages, conversations, text)

                }
            }
        } else {
            search_placeholder.beVisible()
            search_results_list.beGone()
        }
    }

    private fun showSearchResults(messages: List<Message>, conversations: List<Conversation>, searchedText: String) {
        val searchResults = ArrayList<SearchResult>()
        conversations.forEach { conversation ->
            val date = conversation.date.formatDateOrTime(this, true, true)
            val searchResult = SearchResult(-1, conversation.title, conversation.phoneNumber, conversation.phoneNumber, date, conversation.threadId, conversation.photoUri)
            searchResults.add(searchResult)
        }

        messages.sortedByDescending { it.id }.forEach { message ->
            var recipient = message.senderName
            if (recipient.isEmpty() && message.participants.isNotEmpty()) {
                val participantNames = message.participants.map { it.name }
                recipient = TextUtils.join(", ", participantNames)
            }

            val date = message.date.formatDateOrTime(this, true, true)
            val phoneNumber = message.participants.firstOrNull()!!.phoneNumbers.firstOrNull()!!.normalizedNumber
            val searchResult = SearchResult(message.id, recipient, phoneNumber, message.body, date, message.threadId, message.senderPhotoUri)
            searchResults.add(searchResult)
        }

        runOnUiThread {
            search_results_list.beVisibleIf(searchResults.isNotEmpty())
            search_placeholder.beVisibleIf(searchResults.isEmpty())

            val currAdapter = search_results_list.adapter
            if (currAdapter == null) {
                SearchResultsAdapter(this, searchResults, search_results_list, searchedText) {
                    hideKeyboard()
                    Intent(this, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, (it as SearchResult).threadId)
                        putExtra(THREAD_TITLE, it.title)
                        putExtra(SEARCHED_MESSAGE_ID, it.messageId)
                        startActivity(this)
                    }
                }.apply {
                    search_results_list.adapter = this
                }
            } else {
                (currAdapter as SearchResultsAdapter).updateItems(searchResults, searchedText)
            }
        }
    }
}

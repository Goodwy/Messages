package com.goodwy.smsmessenger.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import android.widget.RelativeLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.SimpleContact
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.adapters.ContactsAdapter
import com.goodwy.smsmessenger.databinding.ActivityNewConversationBinding
import com.goodwy.smsmessenger.databinding.ItemSuggestedContactBinding
import com.goodwy.smsmessenger.extensions.getSuggestedContacts
import com.goodwy.smsmessenger.extensions.getThreadId
import com.goodwy.smsmessenger.helpers.*
import com.goodwy.smsmessenger.messaging.isShortCodeWithLetters
import java.net.URLDecoder
import java.util.Locale

class NewConversationActivity : SimpleActivity() {
    private var allContacts = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()

    private val binding by viewBinding(ActivityNewConversationBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        title = getString(R.string.new_conversation)
        updateTextColors(binding.newConversationHolder)

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.newConversationCoordinator,
            nestedView = binding.contactsList,
            useTransparentNavigation = true,
            useTopSearchMenu = false
        )
        //setupMaterialScrollListener(scrollingView = binding.contactsList, toolbar = binding.newConversationToolbar)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        binding.newConversationAddress.requestFocus()

        // READ_CONTACTS permission is not mandatory, but without it we won't be able to show any suggestions during typing
        handlePermission(PERMISSION_READ_CONTACTS) {
            initContacts()
        }
    }

    override fun onResume() {
        super.onResume()
        val getProperPrimaryColor = getProperPrimaryColor()
        setupToolbar(binding.newConversationToolbar, NavigationIcon.Arrow, statusBarColor = getProperBackgroundColor())
        binding.noContactsPlaceholder2.setTextColor(getProperPrimaryColor)
        binding.noContactsPlaceholder2.underlineText()
        binding.suggestionsLabel.setTextColor(getProperPrimaryColor)

        binding.contactsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                hideKeyboard()
            }
        })
    }

    private fun initContacts() {
        if (isThirdPartyIntent()) {
            return
        }

        fetchContacts()
        if (isDynamicTheme()) {
            (binding.newConversationAddress.layoutParams as RelativeLayout.LayoutParams).apply {
                topMargin=12
            }
        }
        binding.newConversationAddress.setBackgroundResource(com.goodwy.commons.R.drawable.search_bg)
        binding.newConversationAddress.backgroundTintList = ColorStateList.valueOf(getBottomNavigationBackgroundColor())
        binding.newConversationAddress.onTextChangeListener { searchString ->
            val filteredContacts = ArrayList<SimpleContact>()
            allContacts.forEach { contact ->
                if (contact.phoneNumbers.any { it.normalizedNumber.contains(searchString, true) } ||
                    contact.name.contains(searchString, true) ||
                    contact.name.contains(searchString.normalizeString(), true) ||
                    contact.name.normalizeString().contains(searchString, true)) {
                    filteredContacts.add(contact)
                }
            }

            filteredContacts.sortWith(compareBy { !it.name.startsWith(searchString, true) })
            setupAdapter(filteredContacts)

            binding.newConversationConfirm.beVisibleIf(searchString.length > 2)
            binding.newConversationAddressClear.beVisibleIf(searchString.isNotEmpty())
        }

        val properTextColor = getProperTextColor()
        binding.newConversationAddressClear.applyColorFilter(properTextColor)
        binding.newConversationAddressClear.setOnClickListener { binding.newConversationAddress.setText("") }
        binding.newConversationConfirm.applyColorFilter(properTextColor)
        binding.newConversationConfirm.setOnClickListener {
            val number = binding.newConversationAddress.value
            if (isShortCodeWithLetters(number)) {
                binding.newConversationAddress.setText("")
                toast(R.string.invalid_short_code, length = Toast.LENGTH_LONG)
                return@setOnClickListener
            }
            launchThreadActivity(number, number)
        }

        binding.noContactsPlaceholder2.setOnClickListener {
            handlePermission(PERMISSION_READ_CONTACTS) {
                if (it) {
                    fetchContacts()
                }
            }
        }

        val properPrimaryColor = getProperPrimaryColor()
        binding.contactsLetterFastscroller.textColor = properTextColor.getColorStateList()
        binding.contactsLetterFastscroller.pressedTextColor = properPrimaryColor
        binding.contactsLetterFastscrollerThumb.setupWithFastScroller(binding.contactsLetterFastscroller)
        binding.contactsLetterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
        binding.contactsLetterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
    }

    private fun isThirdPartyIntent(): Boolean {
        val result = SmsIntentParser.parse(intent)
        if (result != null) {
            val (body, recipients) = result
            launchThreadActivity(
                phoneNumber = URLDecoder.decode(recipients.replace("+", "%2b").trim()),
                name = "",
                body = body
            )
            finish()
            return true
        }
        return false
    }

    private fun fetchContacts() {
        fillSuggestedContacts {
            SimpleContactsHelper(this).getAvailableContacts(false) {
                allContacts = it

                if (privateContacts.isNotEmpty()) {
                    allContacts.addAll(privateContacts)
                    allContacts.sort()
                }

                runOnUiThread {
                    setupAdapter(allContacts)
                }
            }
        }
    }

    private fun setupAdapter(contacts: ArrayList<SimpleContact>) {
        val hasContacts = contacts.isNotEmpty()
        binding.contactsList.beVisibleIf(hasContacts)
        binding.noContactsPlaceholder.beVisibleIf(!hasContacts)
        binding.noContactsPlaceholder2.beVisibleIf(
            !hasContacts && !hasPermission(
                PERMISSION_READ_CONTACTS
            )
        )

        if (!hasContacts) {
            val placeholderText = if (hasPermission(PERMISSION_READ_CONTACTS)) {
                com.goodwy.commons.R.string.no_contacts_found
            } else {
                com.goodwy.commons.R.string.no_access_to_contacts
            }

            binding.noContactsPlaceholder.text = getString(placeholderText)
        }

        val currAdapter = binding.contactsList.adapter
        if (currAdapter == null) {
            ContactsAdapter(this, contacts, binding.contactsList) {
                hideKeyboard()
                val contact = it as SimpleContact
                val phoneNumbers = contact.phoneNumbers
                if (phoneNumbers.size > 1) {
//                    val primaryNumber = contact.phoneNumbers.find { it.isPrimary }
//                    if (primaryNumber != null) {
//                        launchThreadActivity(primaryNumber.value, contact.name)
//                    } else {
//                        val items = ArrayList<RadioItem>()
//                        phoneNumbers.forEachIndexed { index, phoneNumber ->
//                            val type = getPhoneNumberTypeText(phoneNumber.type, phoneNumber.label)
//                            items.add(
//                                RadioItem(
//                                    index,
//                                    "${phoneNumber.normalizedNumber} ($type)",
//                                    phoneNumber.normalizedNumber
//                                )
//                            )
//                        }
//
//                        RadioGroupDialog(this, items) {
//                            launchThreadActivity(it as String, contact.name)
//                        }
//                    }
                    val items = ArrayList<RadioItem>()
                    phoneNumbers.forEachIndexed { index, phoneNumber ->
                        val type = getPhoneNumberTypeText(phoneNumber.type, phoneNumber.label)
                        val favorite = if (phoneNumber.isPrimary) " ★" else ""
                        items.add(
                            RadioItem(
                                index,
                                "${phoneNumber.value} ($type) $favorite",
                                phoneNumber.normalizedNumber)
                        )
                    }

                    RadioGroupDialog(this, items) {
                        launchThreadActivity(it as String, contact.name)
                    }
                } else {
                    launchThreadActivity(phoneNumbers.first().normalizedNumber, contact.name)
                }
            }.apply {
                binding.contactsList.adapter = this
            }

            if (areSystemAnimationsEnabled) {
                binding.contactsList.scheduleLayoutAnimation()
            }
        } else {
            (currAdapter as ContactsAdapter).updateContacts(contacts)
        }

        setupLetterFastscroller(contacts)
    }

    private fun fillSuggestedContacts(callback: () -> Unit) {
        val privateCursor = getMyContactsCursor(false, true)
        ensureBackgroundThread {
            privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val suggestions = getSuggestedContacts(privateContacts)
            runOnUiThread {
                binding.suggestionsHolder.removeAllViews()
                if (suggestions.isEmpty()) {
                    binding.suggestionsLabel.beGone()
                    binding.suggestionsScrollview.beGone()
                } else {
                    //binding.suggestionsLabel.beVisible()
                    binding.suggestionsScrollview.beVisible()
                    suggestions.forEach {
                        val contact = it
                        ItemSuggestedContactBinding.inflate(layoutInflater).apply {
                            suggestedContactName.text = contact.name
                            suggestedContactName.setTextColor(getProperTextColor())

                            if (!isDestroyed) {
                                SimpleContactsHelper(this@NewConversationActivity).loadContactImage(
                                    contact.photoUri,
                                    suggestedContactImage,
                                    contact.name
                                )
                                binding.suggestionsHolder.addView(root)
                                root.setOnClickListener {
                                    launchThreadActivity(
                                        contact.phoneNumbers.first().normalizedNumber,
                                        contact.name
                                    )
                                }
                            }
                        }
                    }
                }
                callback()
            }
        }
    }

    private fun setupLetterFastscroller(contacts: ArrayList<SimpleContact>) {
        try {
            //Decrease the font size based on the number of letters in the letter scroller
            val all = contacts.map { it.name.substring(0, 1) }
            val unique: Set<String> = HashSet(all)
            val sizeUnique = unique.size
            if (isHighScreenSize()) {
                if (sizeUnique > 48) binding.contactsLetterFastscroller.textAppearanceRes = R.style.LetterFastscrollerStyleTooTiny
                else if (sizeUnique > 37) binding.contactsLetterFastscroller.textAppearanceRes = R.style.LetterFastscrollerStyleTiny
                else binding.contactsLetterFastscroller.textAppearanceRes = R.style.LetterFastscrollerStyleSmall
            } else {
                if (sizeUnique > 36) binding.contactsLetterFastscroller.textAppearanceRes = R.style.LetterFastscrollerStyleTooTiny
                else if (sizeUnique > 30) binding.contactsLetterFastscroller.textAppearanceRes = R.style.LetterFastscrollerStyleTiny
                else binding.contactsLetterFastscroller.textAppearanceRes = R.style.LetterFastscrollerStyleSmall
            }
        } catch (_: Exception) { }

        binding.contactsLetterFastscroller.setupWithRecyclerView(binding.contactsList, { position ->
            try {
                val name = contacts[position].name
                val emoji = name.take(2)
                val character = if (emoji.isEmoji()) emoji else if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(
                    character.uppercase(Locale.getDefault()).normalizeString()
                )
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })
    }

    private fun isHighScreenSize(): Boolean {
        return when (resources.configuration.screenLayout
            and Configuration.SCREENLAYOUT_LONG_MASK) {
            Configuration.SCREENLAYOUT_LONG_NO -> false
            else -> true
        }
    }

    private fun launchThreadActivity(phoneNumber: String, name: String, body: String = "") {
        hideKeyboard()
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra("sms_body") ?: ""
        val numbers = phoneNumber.split(";").toSet()
        val number = if (numbers.size == 1) phoneNumber else Gson().toJson(numbers)
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, getThreadId(numbers))
            putExtra(THREAD_TITLE, name)
            putExtra(THREAD_TEXT, body.ifEmpty { intent.getStringExtra(Intent.EXTRA_TEXT) })
            putExtra(THREAD_NUMBER, number)

            if (intent.action == Intent.ACTION_SEND && intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                putExtra(THREAD_ATTACHMENT_URI, uri?.toString())
            } else if (intent.action == Intent.ACTION_SEND_MULTIPLE && intent.extras?.containsKey(
                    Intent.EXTRA_STREAM
                ) == true
            ) {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                putExtra(THREAD_ATTACHMENT_URIS, uris)
            }

            startActivity(this)
        }
    }
}

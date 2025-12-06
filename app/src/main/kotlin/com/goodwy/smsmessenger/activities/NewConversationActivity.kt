package com.goodwy.smsmessenger.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.widget.Toast
import android.widget.RelativeLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
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
import java.util.Objects

class NewConversationActivity : SimpleActivity() {
    private var allContacts = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()
    private var isSpeechToTextAvailable = false

    private val binding by viewBinding(ActivityNewConversationBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        title = getString(R.string.new_conversation)
        updateTextColors(binding.newConversationHolder)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.contactsList))
//        setupMaterialScrollListener(
//            scrollingView = binding.contactsList,
//            topAppBar = binding.newConversationAppbar
//        )

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

        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val backgroundColor = if (useSurfaceColor) getSurfaceColor() else getProperBackgroundColor()
        setupTopAppBar(binding.newConversationAppbar, NavigationIcon.Arrow, topBarColor = backgroundColor)
        binding.newConversationHolder.setBackgroundColor(backgroundColor)

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

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK) {
            if (resultData != null) {
                val res: java.util.ArrayList<String> =
                    resultData.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as java.util.ArrayList<String>

                val speechToText =  Objects.requireNonNull(res)[0]
                if (speechToText.isNotEmpty()) {
                    binding.newConversationAddress.setText(speechToText)
                }
            }
        }
    }

    private fun initContacts() {
        if (isThirdPartyIntent()) {
            return
        }

        fetchContacts()
        if (isDynamicTheme()) {
            (binding.newConversationAddress.layoutParams as RelativeLayout.LayoutParams).apply {
                topMargin = 12
            }
        }

        isSpeechToTextAvailable = isSpeechToTextAvailable()

        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val surfaceColor = if (useSurfaceColor) getProperBackgroundColor() else getSurfaceColor()
        binding.newConversationAddress.setBackgroundResource(com.goodwy.commons.R.drawable.search_bg)
        binding.newConversationAddress.backgroundTintList = ColorStateList.valueOf(surfaceColor)

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
            binding.newConversationAddressSpeechToText.beVisibleIf(isSpeechToTextAvailable && !searchString.isNotEmpty())
        }

        val properTextColor = getProperTextColor()
        binding.newConversationAddressSpeechToText.beVisibleIf(isSpeechToTextAvailable)
        binding.newConversationAddressSpeechToText.applyColorFilter(properTextColor)
        binding.newConversationAddressSpeechToText.setOnClickListener { speechToText() }
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

        val properAccentColor = getProperAccentColor()
        binding.contactsLetterFastscroller.textColor = properTextColor.getColorStateList()
        binding.contactsLetterFastscroller.pressedTextColor = properAccentColor
        binding.contactsLetterFastscrollerThumb.setupWithFastScroller(binding.contactsLetterFastscroller)
        binding.contactsLetterFastscrollerThumb.textColor = properAccentColor.getContrastColor()
        binding.contactsLetterFastscrollerThumb.thumbColor = properAccentColor.getColorStateList()
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
//            SimpleContactsHelper(this).getAvailableContacts(false) {
                allContacts = it

                if (privateContacts.isNotEmpty()) {
                    allContacts.addAll(privateContacts)
                    allContacts.sort()
                }

                runOnUiThread {
                    setupAdapter(allContacts)
                }
//            }
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
                maybeShowNumberPickerDialog(contact.phoneNumbers) { number ->
                    launchThreadActivity(number.normalizedNumber, contact.name, photoUri = contact.photoUri)
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

    private fun fillSuggestedContacts(callback: (ArrayList<SimpleContact>) -> Unit) {
        val privateCursor = getMyContactsCursor(false, true)
        ensureBackgroundThread {
            SimpleContactsHelper(this).getAvailableContacts(false) {
                privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
                val contacts =  ArrayList(it + privateContacts)
                val suggestions = getSuggestedContacts(contacts)
                runOnUiThread {
                    binding.suggestionsHolder.removeAllViews()
                    if (suggestions.isEmpty()) {
                        binding.suggestionsLabel.beGone()
                        binding.suggestionsScrollview.beGone()
                    } else {
                        //binding.suggestionsLabel.beVisible()
                        binding.suggestionsScrollview.beVisible()
                        suggestions.forEach { contact ->
                            ItemSuggestedContactBinding.inflate(layoutInflater).apply {
                                suggestedContactName.text = contact.name
                                suggestedContactName.setTextColor(getProperTextColor())

                                if (!isDestroyed) {
                                    if (contact.isABusinessContact() && contact.photoUri == "") {
                                        val drawable =
                                            SimpleContactsHelper(this@NewConversationActivity).getColoredCompanyIcon(contact.name)
                                        suggestedContactImage.setImageDrawable(drawable)
                                    } else {
                                        SimpleContactsHelper(this@NewConversationActivity).loadContactImage(
                                            contact.photoUri,
                                            suggestedContactImage,
                                            contact.name
                                        )
                                    }
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
                    callback(it)
                }
            }
        }
    }

    private fun setupLetterFastscroller(contacts: ArrayList<SimpleContact>) {
        try {
            //Decrease the font size based on the number of letters in the letter scroller
            val allNotEmpty = contacts.filter { it.name.isNotEmpty() }
            val all = allNotEmpty.map { it.name.substring(0, 1) }
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
            } catch (_: Exception) {
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

    private fun launchThreadActivity(phoneNumber: String, name: String, body: String = "", photoUri: String = "") {
        hideKeyboard()
//        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra("sms_body") ?: ""
        val numbers = phoneNumber.split(";").toSet()
        val number = if (numbers.size == 1) phoneNumber else Gson().toJson(numbers)
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, getThreadId(numbers))
            putExtra(THREAD_TITLE, name)
            putExtra(THREAD_TEXT, body.ifEmpty { intent.getStringExtra(Intent.EXTRA_TEXT) })
            putExtra(THREAD_NUMBER, number)
            putExtra(THREAD_URI, photoUri)

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

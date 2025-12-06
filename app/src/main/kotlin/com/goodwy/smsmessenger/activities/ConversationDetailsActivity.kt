package com.goodwy.smsmessenger.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.SimpleContact
import com.goodwy.smsmessenger.adapters.ContactsAdapter
import com.goodwy.smsmessenger.databinding.ActivityConversationDetailsBinding
import com.goodwy.smsmessenger.dialogs.RenameConversationDialog
import com.goodwy.smsmessenger.extensions.*
import com.goodwy.smsmessenger.helpers.*
import com.goodwy.smsmessenger.messaging.isShortCodeWithLetters
import com.goodwy.smsmessenger.models.Conversation
import androidx.core.graphics.drawable.toDrawable

class ConversationDetailsActivity : SimpleActivity() {

    private var threadId: Long = 0L
    private var conversation: Conversation? = null
    private lateinit var participants: ArrayList<SimpleContact>

    private var buttonBg = Color.WHITE

    private val binding by viewBinding(ActivityConversationDetailsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.conversationDetailsNestedScrollview))

        initButton()

        threadId = intent.getLongExtra(THREAD_ID, 0L)
        ensureBackgroundThread {
            conversation = conversationsDB.getConversationWithThreadId(threadId)
            participants = if (conversation != null && conversation!!.isScheduled) {
                val message = messagesDB.getThreadMessages(conversation!!.threadId).firstOrNull()
                message?.participants ?: arrayListOf()
            } else {
                getThreadParticipants(threadId, null)
            }
            runOnUiThread {
                setupTopViews()
                setupTextViews()
                setupParticipants()
                updateButton()
                setupCustomNotifications()
            }
        }
    }

    private fun initButton() {
        val primaryColor = getProperPrimaryColor()

        var drawableSMS = AppCompatResources.getDrawable(this, com.goodwy.commons.R.drawable.ic_phone_vector)
        drawableSMS = DrawableCompat.wrap(drawableSMS!!)
        DrawableCompat.setTint(drawableSMS, primaryColor)
        DrawableCompat.setTintMode(drawableSMS, PorterDuff.Mode.SRC_IN)
        binding.oneButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableSMS, null, null)

//        var drawableCall = AppCompatResources.getDrawable(this, com.goodwy.commons.R.drawable.ic_videocam_vector)
//        drawableCall = DrawableCompat.wrap(drawableCall!!)
//        DrawableCompat.setTint(drawableCall, primaryColor)
//        DrawableCompat.setTintMode(drawableCall, PorterDuff.Mode.SRC_IN)
//        twoButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableCall, null, null)

        var drawableVideoCall = AppCompatResources.getDrawable(this, com.goodwy.commons.R.drawable.ic_person_rounded)
        drawableVideoCall = DrawableCompat.wrap(drawableVideoCall!!)
        DrawableCompat.setTint(drawableVideoCall, primaryColor)
        DrawableCompat.setTintMode(drawableVideoCall, PorterDuff.Mode.SRC_IN)
        binding.threeButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableVideoCall, null, null)

        var drawableMail = AppCompatResources.getDrawable(this, com.goodwy.commons.R.drawable.ic_ios_share)
        drawableMail = DrawableCompat.wrap(drawableMail!!)
        DrawableCompat.setTint(drawableMail, primaryColor)
        DrawableCompat.setTintMode(drawableMail, PorterDuff.Mode.SRC_IN)
        binding.fourButton.setCompoundDrawablesWithIntrinsicBounds(null, drawableMail, null, null)

        arrayOf(
            binding.oneButton,
//            binding.twoButton,
            binding.threeButton,
            binding.fourButton,
            binding.conversationNumber,
            binding.conversationBirthdays
        ).forEach {
            it.setTextColor(primaryColor)
        }
    }

    override fun onResume() {
        super.onResume()
        buttonBg = if ((isLightTheme() || isGrayTheme()) && !isDynamicTheme()) Color.WHITE else getSurfaceColor()

//        setupToolbar(binding.conversationDetailsToolbar, NavigationIcon.Arrow)
//        setupTopAppBar(binding.conversationDetailsAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.conversationDetailsHolder)

        val primaryColor = getProperPrimaryColor()
        binding.conversationNameHeading.setTextColor(primaryColor)
        binding.conversationNumber.setTextColor(primaryColor)
        binding.conversationBirthdays.setTextColor(primaryColor)
        binding.membersHeading.setTextColor(primaryColor)
        updateBackgroundColors()
        setupMenu()
    }

    private fun updateBackgroundColors() {
        if (isLightTheme() && !isDynamicTheme()) {
            val colorToWhite = getSurfaceColor()
            supportActionBar?.setBackgroundDrawable(colorToWhite.toDrawable())
            window.decorView.setBackgroundColor(colorToWhite)
            window.statusBarColor = colorToWhite
            //window.navigationBarColor = colorToWhite
            binding.contactActionsHolder.setBackgroundColor(colorToWhite)
            binding.collapsingToolbar.setBackgroundColor(colorToWhite)
        } else {
            val properBackgroundColor = getProperBackgroundColor()
            window.decorView.setBackgroundColor(properBackgroundColor)
            binding.contactActionsHolder.setBackgroundColor(properBackgroundColor)
            binding.collapsingToolbar.setBackgroundColor(properBackgroundColor)
        }

        binding.apply {
            arrayOf(
                oneButton, threeButton, fourButton,
                membersWrapper, conversationNumberContainer, conversationBirthdaysContainer,
                customNotificationsHolder, blockButton
            ).forEach {
                it.background.setTint(buttonBg)
            }
        }
    }

    private fun setupCustomNotifications() = binding.apply {
        val textColor = getProperTextColor()
        customNotificationsButtonChevron.setColorFilter(textColor)
        notificationsIcon.setColorFilter(textColor)
        customNotifications.isChecked = config.customNotifications.contains(threadId.toString())

        if (customNotifications.isChecked) {
            customNotificationsButtonHolder.alpha = 1f
            customNotificationsButtonHolder.isEnabled = true
        } else {
            customNotificationsButtonHolder.alpha = 0.6f
            customNotificationsButtonHolder.isEnabled = false
        }

        customNotificationsWrapper.setOnClickListener {
            customNotifications.toggle()
            if (customNotifications.isChecked) {
                customNotificationsButtonHolder.alpha = 1f
                customNotificationsButtonHolder.isEnabled = true
                config.addCustomNotificationsByThreadId(threadId)
                createNotificationChannel()
            } else {
                customNotificationsButtonHolder.alpha = 0.6f
                customNotificationsButtonHolder.isEnabled = false
                config.removeCustomNotificationsByThreadId(threadId)
                removeNotificationChannel()
            }
        }

        customNotificationsButtonHolder.setOnClickListener {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, threadId.hashCode().toString())
                startActivity(this)
            }
        }
    }

    private fun createNotificationChannel() {
        val name = conversation?.title
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()

        NotificationChannel(threadId.hashCode().toString(), name, NotificationManager.IMPORTANCE_HIGH).apply {
            setBypassDnd(false)
            enableLights(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                audioAttributes
            )
            enableVibration(true)
            notificationManager.createNotificationChannel(this)
        }
    }

    private fun removeNotificationChannel() {
        notificationManager.deleteNotificationChannel(threadId.hashCode().toString())
    }

    private fun setupTextViews() {
        binding.conversationName.apply {
            ResourcesCompat.getDrawable(
                resources,
                com.goodwy.commons.R.drawable.ic_edit_vector, theme
            )?.apply {
                applyColorFilter(getProperTextColor())
                setCompoundDrawablesWithIntrinsicBounds(null, null, this, null)
            }

            text = conversation?.title
            setOnClickListener {
                RenameConversationDialog(
                    this@ConversationDetailsActivity,
                    conversation!!
                ) { title ->
                    text = title
                    ensureBackgroundThread {
                        conversation = renameConversation(conversation!!, newTitle = title)
                    }
                }
            }
        }
    }

    private fun setupParticipants() {
        binding.membersWrapper.beVisibleIf(participants.size > 1)
        val adapter = ContactsAdapter(this, participants, binding.participantsRecyclerview) {
            val contact = it as SimpleContact
            val address = contact.phoneNumbers.first().normalizedNumber
            getContactFromAddress(address) { simpleContact ->
                if (simpleContact != null) {
                    runOnUiThread {
                        startContactDetailsIntentRecommendation(simpleContact)
                    }
                }
            }
        }
        binding.participantsRecyclerview.adapter = adapter
    }

    private fun setupTopViews() {
        val title = conversation?.title
        val threadTitle = if (!title.isNullOrEmpty()) title else participants.getThreadTitle()

        if (conversation != null) {
            if ((threadTitle == conversation!!.phoneNumber || conversation!!.isCompany) && conversation!!.photoUri == "") {
                val drawable =
                    if (conversation!!.isCompany) SimpleContactsHelper(this).getColoredCompanyIcon(threadTitle)
                    else SimpleContactsHelper(this).getColoredContactIcon(threadTitle)
                binding.topConversationDetails.conversationDetailsImage.setImageDrawable(drawable)
            } else {
                if (!isDestroyed || !isFinishing) {
                    val placeholder = if (participants.size > 1) {
                        SimpleContactsHelper(this).getColoredGroupIcon(threadTitle)
                    } else {
                        null
                    }
                    SimpleContactsHelper(this).loadContactImage(conversation!!.photoUri, binding.topConversationDetails.conversationDetailsImage, threadTitle, placeholder)
                }
            }
        } else {
            if (!isDestroyed || !isFinishing) {
                val placeholder = if (participants.size > 1) {
                    SimpleContactsHelper(this).getColoredGroupIcon(threadTitle)
                } else {
                    null
                }
                SimpleContactsHelper(this).loadContactImage("", binding.topConversationDetails.conversationDetailsImage, threadTitle, placeholder)
            }
        }

        binding.topConversationDetails.conversationDetailsName.apply {
            text = threadTitle
            setTextColor(getProperTextColor())
            setOnClickListener {
                if (conversation != null) {
                    RenameConversationDialog(this@ConversationDetailsActivity, conversation!!) { title ->
                        text = title
                        ensureBackgroundThread {
                            conversation = renameConversation(conversation!!, newTitle = title)
                        }
                    }
                }
            }
            setOnLongClickListener {
                copyToClipboard(threadTitle)
                true
            }
        }
    }

    private fun updateButton() = binding.apply {
        val primaryColor = getProperPrimaryColor()

        oneButton.alpha = if (participants.size == 1 && !isSpecialNumber()) 1f else 0.5f
        oneButton.isClickable = participants.size == 1 && !isSpecialNumber()
        if (participants.size == 1 && !isSpecialNumber()) {
            oneButton.setOnClickListener { dialNumber() }
            oneButton.setOnLongClickListener { toast(com.goodwy.commons.R.string.call); true; }
        }

        if (participants.size == 1) {
            val contact = participants.firstOrNull()
            if (contact != null) {
                val address = contact.phoneNumbers.first().normalizedNumber

                conversationNumberContainer.beVisible()
                conversationNumberContainer.setOnClickListener {
                    copyToClipboard(address)
                }
                conversationNumber.text = address
                conversationNumber.setTextColor(primaryColor)

                getContactFromAddress(address) { simpleContact ->
                    if (simpleContact != null) {
                        runOnUiThread {
                            threeButton.alpha = 1f
                            threeButton.isClickable = true
                            threeButton.setOnClickListener { startContactDetailsIntentRecommendation(simpleContact) }
                            threeButton.setOnLongClickListener { toast(com.goodwy.commons.R.string.contact_details); true; }
                            topConversationDetails.conversationDetailsImage.setOnClickListener { startContactDetailsIntentRecommendation(simpleContact) }

                            val phoneNumber = simpleContact.phoneNumbers.firstOrNull { it.normalizedNumber == address }
                            if (phoneNumber != null) {
                                conversationNumberTypeContainer.beVisible()
                                conversationNumberType.apply {
                                    beVisible()
                                    //text = contact.phoneNumbers.filter { it.normalizedNumber == getCurrentPhoneNumber()}.toString()
                                    val phoneNumberType = phoneNumber.type
                                    val phoneNumberLabel = phoneNumber.label
                                    text = getPhoneNumberTypeText(phoneNumberType, phoneNumberLabel)
                                }
                                conversationFavoriteIcon.apply {
                                    beVisibleIf(phoneNumber.isPrimary)
                                    applyColorFilter(getProperTextColor())
                                }
                            }

                            if (simpleContact.birthdays.firstOrNull() != null) {
                                val monthName = getDateFormatFromDateString(this@ConversationDetailsActivity, simpleContact.birthdays.first(), "yyyy-MM-dd")
                                conversationBirthdaysContainer.beVisible()
                                conversationBirthdaysPress.setOnClickListener {
                                    if (monthName != null) copyToClipboard(monthName)
                                }
                                conversationBirthdaysTitle.apply {
                                    setTextColor(getProperTextColor())
                                }
                                conversationBirthdays.apply {
                                    text = monthName
                                    setTextColor(primaryColor)
                                }
                            }
                        }
                    } else {
//                        threeButton.alpha = 0.5f
//                        threeButton.isClickable = false
                        val drawable = ResourcesCompat.getDrawable(resources, com.goodwy.commons.R.drawable.ic_add_person_vector, theme)?.apply {
                            applyColorFilter(getProperPrimaryColor())
                        }
                        root.post {
                            threeButton.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null)
                            threeButton.setText(com.goodwy.commons.R.string.add_contact)
                            topConversationDetails.conversationDetailsImage.contentDescription = getString(com.goodwy.commons.R.string.add_contact)

                            arrayOf(
                                threeButton, topConversationDetails.conversationDetailsImage
                            ).forEach {
                                it.setOnClickListener {
                                    Intent().apply {
                                        action = Intent.ACTION_INSERT_OR_EDIT
                                        type = "vnd.android.cursor.item/contact"
                                        putExtra(KEY_PHONE, address)
                                        launchActivityIntent(this)
                                    }
                                }
                            }
                            threeButton.setOnLongClickListener { toast(com.goodwy.commons.R.string.add_contact); true; }
                        }
                    }
                }
            } else {
                threeButton.alpha = 0.5f
                threeButton.isClickable = false
            }
        } else {
            threeButton.alpha = 0.5f
            threeButton.isClickable = false
        }

        fourButton.apply {
            setOnClickListener {
                launchShare()
            }
            setOnLongClickListener { toast(com.goodwy.commons.R.string.share); true; }
        }

        val red = resources.getColor(com.goodwy.commons.R.color.red_missed, theme)
        val isBlockNumbers = isBlockNumbers()
        val blockColor = if (isBlockNumbers) { primaryColor } else { red }
        blockButton.setTextColor(blockColor)
        val unblockText = if (participants.size == 1) com.goodwy.strings.R.string.unblock_number else com.goodwy.strings.R.string.unblock_numbers
        val blockText = if (participants.size == 1) com.goodwy.commons.R.string.block_number else com.goodwy.commons.R.string.block_numbers
        blockButton.text = if (isBlockNumbers) { resources.getString(unblockText) } else { resources.getString(blockText)}
        blockButton.setOnClickListener {
            blockNumber()
        }
    }

    private fun launchShare() {
        val numbers = participants.getAddresses()
        val numbersString = TextUtils.join(", ", numbers)
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, numbersString)
            putExtra(Intent.EXTRA_TEXT, numbersString)
            type = "text/plain"
            startActivity(Intent.createChooser(this, getString(com.goodwy.commons.R.string.invite_via)))
        }
    }

    private fun isBlockNumbers(): Boolean {
        return participants.getAddresses().any { isNumberBlocked(it, getBlockedNumbers()) }
    }

    private fun blockNumber() {
        val numbers = participants.getAddresses()
        val numbersString = TextUtils.join(", ", numbers)
        val isBlockNumbers = isBlockNumbers()
        val baseString = if (isBlockNumbers) com.goodwy.strings.R.string.unblock_confirmation else com.goodwy.commons.R.string.block_confirmation
        val question = String.format(resources.getString(baseString), numbersString)

        ConfirmationDialog(this, question) {
            ensureBackgroundThread {
                numbers.forEach {
                    if (isBlockNumbers) {
                        deleteBlockedNumber(it)
                        val blockText = if (participants.size == 1) com.goodwy.commons.R.string.block_number else com.goodwy.commons.R.string.block_numbers
                        binding.blockButton.text = getString(blockText)
                        binding.blockButton.setTextColor(resources.getColor(com.goodwy.commons.R.color.red_missed, theme))
                    } else {
                        addBlockedNumber(it)
                        val unblockText = if (participants.size == 1) com.goodwy.strings.R.string.unblock_number else com.goodwy.strings.R.string.unblock_numbers
                        binding.blockButton.text = getString(unblockText)
                        binding.blockButton.setTextColor(getProperPrimaryColor())
                    }
                }
                refreshMessages()
//                startActivity(Intent(this, MainActivity::class.java))
//                finish()
            }
        }
    }

    private fun setupMenu() {
        val contrastColor = getProperBackgroundColor().getContrastColor()
        val itemColor = if (baseConfig.topAppBarColorIcon) getProperPrimaryColor() else contrastColor
        binding.conversationDetailsToolbar.setNavigationIconTint(itemColor)
        binding.conversationDetailsToolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun isSpecialNumber(): Boolean {
        val addresses = participants.getAddresses()
        return addresses.any { isShortCodeWithLetters(it) }
    }

    private fun dialNumber() {
        val phoneNumber = participants.first().phoneNumbers.first().normalizedNumber
        dialNumber(phoneNumber)
    }
}

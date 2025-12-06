package com.goodwy.smsmessenger.adapters

import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.databinding.ItemContactWithNumberBinding
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.models.SimpleContact
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.smsmessenger.activities.SimpleActivity

class ContactsAdapter(
    activity: SimpleActivity, var contacts: ArrayList<SimpleContact>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {
    private var fontSize = activity.getTextSize()

    override fun getActionMenuId() = 0

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = contacts.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = contacts.getOrNull(position)?.rawId

    override fun getItemKeyPosition(key: Int) = contacts.indexOfFirst { it.rawId == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactWithNumberBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bindView(contact, allowSingleClick = true, allowLongClick = false) { itemView, _ ->
            setupView(itemView, contact)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = contacts.size

    fun updateContacts(newContacts: ArrayList<SimpleContact>) {
        val oldHashCode = contacts.hashCode()
        val newHashCode = newContacts.hashCode()
        if (newHashCode != oldHashCode) {
            contacts = newContacts
            notifyDataSetChanged()
        }
    }

    private fun setupView(view: View, contact: SimpleContact) {
        ItemContactWithNumberBinding.bind(view).apply {
            divider.apply {
                beInvisibleIf(getLastItem() == contact || !baseConfig.useDividers)
                setBackgroundColor(textColor)
            }

            itemContactName.apply {
                text = contact.name
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }

            itemContactNumber.apply {
                text = TextUtils.join(", ", contact.phoneNumbers.map { it.value })
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            itemContactImage.beGoneIf(!baseConfig.showContactThumbnails)
            if (contact.isABusinessContact() && contact.photoUri == "") {
                val drawable = SimpleContactsHelper(activity).getColoredCompanyIcon(contact.name)
                itemContactImage.setImageDrawable(drawable)
            } else {
                SimpleContactsHelper(activity).loadContactImage(contact.photoUri, itemContactImage, contact.name)
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val binding = ItemContactWithNumberBinding.bind(holder.itemView)
            Glide.with(activity).clear(binding.itemContactImage)
        }
    }

    private fun getLastItem() = contacts.last()
}

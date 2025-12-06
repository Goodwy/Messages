package com.goodwy.smsmessenger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import com.goodwy.commons.databinding.ItemContactWithNumberBinding
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.models.SimpleContact
import com.goodwy.smsmessenger.activities.SimpleActivity
import com.goodwy.smsmessenger.extensions.config

class AutoCompleteTextViewAdapter(val activity: SimpleActivity, val contacts: ArrayList<SimpleContact>) : ArrayAdapter<SimpleContact>(activity, 0, contacts) {
    var resultList = ArrayList<SimpleContact>()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val contact = resultList.getOrNull(position)
        var listItem = convertView
        if (listItem == null || listItem.tag != contact?.name?.isNotEmpty()) {
            listItem = ItemContactWithNumberBinding.inflate(LayoutInflater.from(activity), parent, false).root
        }

        listItem.tag = contact?.name?.isNotEmpty()
        ItemContactWithNumberBinding.bind(listItem).apply {
            // clickable and focusable properties seem to break Autocomplete clicking, so remove them
            itemContactFrame.apply {
                isClickable = false
                isFocusable = false
            }

            val backgroundColor = activity.getProperBackgroundColor()
            itemContactFrame.setBackgroundColor(backgroundColor.darkenColor())
            itemContactName.setTextColor(backgroundColor.getContrastColor())
            itemContactNumber.setTextColor(backgroundColor.getContrastColor())

            if (contact != null) {
                itemContactName.text = contact.name
                itemContactNumber.text = contact.phoneNumbers.first().normalizedNumber
                if (contact.isABusinessContact() && contact.photoUri == "") {
                    val drawable =
                        SimpleContactsHelper(activity).getColoredCompanyIcon(contact.name)
                    itemContactImage.setImageDrawable(drawable)
                } else {
                    SimpleContactsHelper(context).loadContactImage(contact.photoUri, itemContactImage, contact.name)
                }
            }
            itemContactImage.beGoneIf(!activity.config.showContactThumbnails)
        }

        return listItem
    }

    override fun getFilter() = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filterResults = FilterResults()
            if (constraint != null) {
                val results = mutableListOf<SimpleContact>()
                val searchString = constraint.toString().normalizeString()
                contacts.forEach {
                    if (it.doesContainPhoneNumber(searchString, true) || it.name.contains(searchString, true)) {
                        results.add(it)
                    }
                }

                results.sortWith(compareBy { !it.name.startsWith(searchString, true) })

                filterResults.values = results
                filterResults.count = results.size
            }
            return filterResults
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            if (results != null && results.count > 0) {
                resultList.clear()
                @Suppress("UNCHECKED_CAST")
                resultList.addAll(results.values as List<SimpleContact>)
                notifyDataSetChanged()
            } else {
                notifyDataSetInvalidated()
            }
        }

        override fun convertResultToString(resultValue: Any?) = (resultValue as? SimpleContact)?.name
    }

    override fun getItem(index: Int) = resultList[index]

    override fun getCount() = resultList.size
}

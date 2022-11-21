package com.goodwy.smsmessenger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.RelativeLayout
import android.widget.TextView
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.models.SimpleContact
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.activities.SimpleActivity
import com.goodwy.smsmessenger.extensions.config

class AutoCompleteTextViewAdapter(val activity: SimpleActivity, val contacts: ArrayList<SimpleContact>) : ArrayAdapter<SimpleContact>(activity, 0, contacts) {
    var resultList = ArrayList<SimpleContact>()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val contact = resultList.getOrNull(position)
        var listItem = convertView
        if (listItem == null || listItem.tag != contact?.name?.isNotEmpty()) {
            listItem = LayoutInflater.from(activity).inflate(R.layout.item_contact_with_number, parent, false)
        }

        listItem!!.apply {
            tag = contact?.name?.isNotEmpty()
            // clickable and focusable properties seem to break Autocomplete clicking, so remove them
            findViewById<View>(R.id.item_contact_frame).apply {
                isClickable = false
                isFocusable = false
            }

            val backgroundColor = activity.getProperBackgroundColor()
            findViewById<RelativeLayout>(R.id.item_contact_holder).setBackgroundColor(backgroundColor.darkenColor())

            findViewById<TextView>(R.id.item_contact_name).setTextColor(backgroundColor.getContrastColor())
            findViewById<TextView>(R.id.item_contact_number).setTextColor(backgroundColor.getContrastColor())

            if (contact != null) {
                findViewById<TextView>(R.id.item_contact_name).text = contact.name
                findViewById<TextView>(R.id.item_contact_number).text = contact.phoneNumbers.first().normalizedNumber
                SimpleContactsHelper(context).loadContactImage(contact.photoUri, findViewById(R.id.item_contact_image), contact.name)
            }
            findViewById<TextView>(R.id.item_contact_image).beGoneIf(!activity.config.showContactThumbnails)
        }

        return listItem
    }

    override fun getFilter() = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filterResults = FilterResults()
            if (constraint != null) {
                resultList.clear()
                val searchString = constraint.toString().normalizeString()
                contacts.forEach {
                    if (it.doesContainPhoneNumber(searchString) || it.name.contains(searchString, true)) {
                        resultList.add(it)
                    }
                }

                resultList.sortWith(compareBy { !it.name.startsWith(searchString, true) })

                filterResults.values = resultList
                filterResults.count = resultList.size
            }
            return filterResults
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            if (results?.count ?: -1 > 0) {
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

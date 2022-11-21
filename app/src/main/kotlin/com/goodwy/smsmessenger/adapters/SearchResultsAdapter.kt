package com.goodwy.smsmessenger.adapters

import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.extensions.applyColorFilter
import com.goodwy.commons.extensions.beGoneIf
import com.goodwy.commons.extensions.getTextSize
import com.goodwy.commons.extensions.highlightTextPart
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.letterBackgroundColors
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.activities.SimpleActivity
import com.goodwy.smsmessenger.extensions.config
import com.goodwy.smsmessenger.models.SearchResult
import kotlinx.android.synthetic.main.item_search_result.view.*
import java.util.*

class SearchResultsAdapter(
    activity: SimpleActivity, var searchResults: ArrayList<SearchResult>, recyclerView: MyRecyclerView, highlightText: String, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private var fontSize = activity.getTextSize()
    private var textToHighlight = highlightText

    override fun getActionMenuId() = 0

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = searchResults.size

    override fun getIsItemSelectable(position: Int) = false

    override fun getItemSelectionKey(position: Int) = searchResults.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = searchResults.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_search_result, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val searchResult = searchResults[position]
        holder.bindView(searchResult, true, false) { itemView, layoutPosition ->
            setupView(itemView, searchResult)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = searchResults.size

    fun updateItems(newItems: ArrayList<SearchResult>, highlightText: String = "") {
        if (newItems.hashCode() != searchResults.hashCode()) {
            searchResults = newItems.clone() as ArrayList<SearchResult>
            textToHighlight = highlightText
            notifyDataSetChanged()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun setupView(view: View, searchResult: SearchResult) {
        view.apply {
            search_result_title.apply {
                text = searchResult.title.highlightTextPart(textToHighlight, properPrimaryColor)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.2f)
            }

            search_result_snippet.apply {
                text = searchResult.snippet.highlightTextPart(textToHighlight, properPrimaryColor)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.9f)
            }

            search_result_date.apply {
                text = searchResult.date
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            findViewById<ImageView>(R.id.search_result_image).beGoneIf(!activity.config.showContactThumbnails)
            //SimpleContactsHelper(context).loadContactImage(searchResult.photoUri, search_result_image, searchResult.title)
            if (searchResult.title == searchResult.phoneNumber) {
                val drawable = resources.getDrawable(R.drawable.placeholder_contact)
                if (baseConfig.useColoredContacts) {
                    val color = letterBackgroundColors[Math.abs(searchResult.phoneNumber.hashCode()) % letterBackgroundColors.size].toInt()
                    (drawable as LayerDrawable).findDrawableByLayerId(R.id.placeholder_contact_background).applyColorFilter(color)
                }
                search_result_image.setImageDrawable(drawable)
            } else {
                SimpleContactsHelper(context).loadContactImage(searchResult.photoUri, search_result_image, searchResult.title)
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing && holder.itemView.search_result_image != null) {
            Glide.with(activity).clear(holder.itemView.search_result_image)
        }
    }
}

package com.goodwy.smsmessenger.adapters

import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.goodwy.commons.compose.extensions.config
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.activities.SimpleActivity
import com.goodwy.smsmessenger.databinding.ItemVcardContactBinding
import com.goodwy.smsmessenger.databinding.ItemVcardContactPropertyBinding
import com.goodwy.smsmessenger.models.VCardPropertyWrapper
import com.goodwy.smsmessenger.models.VCardWrapper
import kotlin.math.abs

private const val TYPE_VCARD_CONTACT = 1
private const val TYPE_VCARD_CONTACT_PROPERTY = 2

class VCardViewerAdapter(
    activity: SimpleActivity, private var items: MutableList<Any>, private val itemClick: (Any) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var fontSize = activity.getTextSize()
    private var textColor = activity.getProperTextColor()
    private val layoutInflater = activity.layoutInflater
    private val showContactThumbnails = activity.config.showContactThumbnails
    private val activity = activity

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int): Int {
        return when (val item = items[position]) {
            is VCardWrapper -> TYPE_VCARD_CONTACT
            is VCardPropertyWrapper -> TYPE_VCARD_CONTACT_PROPERTY
            else -> throw IllegalArgumentException("Unexpected type: ${item::class.simpleName}")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_VCARD_CONTACT -> VCardContactViewHolder(
                binding = ItemVcardContactBinding.inflate(layoutInflater, parent, false)
            )
            TYPE_VCARD_CONTACT_PROPERTY -> VCardPropertyViewHolder(
                binding = ItemVcardContactPropertyBinding.inflate(layoutInflater, parent, false)
            )
            else -> throw IllegalArgumentException("Unexpected type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is VCardContactViewHolder -> holder.bindView(item as VCardWrapper)
            is VCardPropertyViewHolder -> holder.bindView(item as VCardPropertyWrapper)
        }
    }

    inner class VCardContactViewHolder(val binding: ItemVcardContactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindView(item: VCardWrapper) {
            val name = item.fullName
            binding.apply {
                itemContactName.apply {
                    text = name
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.1f)
                }
                itemContactImage.apply {
                    beGoneIf(!showContactThumbnails)
                    val photo = item.vCard.photos.firstOrNull()
                    val placeholder = if (item.isCompany) {
                        val drawable = ResourcesCompat.getDrawable(
                            resources,
                            R.drawable.placeholder_company,
                            activity.theme
                        )
                        if (context.baseConfig.useColoredContacts) {
                            val letterBackgroundColors = activity.getLetterBackgroundColors()
                            val color = letterBackgroundColors[abs(name.hashCode()) % letterBackgroundColors.size].toInt()
                            (drawable as LayerDrawable).findDrawableByLayerId(R.id.placeholder_contact_background).applyColorFilter(color)
                        }
                        drawable
                    } else if (name != null) {
                        SimpleContactsHelper(context).getContactLetterIcon(name).toDrawable(resources)
                    } else {
                        null
                    }

                    val roundingRadius = resources.getDimensionPixelSize(com.goodwy.commons.R.dimen.big_margin)
                    val transformation = RoundedCorners(roundingRadius)
                    val options = RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .placeholder(placeholder)
                        .transform(transformation)
                    Glide.with(this)
                        .load(photo?.data ?: photo?.url)
                        .apply(options)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(this)
                }
                expandCollapseIcon.apply {
                    val expandCollapseDrawable = if (item.expanded) {
                        R.drawable.ic_collapse_up
                    } else {
                        R.drawable.ic_expand_down
                    }
                    setImageResource(expandCollapseDrawable)
                    applyColorFilter(textColor)
                }

                if (items.size > 1) {
                    root.setOnClickListener {
                        expandOrCollapseRow(item)
                    }
                }
                root.onGlobalLayout {
                    if (items.size == 1) {
                        expandOrCollapseRow(item)
                        expandCollapseIcon.beGone()
                    }
                }
            }
        }

        private fun expandOrCollapseRow(item: VCardWrapper) {
            val properties = item.properties
            if (item.expanded) {
                collapseRow(properties, item)
            } else {
                expandRow(properties, item)
            }
        }

        private fun expandRow(properties: List<VCardPropertyWrapper>, vCardWrapper: VCardWrapper) {
            vCardWrapper.expanded = true
            val nextPosition = items.indexOf(vCardWrapper) + 1
            items.addAll(nextPosition, properties)
            notifyItemRangeInserted(nextPosition, properties.size)
            binding.expandCollapseIcon.setImageResource(R.drawable.ic_collapse_up)
        }

        private fun collapseRow(properties: List<VCardPropertyWrapper>, vCardWrapper: VCardWrapper) {
            vCardWrapper.expanded = false
            val nextPosition = items.indexOf(vCardWrapper) + 1
            repeat(properties.size) {
                items.removeAt(nextPosition)
            }
            notifyItemRangeRemoved(nextPosition, properties.size)
            binding.expandCollapseIcon.setImageResource(R.drawable.ic_expand_down)
        }
    }

    inner class VCardPropertyViewHolder(val binding: ItemVcardContactPropertyBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindView(item: VCardPropertyWrapper) {
            binding.apply {
                itemVcardPropertyTitle.apply {
                    text = item.value
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.1f)
                }
                itemVcardPropertySubtitle.apply {
                    text = item.type
                    setTextColor(textColor)
                }
                root.setOnClickListener {
                    itemClick(item)
                }
            }
        }
    }
}

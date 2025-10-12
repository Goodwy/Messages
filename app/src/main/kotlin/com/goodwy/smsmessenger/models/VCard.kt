package com.goodwy.smsmessenger.models

import android.content.Context
import com.goodwy.commons.extensions.normalizePhoneNumber
import com.goodwy.smsmessenger.extensions.config
import com.goodwy.smsmessenger.extensions.format
import com.goodwy.smsmessenger.helpers.isCompanyVCard
import com.goodwy.smsmessenger.helpers.parseNameFromVCard
import ezvcard.VCard
import ezvcard.property.*

private val displayedPropertyClasses = arrayOf(
    Telephone::class.java, Email::class.java, Organization::class.java, Birthday::class.java, Anniversary::class.java, Note::class.java
)

data class VCardWrapper(val vCard: VCard, val fullName: String?, val properties: List<VCardPropertyWrapper>, var expanded: Boolean = false, var isCompany: Boolean = false) {

    companion object {

        fun from(context: Context, vCard: VCard): VCardWrapper {
            val properties = vCard.properties
                .filter { displayedPropertyClasses.contains(it::class.java) }
                .map { VCardPropertyWrapper.from(context, it) }
                .distinctBy { it.value }
            val fullName = vCard.parseNameFromVCard()
            val isCompany = vCard.isCompanyVCard(fullName ?: "")

            return VCardWrapper(vCard, fullName, properties, isCompany = isCompany)
        }
    }
}

data class VCardPropertyWrapper(val value: String, val type: String, val property: VCardProperty) {

    companion object {
        private const val CELL = "CELL"
        private const val HOME = "HOME"
        private const val WORK = "WORK"

        private fun VCardProperty.getPropertyTypeString(context: Context): String {
            return when (parameters.type) {
                CELL -> context.getString(com.goodwy.commons.R.string.mobile)
                HOME -> context.getString(com.goodwy.commons.R.string.home)
                WORK -> context.getString(com.goodwy.commons.R.string.work)
                else -> ""
            }
        }

        fun from(context: Context, property: VCardProperty): VCardPropertyWrapper {
            return property.run {
                when (this) {
                    is Telephone -> VCardPropertyWrapper(text.normalizePhoneNumber(), getPropertyTypeString(context), property)
                    is Email -> VCardPropertyWrapper(value, getPropertyTypeString(context), property)
                    is Organization -> VCardPropertyWrapper(
                        value = values.joinToString(),
                        type = context.getString(com.goodwy.commons.R.string.work),
                        property = property
                    )

                    is Birthday -> VCardPropertyWrapper(
                        value = date.format(context.config.dateFormat),
                        type = context.getString(com.goodwy.commons.R.string.birthday),
                        property = property
                    )

                    is Anniversary -> VCardPropertyWrapper(
                        value = date.format(context.config.dateFormat),
                        type = context.getString(com.goodwy.commons.R.string.anniversary),
                        property = property
                    )

                    is Note -> VCardPropertyWrapper(value, context.getString(com.goodwy.commons.R.string.notes), property)
                    else -> VCardPropertyWrapper("", "", property)
                }
            }
        }
    }
}

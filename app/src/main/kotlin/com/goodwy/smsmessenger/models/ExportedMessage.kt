package com.goodwy.smsmessenger.models

import com.google.gson.annotations.SerializedName

data class ExportedMessage(
    @SerializedName("sms")
    val sms: List<SmsBackup>?,
    @SerializedName("mms")
    val mms: List<MmsBackup>?,
)

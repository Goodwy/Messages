package com.goodwy.smsmessenger.interfaces

import androidx.room.Dao
import androidx.room.Query
import com.goodwy.smsmessenger.models.MessageAttachment

@Dao
interface MessageAttachmentsDao {
    @Query("SELECT * FROM message_attachments")
    fun getAll(): List<MessageAttachment>
}

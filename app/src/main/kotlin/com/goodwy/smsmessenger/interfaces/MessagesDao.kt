package com.goodwy.smsmessenger.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.goodwy.smsmessenger.models.Message

@Dao
interface MessagesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(message: Message)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOrIgnore(message: Message): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessages(vararg message: Message)

    @Query("SELECT * FROM messages")
    fun getAll(): List<Message>

    @Query("SELECT * FROM messages WHERE thread_id = :threadId")
    fun getThreadMessages(threadId: Long): List<Message>

    @Query("SELECT * FROM messages WHERE body LIKE :text")
    fun getMessagesWithText(text: String): List<Message>

    @Query("UPDATE messages SET read = 1 WHERE id = :id")
    fun markRead(id: Long)

    @Query("UPDATE messages SET read = 1 WHERE thread_id = :threadId")
    fun markThreadRead(threadId: Long)

    @Query("UPDATE messages SET type = :type WHERE id = :id")
    fun updateType(id: Long, type: Int): Int

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    fun updateStatus(id: Long, status: Int): Int

    @Query("DELETE FROM messages WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM messages WHERE thread_id = :threadId")
    fun deleteThreadMessages(threadId: Long)

    @Query("DELETE FROM messages")
    fun deleteAll()
}

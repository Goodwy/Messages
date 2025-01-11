package com.goodwy.smsmessenger.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.goodwy.smsmessenger.models.Draft

@Dao
interface DraftsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(draft: Draft): Long

    @Query("SELECT * FROM drafts")
    fun getAll(): List<Draft>

    @Query("SELECT * FROM drafts WHERE thread_id = :threadId")
    fun getDraftById(threadId: Long): Draft?

    @Query("DELETE FROM drafts WHERE thread_id = :threadId")
    fun delete(threadId: Long): Int
}

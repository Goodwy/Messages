package com.goodwy.smsmessenger.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.goodwy.smsmessenger.helpers.Converters
import com.goodwy.smsmessenger.interfaces.AttachmentsDao
import com.goodwy.smsmessenger.interfaces.ConversationsDao
import com.goodwy.smsmessenger.interfaces.MessageAttachmentsDao
import com.goodwy.smsmessenger.interfaces.MessagesDao
import com.goodwy.smsmessenger.models.Attachment
import com.goodwy.smsmessenger.models.Conversation
import com.goodwy.smsmessenger.models.Message
import com.goodwy.smsmessenger.models.MessageAttachment

@Database(entities = [Conversation::class, Attachment::class, MessageAttachment::class, Message::class], version = 4)
@TypeConverters(Converters::class)
abstract class MessagesDatabase : RoomDatabase() {

    abstract fun ConversationsDao(): ConversationsDao

    abstract fun AttachmentsDao(): AttachmentsDao

    abstract fun MessageAttachmentsDao(): MessageAttachmentsDao

    abstract fun MessagesDao(): MessagesDao

    companion object {
        private var db: MessagesDatabase? = null

        fun getInstance(context: Context): MessagesDatabase {
            if (db == null) {
                synchronized(MessagesDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, MessagesDatabase::class.java, "conversations.db")
                            .fallbackToDestructiveMigration()
                            .addMigrations(MIGRATION_1_2)
                            .addMigrations(MIGRATION_2_3)
                            .addMigrations(MIGRATION_3_4)
                            .build()
                    }
                }
            }
            return db!!
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY NOT NULL, `body` TEXT NOT NULL, `type` INTEGER NOT NULL, `participants` TEXT NOT NULL, `date` INTEGER NOT NULL, `read` INTEGER NOT NULL, `thread_id` INTEGER NOT NULL, `is_mms` INTEGER NOT NULL, `attachment` TEXT, `sender_name` TEXT NOT NULL, `sender_photo_uri` TEXT NOT NULL, `subscription_id` INTEGER NOT NULL)")

                    execSQL("CREATE TABLE IF NOT EXISTS `message_attachments` (`id` INTEGER PRIMARY KEY NOT NULL, `text` TEXT NOT NULL, `attachments` TEXT NOT NULL)")

                    execSQL("CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `message_id` INTEGER NOT NULL, `uri_string` TEXT NOT NULL, `mimetype` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `filename` TEXT NOT NULL)")
                    execSQL("CREATE UNIQUE INDEX `index_attachments_message_id` ON `attachments` (`message_id`)")
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL("CREATE TABLE conversations_new (`thread_id` INTEGER NOT NULL PRIMARY KEY, `snippet` TEXT NOT NULL, `date` INTEGER NOT NULL, `read` INTEGER NOT NULL, `title` TEXT NOT NULL, `photo_uri` TEXT NOT NULL, `is_group_conversation` INTEGER NOT NULL, `phone_number` TEXT NOT NULL)")

                    execSQL("INSERT OR IGNORE INTO conversations_new (thread_id, snippet, date, read, title, photo_uri, is_group_conversation, phone_number) " +
                            "SELECT thread_id, snippet, date, read, title, photo_uri, is_group_conversation, phone_number FROM conversations")

                    execSQL("DROP TABLE conversations")

                    execSQL("ALTER TABLE conversations_new RENAME TO conversations")

                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_conversations_id` ON `conversations` (`thread_id`)")
                }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL("ALTER TABLE messages ADD COLUMN status INTEGER NOT NULL DEFAULT -1")
                }
            }
        }
    }
}

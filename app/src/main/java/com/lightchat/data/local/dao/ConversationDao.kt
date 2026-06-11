package com.lightchat.data.local.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.lightchat.data.local.DatabaseHelper
import com.lightchat.model.Conversation
import com.lightchat.model.ConversationType

class ConversationDao(private val dbHelper: DatabaseHelper) {

    fun insert(conversation: Conversation): Long {
        val db = dbHelper.writableDatabase
        return db.insertWithOnConflict("conversation", null, conversation.toContentValues(dbHelper.currentOwnerId()), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertAll(conversations: List<Conversation>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (conv in conversations) {
                db.insertWithOnConflict("conversation", null, conv.toContentValues(dbHelper.currentOwnerId()), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getById(conversationId: String): Conversation? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "conversation", null,
            "owner_user_id = ? AND conversation_id = ?",
            arrayOf(dbHelper.currentOwnerId(), conversationId),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) it.toConversation() else null
        }
    }

    fun getAllVisible(): List<Conversation> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "conversation", null,
            "owner_user_id = ? AND is_hidden = 0 AND is_deleted = 0",
            arrayOf(dbHelper.currentOwnerId()), null, null,
            "is_pinned DESC, last_message_time DESC"
        )
        val conversations = mutableListOf<Conversation>()
        cursor.use {
            while (it.moveToNext()) {
                conversations.add(it.toConversation())
            }
        }
        return conversations
    }

    fun getVisiblePage(limit: Int = 30, offset: Int = 0): List<Conversation> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "conversation", null,
            "owner_user_id = ? AND is_hidden = 0 AND is_deleted = 0",
            arrayOf(dbHelper.currentOwnerId()), null, null,
            "is_pinned DESC, last_message_time DESC",
            "$offset, $limit"
        )
        val conversations = mutableListOf<Conversation>()
        cursor.use {
            while (it.moveToNext()) {
                conversations.add(it.toConversation())
            }
        }
        return conversations
    }

    fun getVisibleCount(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM conversation WHERE owner_user_id = ? AND is_hidden = 0 AND is_deleted = 0",
            arrayOf(dbHelper.currentOwnerId())
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun getTotalUnreadCount(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
                SELECT COALESCE(SUM(unread_count), 0)
                FROM conversation
                WHERE owner_user_id = ? AND is_hidden = 0 AND is_deleted = 0 AND mute = 0
            """.trimIndent(),
            arrayOf(dbHelper.currentOwnerId())
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun updateLastMessage(conversationId: String, messageId: String, content: String, time: Long, thumbnail: String? = null): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("last_message_id", messageId)
            put("last_message_content", content)
            put("last_message_time", time)
            put("last_thumbnail", thumbnail ?: "")
            put("is_hidden", 0)
        }
        return db.update("conversation", cv, "owner_user_id = ? AND conversation_id = ?", arrayOf(dbHelper.currentOwnerId(), conversationId))
    }

    fun updateUnreadCount(conversationId: String, count: Int): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("unread_count", count) }
        return db.update("conversation", cv, "owner_user_id = ? AND conversation_id = ?", arrayOf(dbHelper.currentOwnerId(), conversationId))
    }

    fun incrementUnread(conversationId: String): Int {
        val db = dbHelper.writableDatabase
        val owner = dbHelper.currentOwnerId()
        db.execSQL("UPDATE conversation SET unread_count = unread_count + 1 WHERE owner_user_id = ? AND conversation_id = ?", arrayOf(owner, conversationId))
        val cursor = db.rawQuery("SELECT unread_count FROM conversation WHERE owner_user_id = ? AND conversation_id = ?", arrayOf(owner, conversationId))
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun clearUnread(conversationId: String): Int {
        return updateUnreadCount(conversationId, 0)
    }

    fun setPinned(conversationId: String, pinned: Boolean, time: Long = System.currentTimeMillis()): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("is_pinned", if (pinned) 1 else 0)
            put("pinned_time", if (pinned) time else 0)
        }
        return db.update("conversation", cv, "owner_user_id = ? AND conversation_id = ?", arrayOf(dbHelper.currentOwnerId(), conversationId))
    }

    fun setHidden(conversationId: String, hidden: Boolean): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("is_hidden", if (hidden) 1 else 0) }
        return db.update("conversation", cv, "owner_user_id = ? AND conversation_id = ?", arrayOf(dbHelper.currentOwnerId(), conversationId))
    }

    fun setDeleted(conversationId: String, deleted: Boolean): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("is_deleted", if (deleted) 1 else 0) }
        return db.update("conversation", cv, "owner_user_id = ? AND conversation_id = ?", arrayOf(dbHelper.currentOwnerId(), conversationId))
    }

    fun setMute(conversationId: String, mute: Boolean): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("mute", if (mute) 1 else 0) }
        return db.update("conversation", cv, "owner_user_id = ? AND conversation_id = ?", arrayOf(dbHelper.currentOwnerId(), conversationId))
    }

    fun getAllForSearch(): List<Conversation> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "conversation", null,
            "owner_user_id = ? AND is_deleted = 0",
            arrayOf(dbHelper.currentOwnerId()), null, null,
            "last_message_time DESC"
        )
        val conversations = mutableListOf<Conversation>()
        cursor.use {
            while (it.moveToNext()) {
                conversations.add(it.toConversation())
            }
        }
        return conversations
    }

    fun setAtMe(conversationId: String, atMe: Boolean): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("at_me", if (atMe) 1 else 0)
            if (!atMe) put("at_me_count", 0)
        }
        return db.update("conversation", cv, "owner_user_id = ? AND conversation_id = ?", arrayOf(dbHelper.currentOwnerId(), conversationId))
    }

    fun incrementAtMe(conversationId: String): Int {
        val db = dbHelper.writableDatabase
        val owner = dbHelper.currentOwnerId()
        db.execSQL(
            "UPDATE conversation SET at_me = 1, at_me_count = at_me_count + 1 WHERE owner_user_id = ? AND conversation_id = ?",
            arrayOf(owner, conversationId)
        )
        val cursor = db.rawQuery(
            "SELECT at_me_count FROM conversation WHERE owner_user_id = ? AND conversation_id = ?",
            arrayOf(owner, conversationId)
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun updateDisplayInfo(conversationId: String, title: String, avatar: String, avatarUrl: String = "", avatarVersion: Int = 0): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("title", title)
            put("avatar", avatar)
            put("avatar_url", avatarUrl)
            put("avatar_version", avatarVersion)
        }
        return db.update("conversation", cv, "owner_user_id = ? AND conversation_id = ?", arrayOf(dbHelper.currentOwnerId(), conversationId))
    }

    fun updateSingleDisplayInfo(conversationId: String, targetId: String, title: String, avatar: String, avatarUrl: String = "", avatarVersion: Int = 0): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("target_id", targetId)
            put("title", title)
            put("avatar", avatar)
            put("avatar_url", avatarUrl)
            put("avatar_version", avatarVersion)
        }
        return db.update("conversation", cv, "owner_user_id = ? AND conversation_id = ?", arrayOf(dbHelper.currentOwnerId(), conversationId))
    }

    companion object {
        fun Conversation.toContentValues(ownerUserId: String) = ContentValues().apply {
            put("owner_user_id", ownerUserId)
            put("conversation_id", conversationId)
            put("type", type.value)
            put("target_id", targetId)
            put("title", title)
            put("avatar", avatar)
            put("avatar_url", avatarUrl)
            put("avatar_version", avatarVersion)
            put("last_message_id", lastMessageId)
            put("last_message_content", lastMessageContent)
            put("last_message_time", lastMessageTime)
            put("last_thumbnail", lastMessageThumbnail ?: "")
            put("unread_count", unreadCount)
            put("at_me", if (atMe) 1 else 0)
            put("at_me_count", atMeCount)
            put("is_pinned", if (isPinned) 1 else 0)
            put("pinned_time", pinnedTime)
            put("is_hidden", if (isHidden) 1 else 0)
            put("is_deleted", if (isDeleted) 1 else 0)
            put("mute", if (mute) 1 else 0)
            put("manual_unread", if (manualUnread) 1 else 0)
        }

        fun android.database.Cursor.toConversation() = Conversation(
            conversationId = getString(getColumnIndexOrThrow("conversation_id")),
            type = ConversationType.fromInt(getInt(getColumnIndexOrThrow("type"))),
            targetId = getString(getColumnIndexOrThrow("target_id")),
            title = getString(getColumnIndexOrThrow("title")) ?: "",
            avatar = getString(getColumnIndexOrThrow("avatar")) ?: "",
            avatarUrl = getString(getColumnIndexOrThrow("avatar_url")) ?: "",
            avatarVersion = getInt(getColumnIndexOrThrow("avatar_version")),
            lastMessageId = getString(getColumnIndexOrThrow("last_message_id")),
            lastMessageContent = getString(getColumnIndexOrThrow("last_message_content")) ?: "",
            lastMessageTime = getLong(getColumnIndexOrThrow("last_message_time")),
            lastMessageThumbnail = getString(getColumnIndexOrThrow("last_thumbnail"))?.takeIf { it.isNotBlank() },
            unreadCount = getInt(getColumnIndexOrThrow("unread_count")),
            atMe = getInt(getColumnIndexOrThrow("at_me")) == 1,
            atMeCount = getInt(getColumnIndexOrThrow("at_me_count")),
            isPinned = getInt(getColumnIndexOrThrow("is_pinned")) == 1,
            pinnedTime = getLong(getColumnIndexOrThrow("pinned_time")),
            isHidden = getInt(getColumnIndexOrThrow("is_hidden")) == 1,
            isDeleted = getInt(getColumnIndexOrThrow("is_deleted")) == 1,
            mute = getInt(getColumnIndexOrThrow("mute")) == 1,
            manualUnread = getInt(getColumnIndexOrThrow("manual_unread")) == 1
        )
    }
}

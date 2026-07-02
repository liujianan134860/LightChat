package com.lightchat.data.local.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.lightchat.data.local.DatabaseHelper
import com.lightchat.model.Message
import com.lightchat.model.MessageStatus
import com.lightchat.model.MessageType

class MessageDao(private val dbHelper: DatabaseHelper) {

    fun insert(message: Message): Long {
        val db = dbHelper.writableDatabase
        return db.insertWithOnConflict("message", null, message.toContentValues(dbHelper.currentOwnerId()), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertAll(messages: List<Message>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (msg in messages) {
                db.insertWithOnConflict("message", null, msg.toContentValues(dbHelper.currentOwnerId()), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getById(messageId: String): Message? {
        val db = dbHelper.readableDatabase
        val cursor = db.query("message", null, "owner_user_id = ? AND message_id = ?", arrayOf(dbHelper.currentOwnerId(), messageId), null, null, null)
        return cursor.use {
            if (it.moveToFirst()) it.toMessage() else null
        }
    }

    fun updateStatus(messageId: String, status: MessageStatus): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("status", status.value) }
        return db.update("message", cv, "owner_user_id = ? AND message_id = ?", arrayOf(dbHelper.currentOwnerId(), messageId))
    }

    fun updateExtra(messageId: String, extra: String): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("extra", extra) }
        return db.update("message", cv, "owner_user_id = ? AND message_id = ?", arrayOf(dbHelper.currentOwnerId(), messageId))
    }

    fun updateStatusAndConversationSeq(messageId: String, status: MessageStatus, conversationSeq: Long): Int {
        val db = dbHelper.writableDatabase
        val currentStatus = getById(messageId)?.status
        val resolvedStatus = when {
            status == MessageStatus.FAILED -> status
            currentStatus == MessageStatus.FAILED -> currentStatus
            currentStatus != null && currentStatus.value > status.value -> currentStatus
            else -> status
        }
        val cv = ContentValues().apply {
            put("status", resolvedStatus.value)
            if (conversationSeq > 0) put("conversation_seq", conversationSeq)
        }
        return db.update("message", cv, "owner_user_id = ? AND message_id = ?", arrayOf(dbHelper.currentOwnerId(), messageId))
    }

    fun getMessagesByConversation(
        conversationId: String,
        limit: Int = 20,
        offset: Int = 0
    ): List<Message> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "message", null,
            "owner_user_id = ? AND conversation_id = ? AND is_deleted = 0",
            arrayOf(dbHelper.currentOwnerId(), conversationId),
            null, null,
            MESSAGE_ORDER_DESC,
            "$offset, $limit"
        )
        val messages = mutableListOf<Message>()
        cursor.use {
            while (it.moveToNext()) {
                messages.add(it.toMessage())
            }
        }
        return messages.reversed()
    }

    fun getLatestMessages(conversationId: String, limit: Int = 20): List<Message> {
        return getMessagesByConversation(conversationId, limit, 0)
    }

    fun getMessagesBefore(
        conversationId: String,
        beforeCreateTime: Long,
        limit: Int = 20
    ): List<Message> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "message", null,
            "owner_user_id = ? AND conversation_id = ? AND create_time < ? AND is_deleted = 0",
            arrayOf(dbHelper.currentOwnerId(), conversationId, beforeCreateTime.toString()),
            null, null,
            MESSAGE_ORDER_DESC,
            "$limit"
        )
        val messages = mutableListOf<Message>()
        cursor.use {
            while (it.moveToNext()) {
                messages.add(it.toMessage())
            }
        }
        return messages.reversed()
    }

    fun getMessagesBeforeSeq(
        conversationId: String,
        beforeConversationSeq: Long,
        limit: Int = 80
    ): List<Message> {
        if (beforeConversationSeq <= 0L) return emptyList()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
                SELECT * FROM message
                WHERE owner_user_id = ?
                  AND conversation_id = ?
                  AND conversation_seq > 0
                  AND conversation_seq < ?
                  AND is_deleted = 0
                ORDER BY conversation_seq DESC, create_time DESC, client_seq DESC
                LIMIT ?
            """.trimIndent(),
            arrayOf(
                dbHelper.currentOwnerId(),
                conversationId,
                beforeConversationSeq.toString(),
                limit.toString()
            )
        )
        val messages = mutableListOf<Message>()
        cursor.use {
            while (it.moveToNext()) {
                messages.add(it.toMessage())
            }
        }
        return messages.reversed()
    }

    fun getMessagesAfter(
        conversationId: String,
        afterMessage: Message,
        limit: Int = 80
    ): List<Message> {
        val db = dbHelper.readableDatabase
        val owner = dbHelper.currentOwnerId()
        val (whereClause, args) = if (afterMessage.conversationSeq > 0L) {
            """
                owner_user_id = ?
                AND conversation_id = ?
                AND conversation_seq > 0
                AND conversation_seq > ?
                AND is_deleted = 0
            """.trimIndent() to arrayOf(owner, conversationId, afterMessage.conversationSeq.toString(), limit.toString())
        } else {
            """
                owner_user_id = ?
                AND conversation_id = ?
                AND is_deleted = 0
                AND (
                    create_time > ?
                    OR (create_time = ? AND client_seq > ?)
                )
            """.trimIndent() to arrayOf(
                owner,
                conversationId,
                afterMessage.createTime.toString(),
                afterMessage.createTime.toString(),
                afterMessage.clientSeq.toString(),
                limit.toString()
            )
        }
        val cursor = db.rawQuery(
            """
                SELECT * FROM message
                WHERE $whereClause
                ORDER BY conversation_seq ASC, create_time ASC, client_seq ASC
                LIMIT ?
            """.trimIndent(),
            args
        )
        val messages = mutableListOf<Message>()
        cursor.use {
            while (it.moveToNext()) messages.add(it.toMessage())
        }
        return messages
    }

    fun hasMessagesBefore(conversationId: String, message: Message): Boolean {
        val db = dbHelper.readableDatabase
        val owner = dbHelper.currentOwnerId()
        val (whereClause, args) = if (message.conversationSeq > 0L) {
            """
                owner_user_id = ?
                AND conversation_id = ?
                AND is_deleted = 0
                AND conversation_seq > 0
                AND conversation_seq < ?
            """.trimIndent() to arrayOf(owner, conversationId, message.conversationSeq.toString())
        } else {
            """
                owner_user_id = ?
                AND conversation_id = ?
                AND is_deleted = 0
                AND (
                    create_time < ?
                    OR (create_time = ? AND client_seq < ?)
                )
            """.trimIndent() to arrayOf(
                owner,
                conversationId,
                message.createTime.toString(),
                message.createTime.toString(),
                message.clientSeq.toString()
            )
        }
        return existsByWhere(db, whereClause, args)
    }

    fun hasMessagesAfter(conversationId: String, message: Message): Boolean {
        val db = dbHelper.readableDatabase
        val owner = dbHelper.currentOwnerId()
        val (whereClause, args) = if (message.conversationSeq > 0L) {
            """
                owner_user_id = ?
                AND conversation_id = ?
                AND is_deleted = 0
                AND conversation_seq > 0
                AND conversation_seq > ?
            """.trimIndent() to arrayOf(owner, conversationId, message.conversationSeq.toString())
        } else {
            """
                owner_user_id = ?
                AND conversation_id = ?
                AND is_deleted = 0
                AND (
                    create_time > ?
                    OR (create_time = ? AND client_seq > ?)
                )
            """.trimIndent() to arrayOf(
                owner,
                conversationId,
                message.createTime.toString(),
                message.createTime.toString(),
                message.clientSeq.toString()
            )
        }
        return existsByWhere(db, whereClause, args)
    }

    private fun existsByWhere(db: SQLiteDatabase, whereClause: String, args: Array<String>): Boolean {
        val cursor = db.rawQuery(
            "SELECT 1 FROM message WHERE $whereClause LIMIT 1",
            args
        )
        return cursor.use { it.moveToFirst() }
    }

    fun getMaxConversationSeq(conversationId: String): Long {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT MAX(conversation_seq) FROM message WHERE owner_user_id = ? AND conversation_id = ?",
            arrayOf(dbHelper.currentOwnerId(), conversationId)
        )
        return cursor.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    fun delete(messageId: String): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("is_deleted", 1) }
        return db.update("message", cv, "owner_user_id = ? AND message_id = ?", arrayOf(dbHelper.currentOwnerId(), messageId))
    }

    fun deleteByConversation(conversationId: String): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("is_deleted", 1) }
        return db.update("message", cv, "owner_user_id = ? AND conversation_id = ?", arrayOf(dbHelper.currentOwnerId(), conversationId))
    }

    fun getMessageCount(conversationId: String): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM message WHERE owner_user_id = ? AND conversation_id = ? AND is_deleted = 0",
            arrayOf(dbHelper.currentOwnerId(), conversationId)
        )
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun getMessagesByStatus(conversationId: String, status: Int): List<Message> {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM message WHERE owner_user_id = ? AND conversation_id = ? AND status = ? AND is_deleted = 0",
            arrayOf(dbHelper.currentOwnerId(), conversationId, status.toString())
        )
        return cursor.use {
            val list = mutableListOf<Message>()
            while (it.moveToNext()) {
                list.add(it.toMessage())
            }
            list
        }
    }

    fun searchMessages(conversationId: String, query: String, limit: Int = 50): List<Message> {
        val db = dbHelper.readableDatabase
        val includeImages = shouldIncludeImageResults(query)
        val selection = if (includeImages) {
            "owner_user_id = ? AND conversation_id = ? AND is_deleted = 0 AND ((message_type != ? AND content LIKE ?) OR message_type = ?)"
        } else {
            "owner_user_id = ? AND conversation_id = ? AND is_deleted = 0 AND message_type != ? AND content LIKE ?"
        }
        val args = if (includeImages) {
            arrayOf(
                dbHelper.currentOwnerId(),
                conversationId,
                MessageType.IMAGE.value.toString(),
                "%$query%",
                MessageType.IMAGE.value.toString()
            )
        } else {
            arrayOf(
                dbHelper.currentOwnerId(),
                conversationId,
                MessageType.IMAGE.value.toString(),
                "%$query%"
            )
        }
        val cursor = db.query(
            "message", null,
            selection,
            args,
            null, null,
            MESSAGE_ORDER_DESC,
            "$limit"
        )
        val messages = mutableListOf<Message>()
        cursor.use {
            while (it.moveToNext()) {
                messages.add(it.toMessage())
            }
        }
        return messages.reversed()
    }

    fun searchMessagesGlobal(query: String, limit: Int = 50): List<Message> {
        val db = dbHelper.readableDatabase
        val includeImages = shouldIncludeImageResults(query)
        val selection = if (includeImages) {
            "owner_user_id = ? AND is_deleted = 0 AND ((message_type != ? AND content LIKE ?) OR message_type = ?)"
        } else {
            "owner_user_id = ? AND is_deleted = 0 AND message_type != ? AND content LIKE ?"
        }
        val args = if (includeImages) {
            arrayOf(
                dbHelper.currentOwnerId(),
                MessageType.IMAGE.value.toString(),
                "%$query%",
                MessageType.IMAGE.value.toString()
            )
        } else {
            arrayOf(
                dbHelper.currentOwnerId(),
                MessageType.IMAGE.value.toString(),
                "%$query%"
            )
        }
        val cursor = db.query(
            "message", null,
            selection,
            args,
            null, null,
            MESSAGE_ORDER_DESC,
            "$limit"
        )
        val messages = mutableListOf<Message>()
        cursor.use {
            while (it.moveToNext()) {
                messages.add(it.toMessage())
            }
        }
        return messages
    }

    private fun shouldIncludeImageResults(query: String): Boolean {
        val normalized = query.trim()
        return normalized.isNotEmpty() && "[图片]".contains(normalized, ignoreCase = true)
    }

    fun getMessagesAround(conversationId: String, messageId: String, beforeLimit: Int = 60, afterLimit: Int = 60): List<Message> {
        val target = getById(messageId) ?: return getLatestMessages(conversationId)
        if (target.conversationId != conversationId) return getLatestMessages(conversationId)
        if (target.conversationSeq <= 0L) {
            return getMessagesAroundCreateTime(conversationId, target, beforeLimit, afterLimit)
        }
        val db = dbHelper.readableDatabase
        val owner = dbHelper.currentOwnerId()
        val beforeCursor = db.rawQuery(
            """
                SELECT * FROM message
                WHERE owner_user_id = ?
                  AND conversation_id = ?
                  AND is_deleted = 0
                  AND conversation_seq > 0
                  AND conversation_seq < ?
                ORDER BY conversation_seq DESC, create_time DESC, client_seq DESC
                LIMIT ?
            """.trimIndent(),
            arrayOf(owner, conversationId, target.conversationSeq.toString(), beforeLimit.toString())
        )
        val before = mutableListOf<Message>()
        beforeCursor.use {
            while (it.moveToNext()) before.add(it.toMessage())
        }
        val afterCursor = db.rawQuery(
            """
                SELECT * FROM message
                WHERE owner_user_id = ?
                  AND conversation_id = ?
                  AND is_deleted = 0
                  AND conversation_seq > 0
                  AND conversation_seq > ?
                ORDER BY conversation_seq ASC, create_time ASC, client_seq ASC
                LIMIT ?
            """.trimIndent(),
            arrayOf(owner, conversationId, target.conversationSeq.toString(), afterLimit.toString())
        )
        val after = mutableListOf<Message>()
        afterCursor.use {
            while (it.moveToNext()) after.add(it.toMessage())
        }
        return (before.reversed() + target + after).distinctBy { it.messageId }
    }

    private fun getMessagesAroundCreateTime(
        conversationId: String,
        target: Message,
        beforeLimit: Int,
        afterLimit: Int
    ): List<Message> {
        val db = dbHelper.readableDatabase
        val owner = dbHelper.currentOwnerId()
        val beforeCursor = db.rawQuery(
            """
                SELECT * FROM message
                WHERE owner_user_id = ?
                  AND conversation_id = ?
                  AND is_deleted = 0
                  AND (
                    create_time < ?
                    OR (create_time = ? AND client_seq < ?)
                  )
                ORDER BY create_time DESC, client_seq DESC
                LIMIT ?
            """.trimIndent(),
            arrayOf(
                owner,
                conversationId,
                target.createTime.toString(),
                target.createTime.toString(),
                target.clientSeq.toString(),
                beforeLimit.toString()
            )
        )
        val before = mutableListOf<Message>()
        beforeCursor.use {
            while (it.moveToNext()) before.add(it.toMessage())
        }

        val afterCursor = db.rawQuery(
            """
                SELECT * FROM message
                WHERE owner_user_id = ?
                  AND conversation_id = ?
                  AND is_deleted = 0
                  AND (
                    create_time > ?
                    OR (create_time = ? AND client_seq > ?)
                  )
                ORDER BY create_time ASC, client_seq ASC
                LIMIT ?
            """.trimIndent(),
            arrayOf(
                owner,
                conversationId,
                target.createTime.toString(),
                target.createTime.toString(),
                target.clientSeq.toString(),
                afterLimit.toString()
            )
        )
        val after = mutableListOf<Message>()
        afterCursor.use {
            while (it.moveToNext()) after.add(it.toMessage())
        }
        return (before.reversed() + target + after).distinctBy { it.messageId }
    }

    fun findLatestMentionForUser(conversationId: String, userId: String, limit: Int = 200): Message? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "message",
            null,
            "owner_user_id = ? AND conversation_id = ? AND sender_id != ? AND is_deleted = 0 AND is_recalled = 0 AND extra IS NOT NULL",
            arrayOf(dbHelper.currentOwnerId(), conversationId, userId),
            null,
            null,
            MESSAGE_ORDER_DESC,
            "$limit"
        )
        cursor.use {
            while (it.moveToNext()) {
                val message = it.toMessage()
                if (mentionsUser(message.extra, userId)) return message
            }
        }
        return null
    }

    fun findFirstUnreadMentionForUser(conversationId: String, userId: String, mentionUnreadCount: Int): Message? {
        if (mentionUnreadCount <= 0) return findLatestMentionForUser(conversationId, userId)
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "message",
            null,
            "owner_user_id = ? AND conversation_id = ? AND sender_id != ? AND is_deleted = 0 AND is_recalled = 0 AND extra IS NOT NULL",
            arrayOf(dbHelper.currentOwnerId(), conversationId, userId),
            null,
            null,
            MESSAGE_ORDER_DESC,
            (mentionUnreadCount * 8).coerceAtLeast(50).coerceAtMost(500).toString()
        )
        val unreadMentions = mutableListOf<Message>()
        cursor.use {
            while (it.moveToNext() && unreadMentions.size < mentionUnreadCount) {
                val message = it.toMessage()
                if (mentionsUser(message.extra, userId)) {
                    unreadMentions.add(message)
                }
            }
        }
        return unreadMentions.reversed().firstOrNull() ?: findLatestMentionForUser(conversationId, userId)
    }

    private fun mentionsUser(extra: String?, userId: String): Boolean {
        return try {
            val ids = org.json.JSONObject(extra ?: "{}").optJSONArray("atUserIds") ?: return false
            (0 until ids.length()).any { ids.optString(it) == userId }
        } catch (_: Exception) {
            false
        }
    }

    fun markConversationMessagesRead(
        conversationId: String,
        senderId: String,
        maxConversationSeq: Long
    ): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("status", MessageStatus.READ.value) }
        return db.update(
            "message",
            cv,
            "owner_user_id = ? AND conversation_id = ? AND sender_id = ? AND conversation_seq <= ? AND is_deleted = 0",
            arrayOf(dbHelper.currentOwnerId(), conversationId, senderId, maxConversationSeq.toString())
        )
    }

    fun markLastSentMessageRead(
        conversationId: String,
        senderId: String,
        maxConversationSeq: Long
    ): Int {
        val db = dbHelper.writableDatabase
        val cursor = db.rawQuery(
            """
                SELECT message_id FROM message
                WHERE owner_user_id = ?
                  AND conversation_id = ?
                  AND sender_id = ?
                  AND (
                    conversation_seq <= ?
                    OR conversation_seq = 0
                  )
                  AND is_deleted = 0
                ORDER BY conversation_seq DESC, create_time DESC
                LIMIT 1
            """.trimIndent(),
            arrayOf(dbHelper.currentOwnerId(), conversationId, senderId, maxConversationSeq.toString())
        )
        val messageId = cursor.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: return 0
        return updateStatus(messageId, MessageStatus.READ)
    }

    fun getLatestSentMessage(conversationId: String, senderId: String): Message? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
                SELECT * FROM message
                WHERE owner_user_id = ?
                  AND conversation_id = ?
                  AND sender_id = ?
                  AND is_deleted = 0
                ORDER BY create_time DESC
                LIMIT 1
            """.trimIndent(),
            arrayOf(dbHelper.currentOwnerId(), conversationId, senderId)
        )
        return cursor.use {
            if (it.moveToFirst()) it.toMessage() else null
        }
    }

    companion object {
        private const val MESSAGE_ORDER_DESC =
            "create_time DESC, " +
                "CASE WHEN conversation_seq > 0 THEN conversation_seq ELSE 0 END DESC, " +
                "client_seq DESC"

        fun Message.toContentValues(ownerUserId: String) = ContentValues().apply {
            put("owner_user_id", ownerUserId)
            put("message_id", messageId)
            put("conversation_id", conversationId)
            put("sender_id", senderId)
            put("receiver_id", receiverId)
            put("group_id", groupId)
            put("message_type", messageType.value)
            put("content", content)
            put("status", status.value)
            put("client_seq", clientSeq)
            put("conversation_seq", conversationSeq)
            put("user_seq", userSeq)
            put("send_time", sendTime)
            put("create_time", createTime)
            put("quote_message_id", quoteMessageId)
            put("original_message_id", originalMessageId)
            put("is_deleted", if (isDeleted) 1 else 0)
            put("is_recalled", if (isRecalled) 1 else 0)
            put("extra", extra)
        }

        fun android.database.Cursor.toMessage() = Message(
            messageId = getString(getColumnIndexOrThrow("message_id")),
            conversationId = getString(getColumnIndexOrThrow("conversation_id")),
            senderId = getString(getColumnIndexOrThrow("sender_id")),
            receiverId = getString(getColumnIndexOrThrow("receiver_id")),
            groupId = getString(getColumnIndexOrThrow("group_id")),
            messageType = MessageType.fromInt(getInt(getColumnIndexOrThrow("message_type"))),
            content = getString(getColumnIndexOrThrow("content")) ?: "",
            status = MessageStatus.fromInt(getInt(getColumnIndexOrThrow("status"))),
            clientSeq = getLong(getColumnIndexOrThrow("client_seq")),
            conversationSeq = getLong(getColumnIndexOrThrow("conversation_seq")),
            userSeq = getLong(getColumnIndexOrThrow("user_seq")),
            sendTime = getLong(getColumnIndexOrThrow("send_time")),
            createTime = getLong(getColumnIndexOrThrow("create_time")),
            quoteMessageId = getString(getColumnIndexOrThrow("quote_message_id")),
            originalMessageId = getString(getColumnIndexOrThrow("original_message_id")),
            isDeleted = getInt(getColumnIndexOrThrow("is_deleted")) == 1,
            isRecalled = getInt(getColumnIndexOrThrow("is_recalled")) == 1,
            extra = getString(getColumnIndexOrThrow("extra"))
        )
    }
}

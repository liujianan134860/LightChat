package com.lightchat.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.lightchat.data.local.dao.ConversationDao.Companion.toContentValues
import com.lightchat.data.local.dao.GroupDao.Companion.toContentValues
import com.lightchat.data.local.dao.UserDao.Companion.toContentValues
import com.lightchat.model.Conversation
import com.lightchat.model.ConversationId
import com.lightchat.model.ConversationType
import com.lightchat.model.ImGroup
import com.lightchat.model.MemberRole
import com.lightchat.model.MessageStatus
import com.lightchat.model.MessageType
import com.lightchat.model.User

object DatabaseSeeder {

    fun seedForUser(db: SQLiteDatabase, userId: String, nickname: String) {
        val key = "seeded_$userId"
        val cursor = db.query("sync_state", null, "owner_user_id = ? AND key = ?", arrayOf(userId, key), null, null, null)
        if (cursor.use { it.moveToFirst() }) return

        seedFriendsForUser(db, userId)
        seedConversationsForUser(db, userId)
        seedMessagesForUser(db, userId)
        seedGroupsForUser(db, userId, nickname)

        val cv = ContentValues().apply {
            put("owner_user_id", userId)
            put("key", key)
            put("value", 1)
        }
        db.insertWithOnConflict("sync_state", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun seedFriendsForUser(db: SQLiteDatabase, userId: String) {
        val friendUsers = listOf(
            User("u1002", "张三", "", "向前走，不回头"),
            User("u1003", "李四", "", "生活不止眼前的苟且"),
            User("u1004", "王五", "", "代码改变世界"),
            User("u1005", "赵六", "", "Stay hungry, stay foolish"),
            User("u1006", "孙七", "", "心之所向，素履以往"),
            User("u1007", "周八", "", "笑看人生"),
            User("u1008", "吴九", "", "保持好奇心"),
            User("u1009", "郑十", "", "做自己喜欢的事")
        )
        for (user in friendUsers) {
            db.insertWithOnConflict("user", null, user.toContentValues(userId), SQLiteDatabase.CONFLICT_IGNORE)
        }
        for (friend in friendUsers) {
            val cv = ContentValues().apply {
                put("owner_user_id", userId)
                put("user_id", userId)
                put("friend_id", friend.userId)
            }
            db.insertWithOnConflict("friend", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    private fun seedConversationsForUser(db: SQLiteDatabase, userId: String) {
        val now = System.currentTimeMillis()
        val conversations = listOf(
            Conversation(ConversationId.single(userId, "u1002"), ConversationType.SINGLE, "u1002", "张三", "", "", 0, null, "好的，明天见", now - 300_000, unreadCount = 3),
            Conversation(ConversationId.single(userId, "u1003"), ConversationType.SINGLE, "u1003", "李四", "", "", 0, null, "那个需求评审完了吗？", now - 600_000),
            Conversation(ConversationId.group("g2001"), ConversationType.GROUP, "g2001", "技术交流群", "", "", 0, null, "[图片]", now - 900_000, unreadCount = 128, isPinned = true, pinnedTime = now - 86_400_000),
            Conversation(ConversationId.single(userId, "u1004"), ConversationType.SINGLE, "u1004", "王五", "", "", 0, null, "最近怎么样？", now - 1_800_000, unreadCount = 1),
            Conversation(ConversationId.single(userId, "u1005"), ConversationType.SINGLE, "u1005", "赵六", "", "", 0, null, "周末一起去爬山", now - 3_600_000),
            Conversation(ConversationId.group("g2002"), ConversationType.GROUP, "g2002", "家人群", "", "", 0, null, "晚上回来吃饭吗？", now - 7_200_000, unreadCount = 5),
            Conversation(ConversationId.single(userId, "u1006"), ConversationType.SINGLE, "u1006", "孙七", "", "", 0, null, "帮我看下这个 bug", now - 86_400_000),
            Conversation(ConversationId.single(userId, "u1007"), ConversationType.SINGLE, "u1007", "周八", "", "", 0, null, "好的，收到", now - 172_800_000)
        )
        for (conv in conversations) {
            db.insertWithOnConflict("conversation", null, conv.toContentValues(userId), SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun seedMessagesForUser(db: SQLiteDatabase, userId: String) {
        val mapping = mapOf(
            ConversationId.single(userId, "u1002") to "u1002",
            ConversationId.single(userId, "u1003") to "u1003",
            ConversationId.single(userId, "u1004") to "u1004",
            ConversationId.single(userId, "u1005") to "u1005",
            ConversationId.single(userId, "u1006") to "u1006",
            ConversationId.single(userId, "u1007") to "u1007"
        )

        val templates = listOf(
            "你好！" to false, "在吗？" to true, "最近忙什么呢？" to false,
            "那个项目进展如何？" to true, "我这边遇到一个问题需要讨论一下" to false,
            "好的，我一会看看" to true, "需要帮忙吗？" to false, "谢谢！" to true,
            "明天有空吗？一起吃个饭" to false, "当然可以！" to true,
            "那个文档你看了吗？" to false, "还没，晚上回去看" to true,
            "代码已经部署到测试环境了" to false, "太好了，我测一下" to true,
            "这个方案你觉得怎么样？" to false, "我觉得挺好的，有些细节再沟通下" to true,
            "OK，那我先开始做了" to false, "嗯，有问题随时沟通" to true
        )

        val baseTime = System.currentTimeMillis() - 86_400_000L

        for ((convId, friendId) in mapping) {
            templates.forEachIndexed { index, (content, fromMe) ->
                val senderId = if (fromMe) userId else friendId
                val cv = ContentValues().apply {
                    put("owner_user_id", userId)
                    put("message_id", "msg_${convId}_$index")
                    put("conversation_id", convId)
                    put("sender_id", senderId)
                    put("receiver_id", if (fromMe) friendId else userId)
                    put("message_type", MessageType.TEXT.value)
                    put("content", content)
                    put("status", MessageStatus.SENT.value)
                    put("client_seq", 0)
                    put("conversation_seq", index.toLong())
                    put("user_seq", 0)
                    put("send_time", baseTime + index * 300_000L)
                    put("create_time", baseTime + index * 300_000L)
                    put("is_deleted", 0)
                    put("is_recalled", 0)
                }
                db.insertWithOnConflict("message", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    private fun seedGroupsForUser(db: SQLiteDatabase, userId: String, nickname: String) {
        // Check if groups already exist
        var cursor = db.query("im_group", null, "owner_user_id = ? AND group_id = ?", arrayOf(userId, "g2001"), null, null, null)
        val g1Exists = cursor.use { it.moveToFirst() }

        if (!g1Exists) {
            val groups = listOf(
                ImGroup("g2001", "技术交流群", "", "", 0, "u1002", 5, System.currentTimeMillis() - 86_400_000L * 30),
                ImGroup("g2002", "家人群", "", "", 0, userId, 4, System.currentTimeMillis() - 86_400_000L * 60)
            )
            for (group in groups) {
                val cv = group.toContentValues(userId)
                db.insertWithOnConflict("im_group", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }

            // Members for g2001
            val m1 = listOf("u1002" to "张三", "u1003" to "李四", "u1004" to "王五", "u1005" to "赵六")
            for ((id, name) in m1) {
                val cv = memberCv(userId, "g2001", id, name, if (id == "u1002") MemberRole.OWNER else MemberRole.MEMBER)
                db.insertWithOnConflict("group_member", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            // Add current user to g2001
            db.insertWithOnConflict("group_member", null, memberCv(userId, "g2001", userId, nickname, MemberRole.MEMBER), SQLiteDatabase.CONFLICT_REPLACE)

            // Members for g2002
            val m2 = listOf("u1006" to "孙七", "u1007" to "周八", "u1002" to "张三")
            for ((id, name) in m2) {
                db.insertWithOnConflict("group_member", null, memberCv(userId, "g2002", id, name, MemberRole.MEMBER), SQLiteDatabase.CONFLICT_REPLACE)
            }
            // Add current user as owner of g2002
            db.insertWithOnConflict("group_member", null, memberCv(userId, "g2002", userId, nickname, MemberRole.OWNER), SQLiteDatabase.CONFLICT_REPLACE)
        }

        // Also initialize sync_state
        val syncCursor = db.query("sync_state", null, "owner_user_id = ? AND key = ?", arrayOf(userId, "last_user_seq"), null, null, null)
        val syncExists = syncCursor.use { it.moveToFirst() }
        if (!syncExists) {
            val cv = ContentValues().apply {
                put("owner_user_id", userId)
                put("key", "last_user_seq")
                put("value", 0)
            }
            db.insertWithOnConflict("sync_state", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun memberCv(ownerUserId: String, groupId: String, userId: String, nickname: String, role: MemberRole) = ContentValues().apply {
        put("owner_user_id", ownerUserId)
        put("group_id", groupId)
        put("user_id", userId)
        put("nickname", nickname)
        put("avatar", "")
        put("avatar_url", "")
        put("avatar_version", 0)
        put("role", role.value)
        put("alias_in_group", "")
        put("join_time", System.currentTimeMillis() - 86_400_000L * 30)
    }
}

package com.lightchat.data.local.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.lightchat.data.local.DatabaseHelper
import com.lightchat.model.GroupMember
import com.lightchat.model.ImGroup
import com.lightchat.model.MemberRole

class GroupDao(private val dbHelper: DatabaseHelper) {

    fun insertGroup(group: ImGroup): Long {
        val db = dbHelper.writableDatabase
        return db.insertWithOnConflict("im_group", null, group.toContentValues(dbHelper.currentOwnerId()), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getGroupById(groupId: String): ImGroup? {
        val db = dbHelper.readableDatabase
        val cursor = db.query("im_group", null, "owner_user_id = ? AND group_id = ?", arrayOf(dbHelper.currentOwnerId(), groupId), null, null, null)
        return cursor.use {
            if (it.moveToFirst()) it.toImGroup() else null
        }
    }

    fun insertMember(member: GroupMember): Long {
        val db = dbHelper.writableDatabase
        return db.insertWithOnConflict("group_member", null, member.toContentValues(dbHelper.currentOwnerId()), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertMembers(members: List<GroupMember>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (member in members) {
                db.insertWithOnConflict("group_member", null, member.toContentValues(dbHelper.currentOwnerId()), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getMembers(groupId: String): List<GroupMember> {
        val db = dbHelper.readableDatabase
        val cursor = db.query("group_member", null, "owner_user_id = ? AND group_id = ?", arrayOf(dbHelper.currentOwnerId(), groupId), null, null, null)
        val members = mutableListOf<GroupMember>()
        cursor.use {
            while (it.moveToNext()) {
                members.add(it.toGroupMember())
            }
        }
        return members
    }

    fun getUserGroups(userId: String): List<ImGroup> {
        val db = dbHelper.readableDatabase
        val sql = """
            SELECT g.* FROM im_group g
            INNER JOIN group_member gm ON g.group_id = gm.group_id
                AND g.owner_user_id = gm.owner_user_id
            WHERE g.owner_user_id = ?
              AND gm.user_id = ?
        """.trimIndent()
        val cursor = db.rawQuery(sql, arrayOf(dbHelper.currentOwnerId(), userId))
        val groups = mutableListOf<ImGroup>()
        cursor.use {
            while (it.moveToNext()) {
                groups.add(it.toImGroup())
            }
        }
        return groups
    }

    fun getCurrentOwnerGroups(): List<ImGroup> {
        val owner = dbHelper.currentOwnerId()
        val db = dbHelper.readableDatabase
        val sql = """
            SELECT DISTINCT g.* FROM im_group g
            LEFT JOIN group_member gm ON g.group_id = gm.group_id
                AND g.owner_user_id = gm.owner_user_id
                AND gm.user_id = ?
            WHERE g.owner_user_id = ?
            ORDER BY g.group_name COLLATE NOCASE ASC, g.group_id ASC
        """.trimIndent()
        val cursor = db.rawQuery(sql, arrayOf(owner, owner))
        val groups = mutableListOf<ImGroup>()
        cursor.use {
            while (it.moveToNext()) {
                groups.add(it.toImGroup())
            }
        }
        return groups
    }

    fun removeMember(groupId: String, userId: String): Int {
        val db = dbHelper.writableDatabase
        return db.delete("group_member", "owner_user_id = ? AND group_id = ? AND user_id = ?", arrayOf(dbHelper.currentOwnerId(), groupId, userId))
    }

    fun updateMemberCount(groupId: String, count: Int): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("member_count", count) }
        return db.update("im_group", cv, "owner_user_id = ? AND group_id = ?", arrayOf(dbHelper.currentOwnerId(), groupId))
    }

    fun updateMemberAvatar(userId: String, avatar: String, avatarUrl: String, avatarVersion: Int) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("avatar", avatar)
            put("avatar_url", avatarUrl)
            put("avatar_version", avatarVersion)
        }
        db.update("group_member", cv, "owner_user_id = ? AND user_id = ?", arrayOf(dbHelper.currentOwnerId(), userId))
    }

    fun upsertConversationMemberRead(conversationId: String, userId: String, lastReadSeq: Long): Long {
        if (conversationId.isBlank() || userId.isBlank() || lastReadSeq <= 0L) return 0L
        val db = dbHelper.writableDatabase
        val owner = dbHelper.currentOwnerId()
        val cv = ContentValues().apply {
            put("owner_user_id", owner)
            put("conversation_id", conversationId)
            put("user_id", userId)
            put("last_read_seq", lastReadSeq)
            put("last_seen_time", System.currentTimeMillis())
        }
        val updated = db.update(
            "conversation_member",
            cv,
            "owner_user_id = ? AND conversation_id = ? AND user_id = ? AND last_read_seq < ?",
            arrayOf(owner, conversationId, userId, lastReadSeq.toString())
        )
        if (updated > 0) return updated.toLong()
        return db.insertWithOnConflict("conversation_member", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun getConversationReadSeqs(conversationId: String): Map<String, Long> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "conversation_member",
            arrayOf("user_id", "last_read_seq"),
            "owner_user_id = ? AND conversation_id = ?",
            arrayOf(dbHelper.currentOwnerId(), conversationId),
            null,
            null,
            null
        )
        val result = mutableMapOf<String, Long>()
        cursor.use {
            while (it.moveToNext()) {
                result[it.getString(0)] = it.getLong(1)
            }
        }
        return result
    }

    fun getConversationReadSeq(conversationId: String, userId: String): Long {
        if (conversationId.isBlank() || userId.isBlank()) return 0L
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "conversation_member",
            arrayOf("last_read_seq"),
            "owner_user_id = ? AND conversation_id = ? AND user_id = ?",
            arrayOf(dbHelper.currentOwnerId(), conversationId, userId),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    companion object {
        fun ImGroup.toContentValues(ownerUserId: String) = ContentValues().apply {
            put("owner_user_id", ownerUserId)
            put("group_id", groupId)
            put("group_name", groupName)
            put("avatar", avatar)
            put("avatar_url", avatarUrl)
            put("avatar_version", avatarVersion)
            put("owner_id", ownerId)
            put("member_count", memberCount)
            put("create_time", createTime)
        }

        fun android.database.Cursor.toImGroup() = ImGroup(
            groupId = getString(getColumnIndexOrThrow("group_id")),
            groupName = getString(getColumnIndexOrThrow("group_name")) ?: "",
            avatar = getString(getColumnIndexOrThrow("avatar")) ?: "",
            avatarUrl = getString(getColumnIndexOrThrow("avatar_url")) ?: "",
            avatarVersion = getInt(getColumnIndexOrThrow("avatar_version")),
            ownerId = getString(getColumnIndexOrThrow("owner_id")) ?: "",
            memberCount = getInt(getColumnIndexOrThrow("member_count")),
            createTime = getLong(getColumnIndexOrThrow("create_time"))
        )

        fun GroupMember.toContentValues(ownerUserId: String) = ContentValues().apply {
            put("owner_user_id", ownerUserId)
            put("group_id", groupId)
            put("user_id", userId)
            put("nickname", nickname)
            put("avatar", avatar)
            put("avatar_url", avatarUrl)
            put("avatar_version", avatarVersion)
            put("role", role.value)
            put("alias_in_group", aliasInGroup)
            put("join_time", joinTime)
        }

        fun android.database.Cursor.toGroupMember() = GroupMember(
            groupId = getString(getColumnIndexOrThrow("group_id")),
            userId = getString(getColumnIndexOrThrow("user_id")),
            nickname = getString(getColumnIndexOrThrow("nickname")) ?: "",
            avatar = getString(getColumnIndexOrThrow("avatar")) ?: "",
            avatarUrl = getString(getColumnIndexOrThrow("avatar_url")) ?: "",
            avatarVersion = getInt(getColumnIndexOrThrow("avatar_version")),
            role = MemberRole.fromInt(getInt(getColumnIndexOrThrow("role"))),
            aliasInGroup = getString(getColumnIndexOrThrow("alias_in_group")) ?: "",
            joinTime = getLong(getColumnIndexOrThrow("join_time"))
        )
    }
}

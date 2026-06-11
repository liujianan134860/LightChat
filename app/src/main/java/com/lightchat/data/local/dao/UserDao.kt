package com.lightchat.data.local.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.lightchat.data.local.DatabaseHelper
import com.lightchat.model.User

class UserDao(private val dbHelper: DatabaseHelper) {

    private fun ownerId(): String = dbHelper.currentOwnerId()

    fun insert(user: User): Long {
        val db = dbHelper.writableDatabase
        return db.insertWithOnConflict("user", null, user.toContentValues(ownerId()), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun upsertPreservingExisting(user: User): Long {
        val existing = getById(user.userId)
        val merged = if (existing == null) {
            user.copy(nickname = user.nickname.ifBlank { user.userId })
        } else {
            val shouldPromoteRemoteAvatar = user.avatarUrl.isNotBlank() && user.avatarVersion >= existing.avatarVersion
            val mergedAvatar = when {
                user.avatar.startsWith("#") -> user.avatar
                shouldPromoteRemoteAvatar -> existing.avatar.takeIf { it.startsWith("#") }.orEmpty()
                user.avatar.isNotBlank() -> user.avatar
                else -> existing.avatar
            }
            existing.copy(
                nickname = chooseIncomingProfileValue(user.nickname, existing.nickname, user.userId),
                avatar = mergedAvatar,
                avatarUrl = if (shouldPromoteRemoteAvatar) user.avatarUrl else existing.avatarUrl,
                avatarVersion = maxOf(user.avatarVersion, existing.avatarVersion),
                signature = user.signature.ifBlank { existing.signature },
                region = user.region.ifBlank { existing.region }
            )
        }
        return insert(merged)
    }

    fun insertAll(users: List<User>) {
        val db = dbHelper.writableDatabase
        val owner = ownerId()
        db.beginTransaction()
        try {
            for (user in users) {
                db.insertWithOnConflict("user", null, user.toContentValues(owner), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getById(userId: String): User? {
        val db = dbHelper.readableDatabase
        val cursor = db.query("user", null, "owner_user_id = ? AND user_id = ?", arrayOf(ownerId(), userId), null, null, null)
        return cursor.use {
            if (it.moveToFirst()) it.toUser() else null
        }
    }

    fun getByIds(userIds: Set<String>): Map<String, User> {
        if (userIds.isEmpty()) return emptyMap()
        val db = dbHelper.readableDatabase
        val owner = ownerId()
        val placeholders = userIds.joinToString(",") { "?" }
        val args = listOf(owner) + userIds.toList()
        val cursor = db.rawQuery(
            "SELECT user_id, nickname, avatar, avatar_url, avatar_version, signature, region FROM user WHERE owner_user_id = ? AND user_id IN ($placeholders)",
            args.toTypedArray()
        )
        val users = mutableMapOf<String, User>()
        cursor.use {
            while (it.moveToNext()) {
                val user = it.toUser()
                users[user.userId] = user
            }
        }
        return users
    }

    fun getAll(): List<User> {
        val db = dbHelper.readableDatabase
        val cursor = db.query("user", null, "owner_user_id = ?", arrayOf(ownerId()), null, null, null)
        val users = mutableListOf<User>()
        cursor.use {
            while (it.moveToNext()) {
                users.add(it.toUser())
            }
        }
        return users
    }

    fun update(user: User): Int {
        val db = dbHelper.writableDatabase
        return db.update("user", user.toContentValues(ownerId()), "owner_user_id = ? AND user_id = ?", arrayOf(ownerId(), user.userId))
    }

    fun delete(userId: String): Int {
        val db = dbHelper.writableDatabase
        return db.delete("user", "owner_user_id = ? AND user_id = ?", arrayOf(ownerId(), userId))
    }

    fun getFriends(currentUserId: String): List<User> {
        val db = dbHelper.readableDatabase
        val owner = ownerId()
        val sql = """
            SELECT
                f.friend_id AS user_id,
                COALESCE(NULLIF(u.nickname, ''), f.friend_id) AS nickname,
                COALESCE(u.avatar, '') AS avatar,
                COALESCE(u.avatar_url, '') AS avatar_url,
                COALESCE(u.avatar_version, 0) AS avatar_version,
                COALESCE(u.signature, '') AS signature,
                COALESCE(u.region, '') AS region
            FROM friend f
            LEFT JOIN user u ON u.owner_user_id = ? AND u.user_id = f.friend_id
            WHERE f.owner_user_id = ? AND f.user_id = ?
            ORDER BY nickname COLLATE NOCASE ASC, f.friend_id ASC
        """.trimIndent()
        val cursor = db.rawQuery(sql, arrayOf(owner, owner, currentUserId))
        val friends = mutableListOf<User>()
        cursor.use {
            while (it.moveToNext()) {
                friends.add(it.toUser())
            }
        }
        return friends
    }

    fun getFriendsPage(currentUserId: String, limit: Int = 50, offset: Int = 0): List<User> {
        val db = dbHelper.readableDatabase
        val owner = ownerId()
        val sql = """
            SELECT
                f.friend_id AS user_id,
                COALESCE(NULLIF(u.nickname, ''), f.friend_id) AS nickname,
                COALESCE(u.avatar, '') AS avatar,
                COALESCE(u.avatar_url, '') AS avatar_url,
                COALESCE(u.avatar_version, 0) AS avatar_version,
                COALESCE(u.signature, '') AS signature,
                COALESCE(u.region, '') AS region
            FROM friend f
            LEFT JOIN user u ON u.owner_user_id = ? AND u.user_id = f.friend_id
            WHERE f.owner_user_id = ? AND f.user_id = ?
            ORDER BY nickname COLLATE NOCASE ASC, f.friend_id ASC
            LIMIT ? OFFSET ?
        """.trimIndent()
        val cursor = db.rawQuery(sql, arrayOf(owner, owner, currentUserId, limit.toString(), offset.toString()))
        val friends = mutableListOf<User>()
        cursor.use {
            while (it.moveToNext()) {
                friends.add(it.toUser())
            }
        }
        return friends
    }

    fun searchFriends(currentUserId: String, query: String, limit: Int = 200): List<User> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        val db = dbHelper.readableDatabase
        val owner = ownerId()
        val like = "%$trimmed%"
        val sql = """
            SELECT
                f.friend_id AS user_id,
                COALESCE(NULLIF(u.nickname, ''), f.friend_id) AS nickname,
                COALESCE(u.avatar, '') AS avatar,
                COALESCE(u.avatar_url, '') AS avatar_url,
                COALESCE(u.avatar_version, 0) AS avatar_version,
                COALESCE(u.signature, '') AS signature,
                COALESCE(u.region, '') AS region
            FROM friend f
            LEFT JOIN user u ON u.owner_user_id = ? AND u.user_id = f.friend_id
            WHERE f.owner_user_id = ? AND f.user_id = ?
              AND (
                f.friend_id LIKE ?
                OR COALESCE(u.nickname, '') LIKE ?
                OR COALESCE(u.signature, '') LIKE ?
                OR COALESCE(u.region, '') LIKE ?
              )
            ORDER BY nickname COLLATE NOCASE ASC, f.friend_id ASC
            LIMIT ?
        """.trimIndent()
        val cursor = db.rawQuery(
            sql,
            arrayOf(owner, owner, currentUserId, like, like, like, like, limit.toString())
        )
        val friends = mutableListOf<User>()
        cursor.use {
            while (it.moveToNext()) {
                friends.add(it.toUser())
            }
        }
        return friends
    }

    fun getFriendCount(currentUserId: String): Int {
        val db = dbHelper.readableDatabase
        val owner = ownerId()
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM friend WHERE owner_user_id = ? AND user_id = ?",
            arrayOf(owner, currentUserId)
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun isFriend(userId: String, friendId: String): Boolean {
        val db = dbHelper.readableDatabase
        val owner = ownerId()
        val cursor = db.query(
            "friend",
            arrayOf("user_id"),
            "owner_user_id = ? AND user_id = ? AND friend_id = ?",
            arrayOf(owner, userId, friendId),
            null, null, null
        )
        return cursor.use { it.moveToFirst() }
    }

    fun addFriend(userId: String, friendId: String) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("owner_user_id", ownerId())
            put("user_id", userId)
            put("friend_id", friendId)
        }
        db.insertWithOnConflict("friend", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun addFriends(userId: String, friendIds: List<String>) {
        val db = dbHelper.writableDatabase
        val owner = ownerId()
        db.beginTransaction()
        try {
            for (friendId in friendIds) {
                val cv = ContentValues().apply {
                    put("owner_user_id", owner)
                    put("user_id", userId)
                    put("friend_id", friendId)
                }
                db.insertWithOnConflict("friend", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        fun User.toContentValues(ownerUserId: String) = ContentValues().apply {
            put("owner_user_id", ownerUserId)
            put("user_id", userId)
            put("nickname", nickname)
            put("avatar", avatar)
            put("avatar_url", avatarUrl)
            put("avatar_version", avatarVersion)
            put("signature", signature)
            put("region", region)
        }

        fun android.database.Cursor.toUser() = User(
            userId = getString(getColumnIndexOrThrow("user_id")),
            nickname = getString(getColumnIndexOrThrow("nickname")) ?: "",
            avatar = getString(getColumnIndexOrThrow("avatar")) ?: "",
            avatarUrl = getString(getColumnIndexOrThrow("avatar_url")) ?: "",
            avatarVersion = getInt(getColumnIndexOrThrow("avatar_version")),
            signature = getString(getColumnIndexOrThrow("signature")) ?: "",
            region = getString(getColumnIndexOrThrow("region")) ?: ""
        )

        private fun chooseIncomingProfileValue(incoming: String, existing: String, userId: String): String {
            val cleanIncoming = incoming.trim()
            val cleanExisting = existing.trim()
            if (cleanIncoming.isBlank()) return cleanExisting.ifBlank { userId }
            if (cleanIncoming == userId && cleanExisting.isNotBlank() && cleanExisting != userId) return cleanExisting
            return cleanIncoming
        }
    }
}

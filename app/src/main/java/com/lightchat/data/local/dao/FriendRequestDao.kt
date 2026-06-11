package com.lightchat.data.local.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.lightchat.data.local.DatabaseHelper
import com.lightchat.model.FriendRequest
import com.lightchat.model.RequestStatus

class FriendRequestDao(private val dbHelper: DatabaseHelper) {

    private val owner: String get() = dbHelper.currentOwnerId()

    fun insert(request: FriendRequest): Long {
        val db = dbHelper.writableDatabase
        return db.insertWithOnConflict("friend_request", null, request.toContentValues(owner), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getPendingRequests(userId: String): List<FriendRequest> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "friend_request", null,
            "owner_user_id = ? AND to_user_id = ? AND status = ?",
            arrayOf(owner, userId, RequestStatus.PENDING.value.toString()),
            null, null, "create_time DESC"
        )
        val requests = mutableListOf<FriendRequest>()
        cursor.use {
            while (it.moveToNext()) requests.add(it.toFriendRequest())
        }
        return requests
    }

    fun getPendingCount(userId: String): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM friend_request WHERE owner_user_id = ? AND to_user_id = ? AND status = ?",
            arrayOf(owner, userId, RequestStatus.PENDING.value.toString())
        )
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun hasPendingRequest(fromUserId: String, toUserId: String): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "friend_request", null,
            "owner_user_id = ? AND from_user_id = ? AND to_user_id = ? AND status = ?",
            arrayOf(owner, fromUserId, toUserId, RequestStatus.PENDING.value.toString()),
            null, null, null
        )
        return cursor.use { it.moveToFirst() }
    }

    fun updateStatus(requestId: String, status: RequestStatus): Int {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("status", status.value) }
        return db.update("friend_request", cv, "owner_user_id = ? AND request_id = ?", arrayOf(owner, requestId))
    }

    companion object {
        fun FriendRequest.toContentValues(owner: String) = ContentValues().apply {
            put("owner_user_id", owner)
            put("request_id", requestId)
            put("from_user_id", fromUserId)
            put("to_user_id", toUserId)
            put("from_nickname", fromNickname)
            put("message", message)
            put("status", status.value)
            put("create_time", createTime)
        }

        fun android.database.Cursor.toFriendRequest() = FriendRequest(
            requestId = getString(getColumnIndexOrThrow("request_id")),
            fromUserId = getString(getColumnIndexOrThrow("from_user_id")),
            toUserId = getString(getColumnIndexOrThrow("to_user_id")),
            fromNickname = getString(getColumnIndexOrThrow("from_nickname")) ?: "",
            message = getString(getColumnIndexOrThrow("message")) ?: "",
            status = RequestStatus.fromInt(getInt(getColumnIndexOrThrow("status"))),
            createTime = getLong(getColumnIndexOrThrow("create_time"))
        )
    }
}

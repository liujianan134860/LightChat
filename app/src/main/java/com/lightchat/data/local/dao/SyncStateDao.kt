package com.lightchat.data.local.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.lightchat.data.local.DatabaseHelper

class SyncStateDao(private val dbHelper: DatabaseHelper) {

    fun get(key: String): Long {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "sync_state",
            arrayOf("value"),
            "owner_user_id = ? AND key = ?",
            arrayOf(dbHelper.currentOwnerId(), key),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    fun put(key: String, value: Long) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("owner_user_id", dbHelper.currentOwnerId())
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict("sync_state", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getLastUserSeq(): Long = get(KEY_LAST_USER_SEQ)

    fun setLastUserSeq(seq: Long) = put(KEY_LAST_USER_SEQ, seq)

    companion object {
        const val KEY_LAST_USER_SEQ = "last_user_seq"
    }
}

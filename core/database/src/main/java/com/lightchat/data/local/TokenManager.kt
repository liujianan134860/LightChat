package com.lightchat.data.local

import android.content.Context
import android.content.SharedPreferences
import android.content.ContentValues
import org.json.JSONObject
import java.util.Base64

class TokenManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("lightchat_auth", Context.MODE_PRIVATE)
    private val databaseHelper by lazy { DatabaseHelper(appContext) }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        saveSessionToDatabase(token)
    }

    fun getToken(): String? {
        val token = prefs.getString(KEY_TOKEN, null)
        if (token != null && !isJwtLike(token)) {
            clearToken()
            return null
        }
        val claims = token?.let { parseJwtClaims(it) }
        val nowSeconds = System.currentTimeMillis() / 1000
        if (claims != null && claims.expiresAt <= nowSeconds) {
            clearToken()
            return null
        }
        return token
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
        databaseHelper.writableDatabase.execSQL("UPDATE auth_session SET is_active = 0")
    }

    fun isLoggedIn(): Boolean = getToken() != null

    private fun isJwtLike(token: String): Boolean = token.split(".").size == 3

    private fun saveSessionToDatabase(token: String) {
        val claims = parseJwtClaims(token) ?: return
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("token_id", claims.tokenId)
            put("user_id", claims.userId)
            put("token", token)
            put("issued_at", claims.issuedAt)
            put("expires_at", claims.expiresAt)
            put("login_time", now)
            put("last_used_at", now)
            put("is_active", 1)
        }
        val db = databaseHelper.writableDatabase
        db.beginTransaction()
        try {
            db.update("auth_session", ContentValues().apply { put("is_active", 0) }, null, null)
            db.insertWithOnConflict("auth_session", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun parseJwtClaims(token: String): LocalJwtClaims? = runCatching {
        val payloadPart = token.split(".").getOrNull(1) ?: return null
        val payload = String(Base64.getUrlDecoder().decode(payloadPart), Charsets.UTF_8)
        val json = JSONObject(payload)
        LocalJwtClaims(
            userId = json.getString("sub"),
            tokenId = json.getString("jti"),
            issuedAt = json.optLong("iat", 0),
            expiresAt = json.optLong("exp", 0)
        )
    }.getOrNull()

    private data class LocalJwtClaims(
        val userId: String,
        val tokenId: String,
        val issuedAt: Long,
        val expiresAt: Long
    )

    companion object {
        private const val KEY_TOKEN = "jwt_token"
    }
}

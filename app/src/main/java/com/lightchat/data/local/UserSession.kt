package com.lightchat.data.local

import android.content.Context
import android.content.SharedPreferences

class UserSession(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("lightchat_session", Context.MODE_PRIVATE)

    var currentUserId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var currentNickname: String?
        get() = prefs.getString(KEY_NICKNAME, null)
        set(value) = prefs.edit().putString(KEY_NICKNAME, value).apply()

    fun isLoggedIn(): Boolean = currentUserId != null

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_USER_ID = "current_user_id"
        private const val KEY_NICKNAME = "current_nickname"
    }
}

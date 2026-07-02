package com.lightchat.domain.repository

import com.lightchat.model.User

interface AuthRepositoryContract {
    fun login(username: String, password: String): Result<User>
    fun register(username: String, password: String, nickname: String): Result<User>
    fun logout()
    fun isLoggedIn(): Boolean
    fun getCurrentUserId(): String?
}

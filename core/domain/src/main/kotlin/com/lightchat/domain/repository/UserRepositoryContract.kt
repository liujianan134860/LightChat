package com.lightchat.domain.repository

import com.lightchat.model.User

interface UserRepositoryContract {
    fun getCurrentUser(): User?
    fun getUserById(userId: String): User?
    fun getFriends(): List<User>
    fun getFriendsPage(limit: Int = 50, offset: Int = 0): List<User>
    fun getFriendCount(): Int
    fun searchFriends(query: String, limit: Int = 200): List<User>
    fun saveUser(user: User)
    fun saveUsers(users: List<User>)
}

package com.lightchat.data.repository

import com.lightchat.data.local.dao.UserDao
import com.lightchat.model.User

class UserRepository(private val userDao: UserDao) {

    fun getCurrentUser(): User? {
        val app = com.lightchat.LightChatApplication.instance
        val userId = app.userSession.currentUserId ?: return null
        return userDao.getById(userId)
    }

    fun getUserById(userId: String): User? = userDao.getById(userId)

    fun getFriends(): List<User> {
        val app = com.lightchat.LightChatApplication.instance
        val userId = app.userSession.currentUserId ?: return emptyList()
        return userDao.getFriends(userId)
    }

    fun getFriendsPage(limit: Int = 50, offset: Int = 0): List<User> {
        val app = com.lightchat.LightChatApplication.instance
        val userId = app.userSession.currentUserId ?: return emptyList()
        return userDao.getFriendsPage(userId, limit, offset)
    }

    fun getFriendCount(): Int {
        val app = com.lightchat.LightChatApplication.instance
        val userId = app.userSession.currentUserId ?: return 0
        return userDao.getFriendCount(userId)
    }

    fun searchFriends(query: String, limit: Int = 200): List<User> {
        val app = com.lightchat.LightChatApplication.instance
        val userId = app.userSession.currentUserId ?: return emptyList()
        return userDao.searchFriends(userId, query, limit)
    }

    fun saveUser(user: User) = userDao.insert(user)

    fun saveUsers(users: List<User>) = userDao.insertAll(users)
}

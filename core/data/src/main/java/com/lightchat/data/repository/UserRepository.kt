package com.lightchat.data.repository

import com.lightchat.data.local.dao.UserDao
import com.lightchat.data.local.UserSession
import com.lightchat.model.User
import com.lightchat.domain.repository.UserRepositoryContract

class UserRepository(
    private val userDao: UserDao,
    private val userSession: UserSession
) : UserRepositoryContract {

    override fun getCurrentUser(): User? {
        val userId = userSession.currentUserId ?: return null
        return userDao.getById(userId)
    }

    override fun getUserById(userId: String): User? = userDao.getById(userId)

    override fun getFriends(): List<User> {
        val userId = userSession.currentUserId ?: return emptyList()
        return userDao.getFriends(userId)
    }

    override fun getFriendsPage(limit: Int, offset: Int): List<User> {
        val userId = userSession.currentUserId ?: return emptyList()
        return userDao.getFriendsPage(userId, limit, offset)
    }

    override fun getFriendCount(): Int {
        val userId = userSession.currentUserId ?: return 0
        return userDao.getFriendCount(userId)
    }

    override fun searchFriends(query: String, limit: Int): List<User> {
        val userId = userSession.currentUserId ?: return emptyList()
        return userDao.searchFriends(userId, query, limit)
    }

    override fun saveUser(user: User) {
        userDao.insert(user)
    }

    override fun saveUsers(users: List<User>) {
        userDao.insertAll(users)
    }
}

package com.lightchat.server.session

import java.util.concurrent.ConcurrentHashMap

class ConnectionRegistry {

    private val connToUser = ConcurrentHashMap<ClientConnection, String>()
    private val userToConn = ConcurrentHashMap<String, ClientConnection>()

    fun register(conn: ClientConnection, userId: String): String? {
        val prev = connToUser.put(conn, userId)
        userToConn[userId] = conn
        return prev
    }

    fun unregister(conn: ClientConnection): String? {
        val userId = connToUser.remove(conn)
        if (userId != null) {
            userToConn.remove(userId)
        }
        return userId
    }

    fun getUserId(conn: ClientConnection): String? = connToUser[conn]
    fun getConnection(userId: String): ClientConnection? = userToConn[userId]
    fun isOnline(userId: String): Boolean = userToConn[userId]?.isOpen == true
    fun onlineCount(): Int = connToUser.size
}

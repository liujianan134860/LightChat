package com.lightchat.server.model

data class ServerGroup(
    val groupId: String,
    val groupName: String,
    val ownerId: String,
    val members: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet(),
    val createdAt: Long = System.currentTimeMillis()
)

package com.lightchat.server.model

data class ServerConversation(
    val conversationId: String,
    val type: String = "SINGLE",
    val participants: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet(),
    val groupId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

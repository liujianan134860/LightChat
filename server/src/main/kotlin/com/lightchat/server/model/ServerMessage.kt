package com.lightchat.server.model

data class ServerMessage(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String? = null,
    val groupId: String? = null,
    val messageType: Int,
    val content: String,
    val clientSeq: Long = 0,
    val conversationSeq: Long = 0,
    val sendTime: Long = System.currentTimeMillis(),
    val createTime: Long = System.currentTimeMillis(),
    val extra: String? = null
)

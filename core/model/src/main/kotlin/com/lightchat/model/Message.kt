package com.lightchat.model

data class Message(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String? = null,
    val groupId: String? = null,
    val messageType: MessageType = MessageType.TEXT,
    val content: String,
    val status: MessageStatus = MessageStatus.SENT,
    val clientSeq: Long = 0,
    val conversationSeq: Long = 0,
    val userSeq: Long = 0,
    val sendTime: Long = System.currentTimeMillis(),
    val createTime: Long = System.currentTimeMillis(),
    val quoteMessageId: String? = null,
    val originalMessageId: String? = null,
    val isDeleted: Boolean = false,
    val isRecalled: Boolean = false,
    val extra: String? = null
)

enum class MessageType(val value: Int) {
    TEXT(0),
    IMAGE(1),
    VOICE(2),
    VIDEO(3),
    FILE(4),
    USER_CARD(5),
    GROUP_CARD(6),
    MERGE_FORWARD(7),
    SYSTEM(99);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: TEXT
    }
}

enum class MessageStatus(val value: Int) {
    CREATED(0),
    SENDING(1),
    SENT(2),
    DELIVERED(3),
    READ(4),
    FAILED(5);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: CREATED
    }
}

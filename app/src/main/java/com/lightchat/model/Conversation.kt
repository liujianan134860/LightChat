package com.lightchat.model

data class Conversation(
    val conversationId: String,
    val type: ConversationType = ConversationType.SINGLE,
    val targetId: String,
    val title: String,
    val avatar: String = "",
    val avatarUrl: String = "",
    val avatarVersion: Int = 0,
    val lastMessageId: String? = null,
    val lastMessageContent: String = "",
    val lastMessageTime: Long = 0,
    val lastMessageThumbnail: String? = null,
    val unreadCount: Int = 0,
    val atMe: Boolean = false,
    val atMeCount: Int = 0,
    val isPinned: Boolean = false,
    val pinnedTime: Long = 0,
    val isHidden: Boolean = false,
    val isDeleted: Boolean = false,
    val mute: Boolean = false,
    val manualUnread: Boolean = false
)

enum class ConversationType(val value: Int) {
    SINGLE(0),
    GROUP(1),
    SYSTEM(2);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: SINGLE
    }
}

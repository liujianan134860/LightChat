package com.lightchat.sync

data class SyncEvent(
    val eventType: Int,
    val userSeq: Long,
    val messageId: String? = null,
    val conversationId: String? = null,
    val senderId: String? = null,
    val senderNickname: String? = null,
    val senderAvatarUrl: String? = null,
    val senderAvatarVersion: Int? = null,
    val receiverId: String? = null,
    val receiverNickname: String? = null,
    val receiverAvatarUrl: String? = null,
    val receiverAvatarVersion: Int? = null,
    val groupId: String? = null,
    val messageType: Int? = null,
    val content: String? = null,
    val conversationSeq: Long? = null,
    val sendTime: Long? = null,
    val createTime: Long? = null,
    val extra: String? = null,
    // Group events
    val groupName: String? = null,
    val ownerId: String? = null,
    val memberCount: Int? = null,
    val members: List<MemberInfo>? = null,
    val inviterId: String? = null,
    val inviterNickname: String? = null,
    val invitedMembers: List<MemberInfo>? = null,
    // Friend events
    val fromUserId: String? = null,
    val toUserId: String? = null,
    val fromNickname: String? = null,
    val toNickname: String? = null,
    val fromAvatarUrl: String? = null,
    val fromAvatarVersion: Int? = null,
    val toAvatarUrl: String? = null,
    val toAvatarVersion: Int? = null,
    val requestMessage: String? = null,
    // Recall/Read
    val recalledMessageId: String? = null,
    val readConversationId: String? = null,
    val readUserId: String? = null,
    val lastReadSeq: Long? = null,
    // User update
    val nickname: String? = null,
    val avatar: String? = null,
    val avatarUrl: String? = null,
    val avatarVersion: Int? = null,
    val signature: String? = null,
    val region: String? = null,
    // Conversation settings update
    val isPinned: Boolean? = null,
    val pinnedTime: Long? = null,
    val mute: Boolean? = null
)

data class MemberInfo(
    val userId: String,
    val nickname: String = "",
    val avatar: String = "",
    val avatarUrl: String = "",
    val avatarVersion: Int = 0,
    val role: Int = 1
)

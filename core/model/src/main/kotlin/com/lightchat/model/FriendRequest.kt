package com.lightchat.model

data class FriendRequest(
    val requestId: String,
    val fromUserId: String,
    val toUserId: String,
    val fromNickname: String,
    val message: String = "",
    val status: RequestStatus = RequestStatus.PENDING,
    val createTime: Long = System.currentTimeMillis()
)

enum class RequestStatus(val value: Int) {
    PENDING(0),
    ACCEPTED(1),
    REJECTED(2);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: PENDING
    }
}

package com.lightchat.model

data class GroupMember(
    val groupId: String,
    val userId: String,
    val nickname: String,
    val avatar: String = "",
    val avatarUrl: String = "",
    val avatarVersion: Int = 0,
    val role: MemberRole = MemberRole.MEMBER,
    val aliasInGroup: String = "",
    val joinTime: Long = System.currentTimeMillis()
)

enum class MemberRole(val value: Int) {
    OWNER(0),
    ADMIN(1),
    MEMBER(2);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: MEMBER
    }
}

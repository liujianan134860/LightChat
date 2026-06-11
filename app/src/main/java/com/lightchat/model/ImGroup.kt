package com.lightchat.model

data class ImGroup(
    val groupId: String,
    val groupName: String,
    val avatar: String = "",
    val avatarUrl: String = "",
    val avatarVersion: Int = 0,
    val ownerId: String,
    val memberCount: Int = 0,
    val createTime: Long = System.currentTimeMillis()
)

package com.lightchat.server.model

data class ServerUser(
    val userId: String,
    val nickname: String,
    val avatar: String = "",
    val avatarUrl: String = "",
    val avatarVersion: Int = 0,
    val signature: String = "",
    val region: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

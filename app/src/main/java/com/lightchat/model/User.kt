package com.lightchat.model

data class User(
    val userId: String,
    val nickname: String,
    val avatar: String = "",
    val avatarUrl: String = "",
    val avatarVersion: Int = 0,
    val signature: String = "",
    val region: String = ""
)

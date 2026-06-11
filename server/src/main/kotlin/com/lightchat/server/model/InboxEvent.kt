package com.lightchat.server.model

import org.json.JSONObject

data class InboxEvent(
    val userSeq: Long,
    val eventType: Int,
    val payload: JSONObject,
    val createdAt: Long = System.currentTimeMillis()
)

package com.lightchat.model

object ConversationId {
    fun single(userA: String, userB: String): String {
        val (min, max) = if (userA < userB) userA to userB else userB to userA
        return "single_${min}_$max"
    }

    fun group(groupId: String): String = "group_$groupId"
}

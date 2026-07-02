package com.lightchat.domain.session

interface AppPresence {
    val isForeground: Boolean
    val currentConversationId: String?
}

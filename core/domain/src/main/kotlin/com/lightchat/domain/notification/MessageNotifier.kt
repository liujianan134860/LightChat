package com.lightchat.domain.notification

interface MessageNotifier {
    fun showMessage(
        conversationId: String,
        title: String,
        content: String,
        targetMessageId: String = ""
    )
}

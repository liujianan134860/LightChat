package com.lightchat.domain.repository

import com.lightchat.model.Conversation

interface ConversationRepositoryContract {
    fun getConversations(): List<Conversation>
    fun getConversationsPage(limit: Int = 30, offset: Int = 0): List<Conversation>
    fun getConversationCount(): Int
    fun getConversation(conversationId: String): Conversation?
    fun saveConversation(conversation: Conversation)
    fun updateLastMessage(
        conversationId: String,
        messageId: String,
        content: String,
        time: Long,
        thumbnail: String? = null
    )
    fun incrementUnread(conversationId: String): Int
    fun clearUnread(conversationId: String)
    fun setPinned(conversationId: String, pinned: Boolean)
    fun setHidden(conversationId: String, hidden: Boolean)
    fun deleteConversation(conversationId: String)
    fun setMute(conversationId: String, mute: Boolean)
}

package com.lightchat.domain.repository

import com.lightchat.model.Message
import com.lightchat.model.MessageStatus

interface MessageRepositoryContract {
    fun getMessages(conversationId: String, limit: Int = 80): List<Message>
    fun getMessagesAround(conversationId: String, messageId: String): List<Message>
    fun loadMoreMessages(conversationId: String, beforeConversationSeq: Long, limit: Int = 80): List<Message>
    fun loadOlderMessages(conversationId: String, beforeMessage: Message, limit: Int = 80): List<Message>
    fun loadNewerMessages(conversationId: String, afterMessage: Message, limit: Int = 80): List<Message>
    fun hasMessagesBefore(conversationId: String, message: Message): Boolean
    fun hasMessagesAfter(conversationId: String, message: Message): Boolean
    fun sendMessage(message: Message): Long
    fun getMessageById(messageId: String): Message?
    fun updateMessageStatus(messageId: String, status: MessageStatus)
    fun getMessageCount(conversationId: String): Int
}

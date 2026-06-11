package com.lightchat.data.repository

import com.lightchat.data.local.dao.MessageDao
import com.lightchat.model.Message
import com.lightchat.model.MessageStatus

class MessageRepository(private val messageDao: MessageDao) {

    fun getMessages(conversationId: String, limit: Int = 80): List<Message> {
        return messageDao.getLatestMessages(conversationId, limit)
    }

    fun getMessagesAround(conversationId: String, messageId: String): List<Message> {
        return messageDao.getMessagesAround(conversationId, messageId)
    }

    fun loadMoreMessages(conversationId: String, beforeConversationSeq: Long, limit: Int = 80): List<Message> {
        return messageDao.getMessagesBeforeSeq(conversationId, beforeConversationSeq, limit)
    }

    fun loadOlderMessages(conversationId: String, beforeMessage: Message, limit: Int = 80): List<Message> {
        return if (beforeMessage.conversationSeq > 0L) {
            messageDao.getMessagesBeforeSeq(conversationId, beforeMessage.conversationSeq, limit)
        } else {
            messageDao.getMessagesBefore(conversationId, beforeMessage.createTime, limit)
        }
    }

    fun loadNewerMessages(conversationId: String, afterMessage: Message, limit: Int = 80): List<Message> {
        return messageDao.getMessagesAfter(conversationId, afterMessage, limit)
    }

    fun hasMessagesBefore(conversationId: String, message: Message): Boolean {
        return messageDao.hasMessagesBefore(conversationId, message)
    }

    fun hasMessagesAfter(conversationId: String, message: Message): Boolean {
        return messageDao.hasMessagesAfter(conversationId, message)
    }

    fun sendMessage(message: Message): Long {
        return messageDao.insert(message)
    }

    fun getMessageById(messageId: String): Message? {
        return messageDao.getById(messageId)
    }

    fun updateMessageStatus(messageId: String, status: MessageStatus) {
        messageDao.updateStatus(messageId, status)
    }

    fun getMessageCount(conversationId: String): Int {
        return messageDao.getMessageCount(conversationId)
    }
}

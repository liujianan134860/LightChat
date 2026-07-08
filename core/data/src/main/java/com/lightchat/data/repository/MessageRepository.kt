package com.lightchat.data.repository

import com.lightchat.data.local.dao.MessageDao
import com.lightchat.model.Message
import com.lightchat.model.MessageStatus
import com.lightchat.domain.repository.MessageRepositoryContract

class MessageRepository(private val messageDao: MessageDao) : MessageRepositoryContract {

    override fun getMessages(conversationId: String, limit: Int): List<Message> {
        return messageDao.getLatestMessages(conversationId, limit)
    }

    override fun getMessagesAround(conversationId: String, messageId: String): List<Message> {
        return messageDao.getMessagesAround(conversationId, messageId)
    }

    override fun loadMoreMessages(conversationId: String, beforeConversationSeq: Long, limit: Int): List<Message> {
        return messageDao.getMessagesBeforeSeq(conversationId, beforeConversationSeq, limit)
    }

    override fun loadOlderMessages(conversationId: String, beforeMessage: Message, limit: Int): List<Message> {
        return if (beforeMessage.conversationSeq > 0L) {
            messageDao.getMessagesBeforeSeq(conversationId, beforeMessage.conversationSeq, limit)
        } else {
            messageDao.getMessagesBefore(conversationId, beforeMessage.createTime, limit)
        }
    }

    override fun loadNewerMessages(conversationId: String, afterMessage: Message, limit: Int): List<Message> {
        return messageDao.getMessagesAfter(conversationId, afterMessage, limit)
    }

    override fun hasMessagesBefore(conversationId: String, message: Message): Boolean {
        return messageDao.hasMessagesBefore(conversationId, message)
    }

    override fun hasMessagesAfter(conversationId: String, message: Message): Boolean {
        return messageDao.hasMessagesAfter(conversationId, message)
    }

    override fun sendMessage(message: Message): Long {
        return messageDao.insert(message)
    }

    override fun getMessageById(messageId: String): Message? {
        return messageDao.getById(messageId)
    }

    override fun updateMessageStatus(messageId: String, status: MessageStatus) {
        messageDao.updateStatus(messageId, status)
    }

    override fun getMessageCount(conversationId: String): Int {
        return messageDao.getMessageCount(conversationId)
    }
}

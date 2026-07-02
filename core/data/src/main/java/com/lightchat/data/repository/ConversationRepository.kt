package com.lightchat.data.repository

import com.lightchat.data.local.dao.ConversationDao
import com.lightchat.event.AppEvents
import com.lightchat.im.ImClient
import com.lightchat.model.Conversation

class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val imClient: ImClient
) {

    fun getConversations(): List<Conversation> {
        return conversationDao.getAllVisible()
    }

    fun getConversationsPage(limit: Int = 30, offset: Int = 0): List<Conversation> {
        return conversationDao.getVisiblePage(limit, offset)
    }

    fun getConversationCount(): Int {
        return conversationDao.getVisibleCount()
    }

    fun getConversation(conversationId: String): Conversation? {
        return conversationDao.getById(conversationId)
    }

    fun saveConversation(conversation: Conversation) {
        conversationDao.insert(conversation)
        AppEvents.notifyConversationChanged(conversation.conversationId)
    }

    fun updateLastMessage(conversationId: String, messageId: String, content: String, time: Long, thumbnail: String? = null) {
        conversationDao.updateLastMessage(conversationId, messageId, content, time, thumbnail)
        AppEvents.notifyMessageChanged(conversationId)
    }

    fun incrementUnread(conversationId: String): Int {
        return conversationDao.incrementUnread(conversationId).also {
            AppEvents.notifyConversationChanged(conversationId)
        }
    }

    fun clearUnread(conversationId: String) {
        conversationDao.clearUnread(conversationId)
        AppEvents.notifyConversationChanged(conversationId)
    }

    fun setPinned(conversationId: String, pinned: Boolean) {
        val time = System.currentTimeMillis()
        conversationDao.setPinned(conversationId, pinned, time)
        AppEvents.notifyConversationChanged(conversationId)
        syncToServer(conversationId, pinned, time, null)
    }

    fun setHidden(conversationId: String, hidden: Boolean) {
        conversationDao.setHidden(conversationId, hidden)
        AppEvents.notifyConversationChanged(conversationId)
    }

    fun deleteConversation(conversationId: String) {
        conversationDao.setDeleted(conversationId, true)
        AppEvents.notifyConversationChanged(conversationId)
    }

    fun setMute(conversationId: String, mute: Boolean) {
        conversationDao.setMute(conversationId, mute)
        AppEvents.notifyConversationChanged(conversationId)
        syncToServer(conversationId, null, null, mute)
    }

    private fun syncToServer(conversationId: String, isPinned: Boolean?, pinnedTime: Long?, mute: Boolean?) {
        val conv = conversationDao.getById(conversationId) ?: return
        val finalPinned = isPinned ?: conv.isPinned
        val finalTime = pinnedTime ?: conv.pinnedTime
        val finalMute = mute ?: conv.mute
        imClient.updateConversationSettings(conversationId, finalPinned, finalTime, finalMute)
    }
}

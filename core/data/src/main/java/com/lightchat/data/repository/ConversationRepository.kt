package com.lightchat.data.repository

import com.lightchat.data.local.dao.ConversationDao
import com.lightchat.event.AppEvents
import com.lightchat.im.ImClient
import com.lightchat.model.Conversation
import com.lightchat.domain.repository.ConversationRepositoryContract

class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val imClient: ImClient
) : ConversationRepositoryContract {

    override fun getConversations(): List<Conversation> {
        return conversationDao.getAllVisible()
    }

    override fun getConversationsPage(limit: Int, offset: Int): List<Conversation> {
        return conversationDao.getVisiblePage(limit, offset)
    }

    override fun getConversationCount(): Int {
        return conversationDao.getVisibleCount()
    }

    override fun getConversation(conversationId: String): Conversation? {
        return conversationDao.getById(conversationId)
    }

    override fun saveConversation(conversation: Conversation) {
        conversationDao.insert(conversation)
        AppEvents.notifyConversationChanged(conversation.conversationId)
    }

    override fun updateLastMessage(conversationId: String, messageId: String, content: String, time: Long, thumbnail: String?) {
        conversationDao.updateLastMessage(conversationId, messageId, content, time, thumbnail)
        AppEvents.notifyMessageChanged(conversationId)
    }

    override fun incrementUnread(conversationId: String): Int {
        return conversationDao.incrementUnread(conversationId).also {
            AppEvents.notifyConversationChanged(conversationId)
        }
    }

    override fun clearUnread(conversationId: String) {
        conversationDao.clearUnread(conversationId)
        AppEvents.notifyConversationChanged(conversationId)
    }

    override fun setPinned(conversationId: String, pinned: Boolean) {
        val time = System.currentTimeMillis()
        conversationDao.setPinned(conversationId, pinned, time)
        AppEvents.notifyConversationChanged(conversationId)
        syncToServer(conversationId, pinned, time, null)
    }

    override fun setHidden(conversationId: String, hidden: Boolean) {
        conversationDao.setHidden(conversationId, hidden)
        AppEvents.notifyConversationChanged(conversationId)
    }

    override fun deleteConversation(conversationId: String) {
        conversationDao.setDeleted(conversationId, true)
        AppEvents.notifyConversationChanged(conversationId)
    }

    override fun setMute(conversationId: String, mute: Boolean) {
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

package com.lightchat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.lightchat.data.local.DatabaseHelper
import com.lightchat.data.local.UserSession
import com.lightchat.data.local.dao.ConversationDao
import com.lightchat.data.local.dao.MessageDao
import com.lightchat.data.local.dao.UserDao
import com.lightchat.domain.repository.ConversationRepositoryContract
import com.lightchat.event.AppEvents
import com.lightchat.model.Conversation
import com.lightchat.model.ConversationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class ConversationListUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false
)

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val conversationRepository: ConversationRepositoryContract,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val userSession: UserSession,
    private val databaseHelper: DatabaseHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()
    private val pageSize = 80
    private var loadingMoreInFlight = false
    private var loadJob: Job? = null
    private var scheduledRefreshJob: Job? = null

    init {
        loadConversations()
        viewModelScope.launch {
            AppEvents.conversationChanged.collect {
                scheduleConversationRefresh()
            }
        }
    }

    fun loadConversations() {
        scheduledRefreshJob?.cancel()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadingMoreInFlight = false
            _uiState.value = _uiState.value.copy(isLoading = true)
            val start = System.currentTimeMillis()
            val (conversations, hasMore, totalCount) = withContext(Dispatchers.IO) {
                    val rawPage = conversationRepository.getConversationsPage(pageSize + 1, 0)
                val page = resolveDisplayInfo(rawPage.take(pageSize))
                Triple(page, rawPage.size > pageSize, conversationRepository.getConversationCount())
            }
            Log.d(
                "LightChatPerf",
                "conversationFirstPage count=${conversations.size} total=$totalCount cost=${System.currentTimeMillis() - start}ms"
            )
            _uiState.value = ConversationListUiState(
                conversations = conversations,
                hasMore = hasMore
            )
        }
    }

    private fun scheduleConversationRefresh() {
        scheduledRefreshJob?.cancel()
        scheduledRefreshJob = viewModelScope.launch {
            delay(180)
            loadConversations()
        }
    }

    fun loadMoreConversations() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || loadingMoreInFlight || !state.hasMore) return
        loadingMoreInFlight = true
        viewModelScope.launch {
            try {
                _uiState.value = state.copy(isLoadingMore = true)
                val start = System.currentTimeMillis()
                val (nextPage, hasMore) = withContext(Dispatchers.IO) {
                    val rawPage = conversationRepository.getConversationsPage(pageSize + 1, state.conversations.size)
                    resolveDisplayInfo(rawPage.take(pageSize)) to (rawPage.size > pageSize)
                }
                Log.d(
                    "LightChatPerf",
                    "conversationNextPage offset=${state.conversations.size} count=${nextPage.size} hasMore=$hasMore cost=${System.currentTimeMillis() - start}ms"
                )
                val merged = (state.conversations + nextPage).distinctBy { it.conversationId }
                _uiState.value = state.copy(
                    conversations = merged,
                    isLoadingMore = false,
                    hasMore = hasMore
                )
            } finally {
                if (_uiState.value.isLoadingMore) {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
                loadingMoreInFlight = false
            }
        }
    }

    private fun resolveDisplayInfo(conversations: List<Conversation>): List<Conversation> {
        val targetIds = conversations
            .filter { it.type == ConversationType.SINGLE }
            .map { resolveSingleTargetId(it) }
            .filter { it.isNotBlank() }
            .toSet()
        val users = userDao.getByIds(targetIds)
        return conversations.map { conv ->
                if (conv.type == ConversationType.SINGLE) {
                    val targetId = resolveSingleTargetId(conv)
                    val user = users[targetId]
                    val title = user?.nickname
                        ?.takeIf { it.isNotBlank() }
                        ?: conv.title.takeIf { it.isNotBlank() && it != conv.targetId }
                        ?: targetId
                    if (user != null) {
                        if (conv.title != title || conv.avatar != user.avatar || conv.avatarUrl != user.avatarUrl || conv.targetId != targetId) {
                            conversationDao.updateSingleDisplayInfo(conv.conversationId, targetId, title, user.avatar, user.avatarUrl, user.avatarVersion)
                        }
                        conv.copy(
                            targetId = targetId,
                            title = title,
                            avatar = user.avatar,
                            avatarUrl = user.avatarUrl,
                            avatarVersion = user.avatarVersion
                        )
                    } else {
                        if (conv.targetId != targetId || conv.title != title) {
                            conversationDao.updateSingleDisplayInfo(conv.conversationId, targetId, title, conv.avatar, conv.avatarUrl, conv.avatarVersion)
                        }
                        conv.copy(targetId = targetId, title = title)
                    }
                } else {
                    conv
                }
            }
    }

    private fun resolveSingleTargetId(conv: Conversation): String {
        val currentUserId = userSession.currentUserId
        if (conv.targetId.isNotBlank() && conv.targetId != currentUserId) return conv.targetId
        val parts = Regex("^single_(.+)_(.+)$").find(conv.conversationId)?.groupValues
        if (parts != null && parts.size >= 3) {
            return listOf(parts[1], parts[2]).firstOrNull { it != currentUserId } ?: conv.targetId
        }
        return conv.targetId
    }

    fun pinConversation(conversationId: String, pinned: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                conversationRepository.setPinned(conversationId, pinned)
            }
            loadConversations()
        }
    }

    fun muteConversation(conversationId: String, muted: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                conversationRepository.setMute(conversationId, muted)
            }
            loadConversations()
        }
    }

    fun hideConversation(conversationId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                conversationDao.setHidden(conversationId, true)
            }
            loadConversations()
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                conversationRepository.deleteConversation(conversationId)
            }
            loadConversations()
        }
    }

    fun markUnread(conversationId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                conversationDao.updateUnreadCount(conversationId, 1)
                val cv = android.content.ContentValues().apply { put("manual_unread", 1) }
                databaseHelper.writableDatabase.update(
                    "conversation",
                    cv,
                    "owner_user_id = ? AND conversation_id = ?",
                    arrayOf(databaseHelper.currentOwnerId(), conversationId)
                )
            }
            loadConversations()
        }
    }

    fun clearUnread(conversationId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                conversationRepository.clearUnread(conversationId)
            }
            loadConversations()
        }
    }

    fun clearMessages(conversationId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                messageDao.deleteByConversation(conversationId)
                conversationRepository.updateLastMessage(conversationId, "", "", 0L)
            }
            loadConversations()
        }
    }
}

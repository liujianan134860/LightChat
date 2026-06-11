package com.lightchat.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ConversationOpenTarget(
    val conversationId: String,
    val title: String,
    val targetMessageId: String = ""
)

object AppEvents {
    private val _conversationChanged = MutableSharedFlow<String?>(extraBufferCapacity = 64)
    val conversationChanged = _conversationChanged.asSharedFlow()

    private val _messageChanged = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messageChanged = _messageChanged.asSharedFlow()

    private val _userChanged = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val userChanged = _userChanged.asSharedFlow()

    private val _friendRequestChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    val friendRequestChanged = _friendRequestChanged.asSharedFlow()

    private val _forcedLogout = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val forcedLogout = _forcedLogout.asSharedFlow()

    private val _openConversation = MutableSharedFlow<ConversationOpenTarget>(extraBufferCapacity = 8)
    val openConversation = _openConversation.asSharedFlow()

    private val _openFriendRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val openFriendRequests = _openFriendRequests.asSharedFlow()

    fun notifyConversationChanged(conversationId: String? = null) {
        _conversationChanged.tryEmit(conversationId)
    }

    fun notifyMessageChanged(conversationId: String) {
        _messageChanged.tryEmit(conversationId)
        notifyConversationChanged(conversationId)
    }

    fun notifyUserChanged(userId: String) {
        _userChanged.tryEmit(userId)
    }

    fun notifyFriendRequestChanged() {
        _friendRequestChanged.tryEmit(Unit)
    }

    fun notifyForcedLogout(message: String) {
        _forcedLogout.tryEmit(message)
    }

    fun notifyOpenConversation(conversationId: String, title: String, targetMessageId: String = "") {
        _openConversation.tryEmit(ConversationOpenTarget(conversationId, title, targetMessageId))
    }

    fun notifyOpenFriendRequests() {
        _openFriendRequests.tryEmit(Unit)
    }
}

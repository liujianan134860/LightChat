package com.lightchat.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lightchat.data.local.UserSession
import com.lightchat.domain.session.AppPresence
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MockVendorPushReceiver : BroadcastReceiver() {
    @Inject lateinit var userSession: UserSession
    @Inject lateinit var appPresence: AppPresence
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MOCK_VENDOR_PUSH) return
        val targetUserId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        if (targetUserId.isNotBlank() && targetUserId != currentUserId(context)) return

        val pushType = intent.getStringExtra(EXTRA_PUSH_TYPE).orEmpty()
        if (pushType == PUSH_TYPE_FRIEND_REQUEST) {
            NotificationHelper.showFriendRequest(
                context = context,
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "新的朋友" },
                content = intent.getStringExtra(EXTRA_CONTENT).orEmpty().ifBlank { "你收到了一条好友请求" }
            )
            return
        }
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()
        if (conversationId.isBlank()) return
        if (appPresence.isForeground && appPresence.currentConversationId == conversationId) return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "LightChat" }
        val content = intent.getStringExtra(EXTRA_CONTENT).orEmpty().ifBlank { "你收到了一条新消息" }
        NotificationHelper.showMessage(
            context = context,
            conversationId = conversationId,
            title = title,
            content = content
        )
    }

    private fun currentUserId(context: Context): String? {
        return userSession.currentUserId
    }

    companion object {
        const val ACTION_MOCK_VENDOR_PUSH = "com.lightchat.action.MOCK_VENDOR_PUSH"
        const val EXTRA_USER_ID = "userId"
        const val EXTRA_PUSH_TYPE = "pushType"
        const val EXTRA_CONVERSATION_ID = "conversationId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CONTENT = "content"
        const val PUSH_TYPE_FRIEND_REQUEST = "friend_request"
    }
}

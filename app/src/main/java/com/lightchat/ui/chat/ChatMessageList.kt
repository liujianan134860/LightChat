package com.lightchat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightchat.LightChatApplication
import com.lightchat.model.Message
import com.lightchat.model.MessageType
import com.lightchat.model.User
import com.lightchat.ui.theme.HighlightBackground
import com.lightchat.ui.theme.TextSecondary
import com.lightchat.ui.theme.WeChatBg
import com.lightchat.viewmodel.ChatUiState
import org.json.JSONObject

@Composable
fun ChatMessageList(
    uiState: ChatUiState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    isMultiSelectMode: Boolean,
    selectedMessageIds: Set<String>,
    highlightedMessageId: String? = null,
    isGroupChat: Boolean = true,
    messageAreaInteraction: MutableInteractionSource,
    contentPadding: PaddingValues,
    onCloseBottomPanels: () -> Unit,
    onAvatarClick: (String) -> Unit,
    onUserCardClick: (String) -> Unit,
    onRecallEdit: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onRetryMessage: (Message) -> Unit,
    onMessageLongPress: (Message, Rect) -> Unit,
    onMessageSelectionToggle: (String) -> Unit,
    onMergeForwardClick: (String) -> Unit
) {
    val currentUserId = LightChatApplication.instance.userSession.currentUserId
    val lastOwnMessageId = uiState.messages
        .lastOrNull { it.senderId == currentUserId && !it.isRecalled && it.messageType != MessageType.SYSTEM }
        ?.messageId
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(WeChatBg)
            .clickable(
                interactionSource = messageAreaInteraction,
                indication = null
            ) { onCloseBottomPanels() },
        state = listState,
        contentPadding = contentPadding
    ) {
        if (uiState.isLoadingMoreMessages) {
            item(key = "load_more_messages") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = TextSecondary
                    )
                }
            }
        }
        uiState.messages.forEachIndexed { index, message ->
            val previous = uiState.messages.getOrNull(index - 1)
            val isLastOwnMessage = message.messageId == lastOwnMessageId
            if (shouldShowTimestamp(previous, message)) {
                item(key = "time_${message.messageId}") {
                    ChatTimestamp(formatChatTimestamp(message.createTime))
                }
            }
            item(key = message.messageId) {
                val highlighted = message.messageId == highlightedMessageId
                val groupReadCount = uiState.groupReadCounts[message.messageId]
                val groupReadText = when {
                    isGroupChat && groupReadCount?.hasMentionTargets == true -> mentionReadText(groupReadCount)
                    isGroupChat && groupReadCount != null && isLastOwnMessage -> "${groupReadCount.readCount}/${groupReadCount.totalCount} 已读"
                    else -> null
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            LightChatApplication.instance.messageBubbleBounds[message.messageId] =
                                coordinates.boundsInWindow()
                        }
                        .background(if (highlighted) HighlightBackground else Color.Transparent)
                ) {
                    ChatBubble(
                        message = message,
                        showStatus = message.senderId == LightChatApplication.instance.userSession.currentUserId &&
                            (groupReadText != null || (!isGroupChat && message.messageId == uiState.messages.lastOrNull()?.messageId)),
                        groupReadText = groupReadText,
                        isSelected = message.messageId in selectedMessageIds,
                        isMultiSelectMode = isMultiSelectMode,
                        isGroupChat = isGroupChat,
                        onAvatarClick = onAvatarClick,
                        onUserCardClick = onUserCardClick,
                        onRecallEdit = { onRecallEdit(message.messageId) },
                        onImageClick = { onImageClick(message.messageId) },
                        onRetry = { onRetryMessage(message) },
                        onLongPress = { bounds ->
                            onMessageLongPress(
                                message,
                                bounds.takeUnless { it == Rect.Zero }
                                    ?: LightChatApplication.instance.messageBubbleBounds[message.messageId]
                                    ?: Rect.Zero
                            )
                        },
                        onTap = {
                            when {
                                isMultiSelectMode -> onMessageSelectionToggle(message.messageId)
                                message.messageType == MessageType.MERGE_FORWARD -> onMergeForwardClick(message.messageId)
                                message.messageType == MessageType.USER_CARD -> {
                                    saveUserCardSnapshot(message)
                                    val userId = parseUserCardId(message)
                                    if (userId.isNotBlank()) onUserCardClick(userId)
                                }
                            }
                        }
                )
            }
        }
        if (uiState.isLoadingNewerMessages) {
            item(key = "load_newer_messages") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
}

private fun saveUserCardSnapshot(message: Message) {
    try {
        val obj = JSONObject(message.extra ?: "{}")
        val userId = obj.optString("userId", "")
        if (userId.isBlank()) return
        LightChatApplication.instance.userDao.upsertPreservingExisting(
            User(
                userId = userId,
                nickname = obj.optString("nickname", message.content).ifBlank { message.content.ifBlank { userId } },
                avatar = obj.optString("avatar", "")
            )
        )
    } catch (_: Exception) {
    }
}

private fun mentionReadText(readCount: com.lightchat.viewmodel.GroupReadCount): String {
    val parts = mutableListOf<String>()
    if (readCount.mentionedReadNames.isNotEmpty()) {
        parts.add("${readCount.mentionedReadNames.joinToString(",")}已读")
    }
    if (readCount.mentionedUnreadNames.isNotEmpty()) {
        parts.add("${readCount.mentionedUnreadNames.joinToString(",")}未读")
    }
    return parts.joinToString("，").ifBlank { "未读" }
}

@Composable
private fun ChatTimestamp(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}

private fun parseUserCardId(message: Message): String {
    return try {
        JSONObject(message.extra ?: "{}").optString("userId", "")
    } catch (_: Exception) {
        ""
    }
}

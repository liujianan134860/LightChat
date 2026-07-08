package com.lightchat.ui.conversation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import com.lightchat.LightChatApplication
import com.lightchat.R
import com.lightchat.model.Conversation
import com.lightchat.model.ConversationType
import com.lightchat.model.Message
import com.lightchat.model.MessageType
import com.lightchat.ui.theme.*
import com.lightchat.viewmodel.ConversationListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val LIGHTCHAT_APP_ICON_AVATAR = "lightchat://app-icon"
private const val CONVERSATION_PREFETCH_THRESHOLD = 25

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    modifier: Modifier = Modifier,
    onChatClick: (conversationId: String, title: String, targetMessageId: String) -> Unit,
    onSearchClick: () -> Unit,
    onCreateGroup: () -> Unit,
    onAddFriend: () -> Unit,
    viewModel: ConversationListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val openScope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var contextConv by remember { mutableStateOf<Conversation?>(null) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            Triple(lastVisibleIndex, uiState.conversations.size, uiState.hasMore)
        }
            .distinctUntilChanged()
            .collect { (lastVisibleIndex, loadedCount, hasMore) ->
                if (hasMore && loadedCount > 0 && lastVisibleIndex >= loadedCount - CONVERSATION_PREFETCH_THRESHOLD) {
                    viewModel.loadMoreConversations()
                }
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text("消息", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            },
            actions = {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "搜索", tint = Color.Black)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = "更多", tint = Color.Black)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = InputStrokeLight
                    ) {
                        DropdownMenuItem(
                            text = { Text("发起群聊") },
                            onClick = {
                                showMenu = false
                                onCreateGroup()
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = DividerColor,
                            thickness = 0.5.dp
                        )
                        DropdownMenuItem(
                            text = { Text("添加朋友") },
                            onClick = {
                                showMenu = false
                                onAddFriend()
                            }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarBackground)
        )

        HorizontalDivider(thickness = 0.5.dp, color = DividerColor)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(WeChatWhite),
            state = listState
        ) {
            items(uiState.conversations, key = { it.conversationId }) { conversation ->
                Box {
                    ConversationItem(
                        conversation = conversation,
                        onClick = {
                            openScope.launch {
                                val targetMessageId = withContext(Dispatchers.IO) {
                                    findMentionTargetMessageId(conversation)
                                }
                                viewModel.clearUnread(conversation.conversationId)
                                prewarmConversation(conversation.conversationId)
                                onChatClick(conversation.conversationId, conversation.title, targetMessageId)
                            }
                        },
                        onLongPress = { contextConv = conversation }
                    )
                    ConversationContextMenu(
                        expanded = contextConv?.conversationId == conversation.conversationId,
                        conversation = conversation,
                        onDismiss = { contextConv = null },
                        onToggleMute = {
                            viewModel.muteConversation(conversation.conversationId, !conversation.mute)
                            contextConv = null
                        },
                        onTogglePin = {
                            viewModel.pinConversation(conversation.conversationId, !conversation.isPinned)
                            contextConv = null
                        },
                        onHide = {
                            viewModel.hideConversation(conversation.conversationId)
                            contextConv = null
                        },
                        onDelete = {
                            viewModel.deleteConversation(conversation.conversationId)
                            contextConv = null
                        }
                    )
                }
            }
            if (uiState.isLoadingMore) {
                item(key = "loading_more_conversations") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = WeChatGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationContextMenu(
    expanded: Boolean,
    conversation: Conversation,
    onDismiss: () -> Unit,
    onToggleMute: () -> Unit,
    onTogglePin: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(160.dp),
        containerColor = Color.White
    ) {
        DropdownMenuItem(
            text = { Text(if (conversation.mute) "取消免打扰" else "设为免打扰", fontSize = 16.sp) },
            leadingIcon = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
            onClick = onToggleMute
        )
        HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
        DropdownMenuItem(
            text = { Text(if (conversation.isPinned) "取消置顶" else "置顶", fontSize = 16.sp) },
            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
            onClick = onTogglePin
        )
        HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
        DropdownMenuItem(
            text = { Text("不显示", fontSize = 16.sp) },
            leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
            onClick = onHide
        )
        HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
        DropdownMenuItem(
            text = { Text("删除", fontSize = 16.sp, color = UnreadRed) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = UnreadRed) },
            onClick = onDelete
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val targetUser = remember(conversation.targetId) {
        if (conversation.type == ConversationType.SINGLE) LightChatApplication.instance.userDao.getById(conversation.targetId) else null
    }
    val avatarBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, targetUser?.avatarUrl, targetUser?.avatarVersion) {
        value = if (targetUser?.avatarUrl?.isNotBlank() == true) {
            withContext(Dispatchers.IO) {
                com.lightchat.ui.components.AvatarCacheLoader.loadAvatar(
                    context = context,
                    userId = targetUser.userId,
                    avatarUrl = targetUser.avatarUrl,
                    avatarVersion = targetUser.avatarVersion,
                    avatarFallback = "",
                    allowNetwork = true
                ).bitmap
            }
        } else {
            withContext(Dispatchers.Default) { null }
        }
    }
    val showAppIcon = conversation.avatar == LIGHTCHAT_APP_ICON_AVATAR
    val displayInitial = conversation.title.take(1).ifBlank { conversation.targetId.take(1) }
    val rowBackground = if (conversation.isPinned) TopBarBackground else WeChatWhite
    val mentionPreview = remember(
        conversation.conversationId,
        conversation.atMe,
        conversation.atMeCount,
        conversation.lastMessageId
    ) {
        findMentionPreview(conversation)
    }
    val previewText = mentionPreview.ifBlank { conversation.lastMessageContent }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .background(rowBackground)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(58.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (conversation.type.value == 1) GroupAccentBlue.copy(alpha = 0.2f)
                        else WeChatGreen.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (showAppIcon) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.mipmap.ic_launcher),
                        contentDescription = "LightChat图标",
                        modifier = Modifier.fillMaxSize()
                    )
                } else avatarBitmap?.let { bitmap ->
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "头像",
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: run {
                    Text(
                        text = if (conversation.type.value == 1) "群" else displayInitial,
                        color = if (conversation.type.value == 1) GroupAccentBlue else WeChatGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
            if (conversation.unreadCount > 0) {
                if (conversation.mute) {
                    StaticUnreadDot(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-5).dp, y = 5.dp)
                    )
                } else {
                    StaticUnreadBadge(
                        count = conversation.unreadCount,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.title,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (conversation.isPinned) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("[置顶]", fontSize = 11.sp, color = TextSecondary)
                }
                if (conversation.mute) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = "免打扰",
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row {
                if (conversation.atMe) {
                    Text(
                        text = if (conversation.atMeCount > 1) "[有人@我]*${conversation.atMeCount} " else "[有人@我] ",
                        fontSize = 13.sp,
                        color = UnreadRed,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = previewText,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = formatTime(conversation.lastMessageTime), fontSize = 11.sp, color = TextSecondary)
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 80.dp),
        color = DividerColor,
        thickness = 0.5.dp
    )
}

@Composable
private fun StaticUnreadDot(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(UnreadRed)
    )
}

@Composable
private fun StaticUnreadBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Badge(containerColor = UnreadRed) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                fontSize = 11.sp,
                color = WeChatWhite
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        diff < 172_800_000 -> "昨天"
        else -> "${diff / 86_400_000}天前"
    }
}

private suspend fun prewarmConversation(conversationId: String) {
    withTimeoutOrNull(180) {
        withContext(Dispatchers.IO) {
            val app = LightChatApplication.instance
            app.conversationRepository.getConversation(conversationId)
            app.messageRepository.getMessages(conversationId)
            app.messageRepository.getMessageCount(conversationId)
        }
    }
}

private fun findMentionTargetMessageId(conversation: Conversation): String {
    if (!conversation.atMe) return ""
    val app = LightChatApplication.instance
    val currentUserId = app.userSession.currentUserId ?: return ""
    return app.messageDao.findFirstUnreadMentionForUser(
        conversation.conversationId,
        currentUserId,
        conversation.atMeCount
    )?.messageId.orEmpty()
}

private fun findMentionPreview(conversation: Conversation): String {
    if (!conversation.atMe) return ""
    val app = LightChatApplication.instance
    val currentUserId = app.userSession.currentUserId ?: return ""
    val message = app.messageDao.findLatestMentionForUser(conversation.conversationId, currentUserId) ?: return ""
    val senderName = app.userDao.getById(message.senderId)?.nickname?.takeIf { it.isNotBlank() }
        ?: message.senderId
    return "$senderName: ${messagePreviewText(message)}"
}

private fun messagePreviewText(message: Message): String = when (message.messageType) {
    MessageType.IMAGE -> "[图片]"
    MessageType.USER_CARD -> "[名片]${message.content}"
    MessageType.GROUP_CARD -> "[群名片]${message.content}"
    MessageType.MERGE_FORWARD -> "[聊天记录]"
    else -> message.content
}

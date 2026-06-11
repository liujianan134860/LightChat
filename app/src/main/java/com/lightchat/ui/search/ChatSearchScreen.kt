package com.lightchat.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightchat.LightChatApplication
import com.lightchat.model.Message
import com.lightchat.model.MessageType
import com.lightchat.ui.components.LightChatAvatar
import com.lightchat.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSearchScreen(
    conversationId: String,
    title: String,
    onBack: () -> Unit,
    onMessageClick: (String) -> Unit = {}
) {
    val app = LightChatApplication.instance
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Message>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }
    var visibleCount by remember { mutableIntStateOf(50) }
    val listState = rememberLazyListState()

    LaunchedEffect(query) {
        visibleCount = 50
    }

    LaunchedEffect(listState, results.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= visibleCount - 10 && visibleCount < results.size) {
                    visibleCount = (visibleCount + 50).coerceAtMost(results.size)
                }
            }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("搜索聊天记录", color = Color.Black) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarBackground)
                )
                HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(SectionBackground)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { newQuery ->
                    query = newQuery
                    if (newQuery.isNotEmpty()) {
                        hasSearched = true
                        results = app.messageDao.searchMessages(conversationId, newQuery)
                    } else {
                        results = emptyList()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索\"$title\"的聊天记录") },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = WeChatWhite,
                    unfocusedContainerColor = WeChatWhite,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "清除")
                        }
                    }
                }
            )

            if (query.isEmpty() && !hasSearched) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("输入关键词搜索聊天记录", color = TextSecondary, fontSize = 14.sp)
                }
            } else if (results.isEmpty() && hasSearched) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("未找到相关消息", color = TextSecondary, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    item {
                        Text(
                            "找到 ${results.size} 条消息",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(results.take(visibleCount), key = { it.messageId }) { message ->
                        ChatSearchResultItem(
                            message = message,
                            onClick = { onMessageClick(message.messageId) }
                        )
                    }
                    if (visibleCount < results.size) {
                        item(key = "chat_search_loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = WeChatGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatSearchResultItem(
    message: Message,
    onClick: () -> Unit
) {
    val app = LightChatApplication.instance
    val senderUser = remember(message.senderId) { app.userDao.getById(message.senderId) }
    val senderName = senderUser?.nickname ?: message.senderId
    val displayText = remember(message.messageType, message.content) { message.searchDisplayText() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = WeChatWhite)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            LightChatAvatar(
                avatar = senderUser?.avatar.orEmpty(),
                avatarUrl = senderUser?.avatarUrl.orEmpty(),
                avatarVersion = senderUser?.avatarVersion ?: 0,
                userId = senderUser?.userId.orEmpty(),
                name = senderName,
                size = 36.dp,
                allowNetwork = true
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = senderName,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayText,
                    fontSize = 14.sp,
                    color = Color.Black,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun Message.searchDisplayText(): String = when (messageType) {
    MessageType.IMAGE -> "[图片]"
    MessageType.USER_CARD -> "[名片]$content"
    MessageType.GROUP_CARD -> "[群名片]$content"
    MessageType.MERGE_FORWARD -> "[聊天记录]"
    else -> content
}

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightchat.LightChatApplication
import com.lightchat.model.Conversation
import com.lightchat.model.ConversationType
import com.lightchat.model.Message
import com.lightchat.model.MessageType
import com.lightchat.model.User
import com.lightchat.ui.components.LightChatAvatar
import com.lightchat.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
private data class GlobalMessageSearchResult(
    val conversation: Conversation,
    val message: Message
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onChatClick: (conversationId: String, title: String, targetMessageId: String) -> Unit,
    onContactClick: (userId: String) -> Unit
) {
    val app = LightChatApplication.instance
    var query by remember { mutableStateOf("") }
    val currentUserId = app.userSession.currentUserId
    val listState = rememberLazyListState()
    var visibleContactCount by remember { mutableIntStateOf(30) }
    var visibleMessageCount by remember { mutableIntStateOf(30) }

    LaunchedEffect(query) {
        visibleContactCount = 30
        visibleMessageCount = 30
    }

    // Search results
    val allConversations = remember { app.conversationDao.getAllForSearch() }
    val allUsers = remember {
        val friends = app.userDao.getFriends(currentUserId ?: "")
        // Also search all users in the DB (for friend adding)
        friends + app.userDao.getAll().filter { it.userId != currentUserId && it !in friends }
    }

    val messageResults = remember(query, allConversations) {
        if (query.isBlank()) {
            emptyList()
        } else {
            val conversationById = allConversations.associateBy { it.conversationId }
            app.messageDao.searchMessagesGlobal(query, 50).mapNotNull { message ->
                conversationById[message.conversationId]?.let { conversation ->
                    GlobalMessageSearchResult(conversation, message)
                }
            }
        }
    }

    val contactResults = remember(query, allUsers) {
        if (query.isBlank()) {
            emptyList()
        } else {
            allUsers.filter {
                it.nickname.contains(query, ignoreCase = true) ||
                        it.userId.contains(query, ignoreCase = true)
            }
        }
    }

    val visibleContacts = contactResults.take(visibleContactCount)
    val visibleMessages = messageResults.take(visibleMessageCount)
    val catCount = listOf(
        contactResults.isNotEmpty(),
        messageResults.isNotEmpty()
    ).count { it }
    val visibleTotal = visibleContacts.size + visibleMessages.size + catCount

    LaunchedEffect(listState, query, contactResults.size, messageResults.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (query.isNotBlank() && lastVisible >= visibleTotal - 8) {
                    when {
                        visibleContactCount < contactResults.size -> {
                            visibleContactCount =
                                (visibleContactCount + 30).coerceAtMost(contactResults.size)
                        }

                        visibleMessageCount < messageResults.size -> {
                            visibleMessageCount =
                                (visibleMessageCount + 30).coerceAtMost(messageResults.size)
                        }
                    }
                }
            }
    }

    Scaffold(
        topBar = {
            SearchTopBar(
                query = query,
                onQueryChange = { query = it },
                onBack = onBack
            )
        }
    ) { padding ->
        if (query.isBlank()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(WeChatWhite),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "搜索聊天记录、联系人",
                    color = TextSecondary,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(WeChatWhite)
            ) {
                // Contact results
                if (contactResults.isNotEmpty()) {
                    item {
                        Text(
                            text = "联系人",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }

                    items(visibleContacts, key = { it.userId }) { user ->
                        SearchContactItem(
                            user = user,
                            onClick = { onContactClick(user.userId) }
                        )
                    }
                }

                // Message results
                if (messageResults.isNotEmpty()) {
                    item {
                        Text(
                            text = "聊天记录",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }

                    items(visibleMessages, key = { it.message.messageId }) { result ->
                        SearchMessageItem(
                            conversation = result.conversation,
                            matchedContent = result.message.searchDisplayText(),
                            onClick = {
                                onChatClick(
                                    result.conversation.conversationId,
                                    result.conversation.title,
                                    result.message.messageId
                                )
                            }
                        )
                    }
                }

                if (
                    visibleContactCount < contactResults.size ||
                    visibleMessageCount < messageResults.size
                ) {
                    item(key = "search_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = WeChatGreen
                            )
                        }
                    }
                }

                if (contactResults.isEmpty() && messageResults.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "未找到相关结果",
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit
) {
    // 使用 Surface 和 Row 代替 TopAppBar 以解决预览中的 NoSuchMethodError
    // 这是由于 Material3 TopAppBar 在某些 Android Studio 预览环境下存在二进制不兼容问题（常见于版本升级期间）
    Surface(
        color = TopBarBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                    .height(64.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .background(
                            color = WeChatWhite,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            color = TextPrimary
                        ),
                        cursorBrush = SolidColor(WeChatGreen),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (query.isEmpty()) {
                                        Text(
                                            text = "搜索聊天记录或联系人",
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    innerTextField()
                                }
                                if (query.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "清除",
                                        tint = TextSecondary,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { onQueryChange("") }
                                    )
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = DividerColor
            )
        }
    }
}

@Composable
private fun SearchContactItem(
    user: User,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WeChatWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LightChatAvatar(
            avatar = user.avatar,
            avatarUrl = user.avatarUrl,
            avatarVersion = user.avatarVersion,
            userId = user.userId,
            name = user.nickname.ifBlank { user.userId },
            size = 44.dp,
            allowNetwork = true
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = user.nickname,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )

            Text(
                text = "ID: ${user.userId}",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = DividerColor,
        thickness = 0.5.dp
    )
}

@Composable
private fun SearchMessageItem(
    conversation: Conversation,
    matchedContent: String,
    onClick: () -> Unit
) {
    val targetUser = remember(conversation.targetId) {
        if (conversation.type == ConversationType.SINGLE) LightChatApplication.instance.userDao.getById(conversation.targetId) else null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WeChatWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LightChatAvatar(
            avatar = targetUser?.avatar ?: conversation.avatar,
            avatarUrl = targetUser?.avatarUrl ?: "",
            avatarVersion = targetUser?.avatarVersion ?: 0,
            userId = targetUser?.userId ?: "",
            name = conversation.title,
            size = 44.dp,
            allowNetwork = true
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = conversation.title,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )

            Text(
                text = matchedContent,
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = DividerColor,
        thickness = 0.5.dp
    )
}

private fun Message.searchDisplayText(): String = when (messageType) {
    MessageType.IMAGE -> "[图片]"
    MessageType.USER_CARD -> "[名片]$content"
    MessageType.GROUP_CARD -> "[群名片]$content"
    MessageType.MERGE_FORWARD -> "[聊天记录]"
    else -> content
}

@Preview(
    name = "搜索顶部栏预览",
    showBackground = true,
    backgroundColor = 0xFFF7F7F7
)
@Composable
private fun SearchTopBarPreview() {
    LightChatTheme {
        SearchTopBar(
            query = "",
            onQueryChange = {},
            onBack = {}
        )
    }
}

@Preview(
    name = "搜索顶部栏-有输入内容",
    showBackground = true,
    backgroundColor = 0xFFF7F7F7
)
@Composable
private fun SearchTopBarWithTextPreview() {
    LightChatTheme {
        SearchTopBar(
            query = "测试搜索内容",
            onQueryChange = {},
            onBack = {}
        )
    }
}

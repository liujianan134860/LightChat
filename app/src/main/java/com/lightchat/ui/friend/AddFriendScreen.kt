package com.lightchat.ui.friend

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.lightchat.LightChatApplication
import com.lightchat.data.remote.AuthApiClient
import com.lightchat.model.Conversation
import com.lightchat.model.ConversationId
import com.lightchat.model.ConversationType
import com.lightchat.model.User
import com.lightchat.ui.components.AvatarCacheLoader
import com.lightchat.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFriendScreen(
    onBack: () -> Unit,
    onChatClick: (conversationId: String, title: String) -> Unit,
    onProfileClick: (userId: String) -> Unit
) {
    val app = LightChatApplication.instance
    val authApiClient = remember { AuthApiClient() }
    var query by remember { mutableStateOf("") }
    var remoteUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val currentUserId = app.userSession.currentUserId
    val scope = rememberCoroutineScope()

    val friendIds = remember {
        app.userDao.getFriends(currentUserId ?: "").map { it.userId }.toSet()
    }

    fun searchByExactId() {
        if (query.isBlank()) {
            hasSearched = false
            remoteUsers = emptyList()
            searchError = null
            return
        }

        scope.launch {
            hasSearched = true
            isSearching = true
            searchError = null

            val keyword = query.trim()
            val token = app.tokenManager.getToken()

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    authApiClient.searchUsers(keyword, token)
                }
            }

            result.fold(
                onSuccess = { users ->
                    val filtered = users.filter {
                        it.userId != currentUserId && it.userId == keyword
                    }

                    withContext(Dispatchers.IO) {
                        filtered.forEach {
                            app.userDao.upsertPreservingExisting(it)
                        }
                    }

                    remoteUsers = filtered
                    isSearching = false
                },
                onFailure = {
                    val local = app.userDao.getById(keyword)

                    remoteUsers = if (local != null && local.userId != currentUserId) {
                        listOf(local)
                    } else {
                        emptyList()
                    }

                    searchError = "无法连接服务端，已显示本地缓存"
                    isSearching = false
                }
            )
        }
    }

    val searchResults = remember(remoteUsers) {
        remoteUsers.take(30)
    }

    Scaffold(
        topBar = {
            AddFriendTopBar(
                query = query,
                onQueryChange = { query = it },
                onBack = onBack,
                onSearchClick = { searchByExactId() }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(WeChatWhite)
        ) {
            if (isSearching) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = WeChatGreen)
                    }
                }
            }

            searchError?.let { error ->
                item {
                    Text(
                        text = error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            if (!isSearching && hasSearched && searchResults.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "未找到用户",
                            color = TextSecondary
                        )
                    }
                }
            } else {
                items(searchResults) { user ->
                    val isFriend = friendIds.contains(user.userId)

                    AddFriendItem(
                        user = user,
                        isFriend = isFriend,
                        onChatClick = {
                            val convId = ConversationId.single(
                                currentUserId ?: "",
                                user.userId
                            )

                            if (app.conversationRepository.getConversation(convId) == null) {
                                app.conversationRepository.saveConversation(
                                    Conversation(
                                        conversationId = convId,
                                        type = ConversationType.SINGLE,
                                        targetId = user.userId,
                                        title = user.nickname,
                                        lastMessageTime = System.currentTimeMillis()
                                    )
                                )
                            }

                            onChatClick(convId, user.nickname)
                        },
                        onProfileClick = {
                            onProfileClick(user.userId)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFriendTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onSearchClick: () -> Unit
) {
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
                                            text = "输入用户ID精确搜索",
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    innerTextField()
                                }
                                if (query.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
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

                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = TextPrimary
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = DividerColor
            )
        }
    }
}

@Composable
private fun AddFriendItem(
    user: User,
    isFriend: Boolean,
    onChatClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val context = LocalContext.current
    val avatarBitmap by produceState<Bitmap?>(initialValue = null, user.avatarUrl, user.avatarVersion) {
        value = if (user.avatarUrl.isNotBlank()) {
            withContext(Dispatchers.IO) {
                AvatarCacheLoader.loadAvatar(
                    context = context,
                    userId = user.userId,
                    avatarUrl = user.avatarUrl,
                    avatarVersion = user.avatarVersion,
                    avatarFallback = "",
                    allowNetwork = true
                ).bitmap
            }
        } else {
            null
        }
    }

    val avatarColor = remember(user.avatar) {
        user.avatar.takeIf { it.startsWith("#") }?.let {
            try {
                Color(android.graphics.Color.parseColor(it))
            } catch (_: Exception) {
                WeChatGreen
            }
        } ?: WeChatGreen
    }

    val displayInitial = user.nickname.take(1).ifBlank {
        user.userId.take(1)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onProfileClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(avatarColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            val bm = avatarBitmap
            if (bm != null) {
                Image(
                    bitmap = bm.asImageBitmap(),
                    contentDescription = "头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = displayInitial,
                    color = avatarColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
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

        if (isFriend) {
            Text(
                text = "已添加",
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.width(8.dp))

            TextButton(onClick = onChatClick) {
                Text(
                    text = "发消息",
                    fontSize = 13.sp,
                    color = WeChatGreen
                )
            }
        } else {
            IconButton(onClick = onProfileClick) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "添加好友",
                    tint = WeChatGreen
                )
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = DividerColor,
        thickness = 0.5.dp
    )
}

@Preview(
    name = "添加好友顶部栏预览",
    showBackground = true,
    backgroundColor = 0xFFF7F7F7
)
@Composable
private fun AddFriendTopBarPreview() {
    LightChatTheme {
        AddFriendTopBar(
            query = "",
            onQueryChange = {},
            onBack = {},
            onSearchClick = {}
        )
    }
}

@Preview(
    name = "添加好友顶部栏-有输入内容",
    showBackground = true,
    backgroundColor = 0xFFF7F7F7
)
@Composable
private fun AddFriendTopBarWithTextPreview() {
    LightChatTheme {
        AddFriendTopBar(
            query = "10001",
            onQueryChange = {},
            onBack = {},
            onSearchClick = {}
        )
    }
}

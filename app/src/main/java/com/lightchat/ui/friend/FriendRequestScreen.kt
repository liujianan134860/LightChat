package com.lightchat.ui.friend

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightchat.LightChatApplication
import com.lightchat.event.AppEvents
import com.lightchat.model.FriendRequest
import com.lightchat.model.RequestStatus
import com.lightchat.model.User
import com.lightchat.ui.components.AvatarCacheLoader
import com.lightchat.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendRequestScreen(onBack: () -> Unit) {
    val app = LightChatApplication.instance
    val currentUserId = app.userSession.currentUserId
    val scope = rememberCoroutineScope()

    var requests by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }

    LaunchedEffect(currentUserId) {
        fun refreshRequests() {
            currentUserId?.let { requests = app.friendRequestDao.getPendingRequests(it) }
        }
        refreshRequests()
        AppEvents.friendRequestChanged.collect {
            refreshRequests()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("新的朋友") },
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
        if (requests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(WeChatWhite),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无好友申请", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(WeChatWhite)
            ) {
                items(requests, key = { it.requestId }) { request ->
                    FriendRequestItem(
                        request = request,
                        onAccept = {
                            scope.launch {
                                val uid = currentUserId ?: return@launch
                                app.friendRequestDao.updateStatus(request.requestId, RequestStatus.ACCEPTED)
                                val nickname = request.fromNickname.ifBlank { request.fromUserId }
                                val existing = app.userDao.getById(request.fromUserId)
                                if (existing == null) {
                                    app.userDao.insert(User(userId = request.fromUserId, nickname = nickname))
                                } else if (existing.nickname.isBlank() || existing.nickname == request.fromUserId) {
                                    app.userDao.update(existing.copy(nickname = nickname))
                                }
                                app.userDao.addFriend(uid, request.fromUserId)
                                app.userDao.addFriend(request.fromUserId, uid)
                                requests = app.friendRequestDao.getPendingRequests(uid)
                                AppEvents.notifyFriendRequestChanged()
                                app.imClient.acceptFriendRequest(request.fromUserId)
                            }
                        },
                        onReject = {
                            scope.launch {
                                val uid = currentUserId ?: return@launch
                                app.friendRequestDao.updateStatus(request.requestId, RequestStatus.REJECTED)
                                requests = app.friendRequestDao.getPendingRequests(uid)
                                AppEvents.notifyFriendRequestChanged()
                                app.imClient.rejectFriendRequest(request.fromUserId)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendRequestItem(
    request: FriendRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val app = LightChatApplication.instance
    val context = LocalContext.current
    val targetUser = remember(request.fromUserId) {
        app.userDao.getById(request.fromUserId)
    }
    val avatarBitmap by produceState<Bitmap?>(initialValue = null, targetUser?.avatarUrl, targetUser?.avatarVersion) {
        val user = targetUser
        value = if (user?.avatarUrl?.isNotBlank() == true) {
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
    val avatarColor = remember(targetUser?.avatar) {
        targetUser?.avatar?.takeIf { it.startsWith("#") }?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { WeChatGreen }
        } ?: WeChatGreen
    }
    val displayName = request.fromNickname.ifBlank { request.fromUserId }
    val displayInitial = displayName.take(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(displayInitial, color = avatarColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(request.fromNickname, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            if (request.message.isNotEmpty()) {
                Text(request.message, fontSize = 13.sp, color = TextSecondary)
            }
        }
        TextButton(onClick = onAccept) {
            Text("同意", color = WeChatGreen, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onReject) {
            Text("拒绝", color = UnreadRed)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = DividerColor, thickness = 0.5.dp)
}

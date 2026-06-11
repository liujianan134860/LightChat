package com.lightchat.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightchat.LightChatApplication
import com.lightchat.model.Conversation
import com.lightchat.model.ConversationType
import com.lightchat.model.Message
import com.lightchat.model.MessageStatus
import com.lightchat.model.MessageType
import com.lightchat.model.User
import com.lightchat.ui.components.LightChatAvatar
import com.lightchat.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserCardShareScreen(
    userId: String,
    onBack: () -> Unit,
    onShared: () -> Unit
) {
    val app = LightChatApplication.instance
    val context = LocalContext.current
    val user = remember(userId) { app.userDao.getById(userId) }
    val allConversations = remember(userId) {
        app.conversationRepository.getConversations().filter { conv ->
            conv.type != ConversationType.SINGLE || conv.targetId != userId
        }
    }
    val listState = rememberLazyListState()
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }

    val groupConvs = remember(allConversations, query) {
        allConversations.filter { it.type == ConversationType.GROUP && it.title.contains(query, ignoreCase = true) }
    }
    val singleConvs = remember(allConversations, query) {
        allConversations.filter { it.type == ConversationType.SINGLE && it.title.contains(query, ignoreCase = true) }
    }
    val totalFiltered = groupConvs.size + singleConvs.size

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("推荐给朋友") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(
                            enabled = selectedIds.isNotEmpty() && user != null,
                            onClick = {
                                val targetUser = user ?: return@TextButton
                                val count = selectedIds.count { sendUserCardToConversation(it, targetUser) }
                                Toast.makeText(context, if (count > 0) "已推荐给 $count 个会话" else "推荐失败", Toast.LENGTH_SHORT).show()
                                if (count > 0) onShared()
                            }
                        ) {
                            Text(
                                if (selectedIds.isEmpty()) "发送" else "发送(${selectedIds.size})",
                                color = if (selectedIds.isNotEmpty()) WeChatGreen else TextSecondary
                            )
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
                .background(WeChatWhite)
        ) {
            Text(
                text = user?.nickname?.let { "选择要分享「$it」名片的会话" } ?: "名片用户不存在",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                fontSize = 14.sp,
                color = TextSecondary
            )
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)

            // Search bar
            CardShareSearchBar(
                query = query,
                onQueryChange = { query = it }
            )

            if (totalFiltered == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) "暂无可推荐的会话" else "未找到匹配的会话",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (groupConvs.isNotEmpty()) {
                        item(key = "share_group_header") {
                            CardShareSectionHeader("群聊")
                        }
                        items(groupConvs, key = { "group_${it.conversationId}" }) { conversation ->
                            ShareConversationItem(
                                conversation = conversation,
                                selected = conversation.conversationId in selectedIds,
                                onClick = {
                                    selectedIds = if (conversation.conversationId in selectedIds) {
                                        selectedIds - conversation.conversationId
                                    } else {
                                        selectedIds + conversation.conversationId
                                    }
                                }
                            )
                        }
                    }
                    if (singleConvs.isNotEmpty()) {
                        item(key = "share_contact_header") {
                            CardShareSectionHeader("联系人")
                        }
                        items(singleConvs, key = { "single_${it.conversationId}" }) { conversation ->
                            ShareConversationItem(
                                conversation = conversation,
                                selected = conversation.conversationId in selectedIds,
                                onClick = {
                                    selectedIds = if (conversation.conversationId in selectedIds) {
                                        selectedIds - conversation.conversationId
                                    } else {
                                        selectedIds + conversation.conversationId
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardShareSearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
        placeholder = { Text("搜索群聊、联系人", fontSize = 14.sp) },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = BottomBarBackground,
            unfocusedContainerColor = BottomBarBackground
        ),
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "清除")
                }
            }
        }
    )
}

@Composable
private fun CardShareSectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SectionBackground)
            .padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ShareConversationItem(conversation: Conversation, selected: Boolean, onClick: () -> Unit) {
    val targetUser = remember(conversation.targetId) {
        if (conversation.type == ConversationType.SINGLE) LightChatApplication.instance.userDao.getById(conversation.targetId) else null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LightChatAvatar(avatar = targetUser?.avatar ?: conversation.avatar, avatarUrl = targetUser?.avatarUrl ?: "", avatarVersion = targetUser?.avatarVersion ?: 0, userId = targetUser?.userId ?: "", name = conversation.title, size = 44.dp, allowNetwork = true)
        Spacer(modifier = Modifier.width(12.dp))
        Text(conversation.title, fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "已选择", tint = WeChatGreen)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = DividerColor, thickness = 0.5.dp)
}

private fun sendUserCardToConversation(conversationId: String, user: User): Boolean {
    val app = LightChatApplication.instance
    if (!app.imClient.isAuthenticated()) return false
    val currentUserId = app.userSession.currentUserId ?: return false
    val targetConv = app.conversationRepository.getConversation(conversationId) ?: return false
    if (targetConv.type == ConversationType.SINGLE && targetConv.targetId == user.userId) return false
    val now = System.currentTimeMillis()
    val newMsgId = UUID.randomUUID().toString()
    val extra = org.json.JSONObject().apply {
        put("userId", user.userId)
        put("nickname", user.nickname)
        put("avatar", user.avatar)
        put("avatarUrl", user.avatarUrl)
        put("avatarVersion", user.avatarVersion)
        put("signature", user.signature)
        put("region", user.region)
    }.toString()
    val msg = Message(
        messageId = newMsgId,
        conversationId = conversationId,
        senderId = currentUserId,
        messageType = MessageType.USER_CARD,
        content = user.nickname.ifBlank { user.userId },
        status = MessageStatus.SENDING,
        sendTime = now,
        createTime = now,
        extra = extra
    )
    app.messageRepository.sendMessage(msg)
    app.conversationRepository.updateLastMessage(conversationId, newMsgId, "[名片]${msg.content}", now)
    val receiverId = resolveSingleReceiverId(targetConv, currentUserId)
    val groupId = if (targetConv.type == ConversationType.GROUP) targetConv.targetId else null
    val sent = app.imClient.sendMessage(
        conversationId,
        MessageType.USER_CARD.value,
        msg.content,
        0,
        newMsgId,
        now,
        receiverId,
        groupId,
        extra
    )
    if (!sent) {
        app.messageRepository.updateMessageStatus(newMsgId, MessageStatus.FAILED)
        return false
    }
    GlobalScope.launch(Dispatchers.IO) {
        delay(3_000)
        val current = app.messageDao.getById(newMsgId)
        if (current != null && current.status == MessageStatus.SENDING) {
            app.messageRepository.updateMessageStatus(newMsgId, MessageStatus.FAILED)
        }
    }
    return true
}

private fun resolveSingleReceiverId(conversation: Conversation, currentUserId: String): String? {
    if (conversation.type != ConversationType.SINGLE) return null
    if (conversation.targetId.isNotBlank() && conversation.targetId != currentUserId) return conversation.targetId
    val parts = Regex("^single_(.+)_(.+)$").find(conversation.conversationId)?.groupValues ?: return null
    return listOf(parts[1], parts[2]).firstOrNull { it != currentUserId }
}

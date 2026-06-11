package com.lightchat.ui.forward

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.lightchat.LightChatApplication
import com.lightchat.model.Message
import com.lightchat.model.MessageStatus
import com.lightchat.model.MessageType
import com.lightchat.ui.components.LightChatAvatar
import com.lightchat.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardPreviewScreen(
    onBack: () -> Unit,
    onSend: () -> Unit
) {
    val app = LightChatApplication.instance
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUserId = app.userSession.currentUserId
    val scope = rememberCoroutineScope()
    val forwardMessages = remember {
        app.currentForwardMessageIds.mapNotNull { app.messageDao.getById(it) }
    }
    val forwardSnapshots = remember(forwardMessages) {
        forwardMessages.map(::messageToForwardSnapshot)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("合并转发") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            val targetConvId = app.currentForwardTargetConversationId
                            if (targetConvId != null && forwardSnapshots.isNotEmpty()) {
                                scope.launch {
                                    val currentId = currentUserId ?: return@launch
                                    val sourceConvId = app.currentForwardSourceConversationId
                                    val sourceConv = sourceConvId?.let { app.conversationRepository.getConversation(it) }
                                    val sourceOwner = currentId?.let { app.userDao.getById(it) }
                                    val mergedContent = org.json.JSONObject().apply {
                                        put("type", "merge_forward")
                                        if (sourceConv != null) {
                                            put("sourceType", sourceConv.type.value)
                                            put("sourceTitle", sourceConv.title)
                                            if (sourceConv.type == com.lightchat.model.ConversationType.SINGLE && sourceOwner != null) {
                                                put("sourceOwnerName", sourceOwner.nickname.ifBlank { sourceOwner.userId })
                                            }
                                        }
                                        put("messages", org.json.JSONArray().apply {
                                            forwardSnapshots.forEach { snapshot ->
                                                put(org.json.JSONObject().apply {
                                                    put("senderId", snapshot.senderId)
                                                    put("sender", snapshot.sender)
                                                    put("messageType", snapshot.messageType.value)
                                                    put("content", snapshot.content)
                                                    put("displayContent", displayForwardContent(snapshot))
                                                    put("time", snapshot.time)
                                                    if (!snapshot.extra.isNullOrBlank()) put("extra", snapshot.extra)
                                                })
                                            }
                                        })
                                    }.toString()
                                    val targetConv = app.conversationRepository.getConversation(targetConvId)
                                    val newMsgId = UUID.randomUUID().toString()
                                    val msg = Message(
                                        messageId = newMsgId,
                                        conversationId = targetConvId,
                                        senderId = currentId,
                                        messageType = MessageType.MERGE_FORWARD,
                                        content = "[聊天记录]",
                                        status = MessageStatus.SENDING,
                                        sendTime = System.currentTimeMillis(),
                                        createTime = System.currentTimeMillis(),
                                        extra = mergedContent
                                    )
                                    app.messageRepository.sendMessage(msg)
                                    app.conversationRepository.updateLastMessage(
                                        targetConvId, newMsgId, "[聊天记录]", System.currentTimeMillis()
                                    )
                                    val receiverId = resolveSingleReceiverId(targetConv, currentId)
                                    val groupId = if (targetConv?.type == com.lightchat.model.ConversationType.GROUP) targetConv.targetId else null
                                    val sent = app.imClient.sendMessage(
                                        targetConvId, MessageType.MERGE_FORWARD.value, "[聊天记录]",
                                        0, newMsgId, System.currentTimeMillis(), receiverId, groupId, mergedContent
                                    )
                                    if (!sent) {
                                        app.messageRepository.updateMessageStatus(newMsgId, MessageStatus.FAILED)
                                    } else {
                                        GlobalScope.launch(Dispatchers.IO) {
                                            delay(2_000)
                                            val current = app.messageDao.getById(newMsgId)
                                            if (current != null && current.status == MessageStatus.SENDING) {
                                                app.messageRepository.updateMessageStatus(newMsgId, MessageStatus.FAILED)
                                            }
                                        }
                                    }
                                    app.currentForwardMessageIds = emptyList()
                                    app.currentForwardTargetConversationId = null
                                    Toast.makeText(context, if (sent) "已转发" else "转发失败", Toast.LENGTH_SHORT).show()
                                    onSend()
                                }
                            }
                        }) {
                            Text("发送", color = WeChatGreen, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarBackground)
                )
                HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(WeChatBg),
            contentPadding = PaddingValues(16.dp)
        ) {
            // Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = WeChatWhite)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "聊天记录",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        forwardMessages.forEach { msg ->
                            val sender = app.userDao.getById(msg.senderId)
                            val senderName = sender?.nickname ?: msg.senderId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LightChatAvatar(
                                    avatar = sender?.avatar.orEmpty(),
                                    avatarUrl = sender?.avatarUrl.orEmpty(),
                                    avatarVersion = sender?.avatarVersion ?: 0,
                                    userId = sender?.userId.orEmpty(),
                                    name = senderName,
                                    size = 28.dp,
                                    allowNetwork = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$senderName: ",
                                    fontSize = 13.sp,
                                    color = WeChatGreen
                                )
                                Text(
                                    text = msg.content,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "共 ${forwardMessages.size} 条消息",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

private fun resolveSingleReceiverId(conversation: com.lightchat.model.Conversation?, currentUserId: String): String? {
    if (conversation?.type != com.lightchat.model.ConversationType.SINGLE) return null
    if (conversation.targetId.isNotBlank() && conversation.targetId != currentUserId) return conversation.targetId
    val parts = Regex("^single_(.+)_(.+)$").find(conversation.conversationId)?.groupValues ?: return null
    return listOf(parts[1], parts[2]).firstOrNull { it != currentUserId }
}

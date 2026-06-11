package com.lightchat.ui.forward

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.lightchat.LightChatApplication
import com.lightchat.model.Conversation
import com.lightchat.model.ConversationType
import com.lightchat.model.Message
import com.lightchat.model.MessageStatus
import com.lightchat.model.MessageType

import com.lightchat.ui.chat.generateThumbnail
import com.lightchat.ui.chat.originalCacheFile
import com.lightchat.ui.chat.thumbnailCacheFile
import com.lightchat.ui.components.LightChatAvatar
import com.lightchat.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardSelectScreen(
    onBack: () -> Unit,
    onSend: (targetId: String) -> Unit = {}
) {
    val app = LightChatApplication.instance
    val context = androidx.compose.ui.platform.LocalContext.current
    val allConversations = remember { app.conversationRepository.getConversations() }
    val listState = rememberLazyListState()
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showForwardTypeDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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
                    title = { Text("选择联系人") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    if (app.currentForwardRequiresTypeChoice) {
                                        showForwardTypeDialog = true
                                    } else {
                                        val msgIds = app.currentForwardMessageIds
                                        val snapshots = currentForwardSnapshots()
                                        val targets = selectedIds.toList()
                                        if (msgIds.isNotEmpty() || snapshots.isNotEmpty()) {
                                            scope.launch {
                                                val sentTargets = sendIndividualForwardMessages(targets, msgIds, snapshots)
                                                app.currentForwardMessageIds = emptyList()
                                                app.currentForwardSnapshotPayloads = emptyList()
                                                app.currentForwardTargetConversationId = null
                                                app.currentForwardRequiresTypeChoice = false
                                                if (sentTargets.isNotEmpty()) {
                                                    Toast.makeText(context, "已转发", Toast.LENGTH_SHORT).show()
                                                    onSend(sentTargets.joinToString(","))
                                                } else {
                                                    Toast.makeText(context, "转发失败", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = selectedIds.isNotEmpty()
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
            // Search bar
            ForwardSearchBar(
                query = query,
                onQueryChange = { query = it }
            )

            if (totalFiltered == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (query.isBlank()) "暂无可用会话" else "未找到匹配的会话",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (groupConvs.isNotEmpty()) {
                        item(key = "forward_group_header") {
                            ForwardSectionHeader("群聊")
                        }
                        items(groupConvs, key = { "group_${it.conversationId}" }) { conv ->
                            ForwardSelectItem(
                                conversation = conv,
                                isSelected = selectedIds.contains(conv.conversationId),
                                onClick = {
                                    selectedIds = if (selectedIds.contains(conv.conversationId)) {
                                        selectedIds - conv.conversationId
                                    } else {
                                        selectedIds + conv.conversationId
                                    }
                                }
                            )
                        }
                    }
                    if (singleConvs.isNotEmpty()) {
                        item(key = "forward_contact_header") {
                            ForwardSectionHeader("联系人")
                        }
                        items(singleConvs, key = { "single_${it.conversationId}" }) { conv ->
                            ForwardSelectItem(
                                conversation = conv,
                                isSelected = selectedIds.contains(conv.conversationId),
                                onClick = {
                                    selectedIds = if (selectedIds.contains(conv.conversationId)) {
                                        selectedIds - conv.conversationId
                                    } else {
                                        selectedIds + conv.conversationId
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Forward type dialog
    if (showForwardTypeDialog) {
        AlertDialog(
            onDismissRequest = { showForwardTypeDialog = false },
            title = { Text("选择转发方式") },
            containerColor = TopBarBackground,
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showForwardTypeDialog = false
                            val msgIds = app.currentForwardMessageIds
                            val snapshots = currentForwardSnapshots()
                            val targets = selectedIds.toList()
                            if ((msgIds.isNotEmpty() || snapshots.isNotEmpty()) && targets.isNotEmpty()) {
                                scope.launch {
                                    val sentTargets = sendIndividualForwardMessages(targets, msgIds, snapshots)
                                    app.currentForwardMessageIds = emptyList()
                                    app.currentForwardSnapshotPayloads = emptyList()
                                    app.currentForwardTargetConversationId = null
                                    app.currentForwardRequiresTypeChoice = false
                                    if (sentTargets.isNotEmpty()) {
                                        Toast.makeText(context, "已转发", Toast.LENGTH_SHORT).show()
                                        onSend(sentTargets.joinToString(","))
                                    } else {
                                        Toast.makeText(context, "转发失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("逐条转发", modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = {
                            showForwardTypeDialog = false
                            val msgIds = app.currentForwardMessageIds
                            val snapshots = currentForwardSnapshots()
                            val targets = selectedIds.toList()
                            if ((msgIds.isNotEmpty() || snapshots.isNotEmpty()) && targets.isNotEmpty()) {
                                scope.launch {
                                    val sentTargets = sendMergedForwardMessages(targets, msgIds, snapshots)
                                    app.currentForwardMessageIds = emptyList()
                                    app.currentForwardSnapshotPayloads = emptyList()
                                    app.currentForwardTargetConversationId = null
                                    app.currentForwardRequiresTypeChoice = false
                                    if (sentTargets.isNotEmpty()) {
                                        Toast.makeText(context, "已转发", Toast.LENGTH_SHORT).show()
                                        onSend(sentTargets.joinToString(","))
                                    } else {
                                        Toast.makeText(context, "转发失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("合并转发", modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showForwardTypeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

data class ForwardSnapshot(
    val senderId: String,
    val sender: String,
    val messageType: MessageType,
    val content: String,
    val extra: String?,
    val time: Long
)

fun messageToForwardSnapshot(message: Message): ForwardSnapshot {
    val app = LightChatApplication.instance
    val sender = app.userDao.getById(message.senderId)
    val snapshot = ForwardSnapshot(
        senderId = message.senderId,
        sender = sender?.nickname ?: message.senderId,
        messageType = message.messageType,
        content = message.content,
        extra = message.extra,
        time = message.sendTime
    )
    return normalizeForwardImageSnapshot(message, snapshot)
}

fun forwardSnapshotToJson(snapshot: ForwardSnapshot): String {
    return JSONObject().apply {
        put("senderId", snapshot.senderId)
        put("sender", snapshot.sender)
        put("messageType", snapshot.messageType.value)
        put("content", snapshot.content)
        put("displayContent", displayForwardContent(snapshot))
        put("time", snapshot.time)
        if (!snapshot.extra.isNullOrBlank()) put("extra", snapshot.extra)
    }.toString()
}

fun parseForwardSnapshot(payload: String): ForwardSnapshot? {
    return try {
        val obj = JSONObject(payload)
        val type = MessageType.fromInt(obj.optInt("messageType", MessageType.TEXT.value))
        ForwardSnapshot(
            senderId = obj.optString("senderId", obj.optString("sender", "")),
            sender = obj.optString("sender"),
            messageType = type,
            content = obj.optString("content", obj.optString("displayContent", "")),
            extra = obj.optString("extra").takeIf { it.isNotBlank() },
            time = obj.optLong("time", 0L)
        )
    } catch (_: Exception) {
        null
    }
}

private fun currentForwardSnapshots(): List<ForwardSnapshot> {
    return LightChatApplication.instance.currentForwardSnapshotPayloads.mapNotNull(::parseForwardSnapshot)
}

private fun snapshotsFromMessageIds(messageIds: List<String>): List<ForwardSnapshot> {
    val app = LightChatApplication.instance
    return messageIds.mapNotNull { app.messageDao.getById(it) }.map(::messageToForwardSnapshot)
}

private fun sendIndividualForwardMessages(
    targetConversationIds: List<String>,
    messageIds: List<String>,
    explicitSnapshots: List<ForwardSnapshot> = emptyList()
): List<String> {
    val app = LightChatApplication.instance
    val currentUserId = app.userSession.currentUserId ?: return emptyList()
    if (!app.imClient.isAuthenticated()) return emptyList()
    val snapshots = explicitSnapshots.ifEmpty { snapshotsFromMessageIds(messageIds) }
    if (targetConversationIds.isEmpty() || snapshots.isEmpty()) return emptyList()

    val sentTargets = mutableListOf<String>()
    targetConversationIds.forEach { targetConvId ->
        val targetConv = app.conversationRepository.getConversation(targetConvId)
        var sentAny = false
        for (snapshot in snapshots) {
            val forwardedContent = when (snapshot.messageType) {
                MessageType.IMAGE -> snapshot.content
                else -> snapshot.content
            }
            val newMsgId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val msg = Message(
                messageId = newMsgId,
                conversationId = targetConvId,
                senderId = currentUserId,
                messageType = snapshot.messageType,
                content = forwardedContent,
                status = MessageStatus.SENDING,
                sendTime = now,
                createTime = now,
                extra = snapshot.extra
            )
            app.messageRepository.sendMessage(msg)
            app.conversationRepository.updateLastMessage(
                targetConvId,
                newMsgId,
                displayForwardContent(snapshot),
                now
            )
            val receiverId = resolveSingleReceiverId(targetConv, currentUserId)
            val groupId = if (targetConv?.type == com.lightchat.model.ConversationType.GROUP) targetConv.targetId else null
            val sent = app.imClient.sendMessage(
                targetConvId, snapshot.messageType.value, forwardedContent,
                app.nextClientSeq, newMsgId, now, receiverId, groupId, snapshot.extra
            )
            if (!sent) {
                app.messageRepository.updateMessageStatus(newMsgId, MessageStatus.FAILED)
            } else {
                GlobalScope.launch(Dispatchers.IO) {
                    delay(3_000)
                    val current = app.messageDao.getById(newMsgId)
                    if (current != null && current.status == MessageStatus.SENDING) {
                        app.messageRepository.updateMessageStatus(newMsgId, MessageStatus.FAILED)
                    }
                }
            }
            sentAny = sentAny || sent
        }
        if (sentAny) sentTargets += targetConvId
    }
    return sentTargets
}

private fun sendMergedForwardMessages(
    targetConversationIds: List<String>,
    messageIds: List<String>,
    explicitSnapshots: List<ForwardSnapshot> = emptyList()
): List<String> {
    val app = LightChatApplication.instance
    val currentUserId = app.userSession.currentUserId ?: return emptyList()
    if (!app.imClient.isAuthenticated()) return emptyList()
    val snapshots = explicitSnapshots.ifEmpty { snapshotsFromMessageIds(messageIds) }
    if (snapshots.isEmpty()) return emptyList()

    val sourceConvId = app.currentForwardSourceConversationId
    val sourceConv = sourceConvId?.let { app.conversationRepository.getConversation(it) }
    val sourceOwner = currentUserId?.let { app.userDao.getById(it) }

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
            snapshots.forEach { snapshot ->
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

    val sentTargets = mutableListOf<String>()
    targetConversationIds.forEach { targetConvId ->
        val targetConv = app.conversationRepository.getConversation(targetConvId) ?: return@forEach
        val newMsgId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val msg = Message(
            messageId = newMsgId,
            conversationId = targetConvId,
            senderId = currentUserId,
            messageType = MessageType.MERGE_FORWARD,
            content = "[聊天记录]",
            status = MessageStatus.SENDING,
            sendTime = now,
            createTime = now,
            extra = mergedContent
        )
        app.messageRepository.sendMessage(msg)
        app.conversationRepository.updateLastMessage(targetConvId, newMsgId, "[聊天记录]", now)
        val receiverId = resolveSingleReceiverId(targetConv, currentUserId)
        val groupId = if (targetConv.type == com.lightchat.model.ConversationType.GROUP) targetConv.targetId else null
        val sent = app.imClient.sendMessage(
            targetConvId,
            MessageType.MERGE_FORWARD.value,
            "[聊天记录]",
            app.nextClientSeq,
            newMsgId,
            now,
            receiverId,
            groupId,
            mergedContent
        )
        if (!sent) {
            app.messageRepository.updateMessageStatus(newMsgId, MessageStatus.FAILED)
        } else {
            sentTargets += targetConvId
            GlobalScope.launch(Dispatchers.IO) {
                delay(3_000)
                val current = app.messageDao.getById(newMsgId)
                if (current != null && current.status == MessageStatus.SENDING) {
                    app.messageRepository.updateMessageStatus(newMsgId, MessageStatus.FAILED)
                }
            }
        }
    }
    return sentTargets
}

private fun normalizeForwardImageSnapshot(message: Message, snapshot: ForwardSnapshot): ForwardSnapshot {
    if (snapshot.messageType != MessageType.IMAGE) return snapshot

    val app = LightChatApplication.instance
    val context = app.applicationContext
    val origCache = originalCacheFile(context, message.messageId)
    val contentFile = message.content.takeIf { it.isNotBlank() }?.let { File(it) }
    val isContentThumbnail = contentFile?.name?.startsWith("thumb_") == true

    // Original file: prefer originalCacheFile, or message.content only if it's NOT a thumbnail
    val originalFile = when {
        origCache.exists() && origCache.length() > 0L -> origCache
        contentFile != null && contentFile.exists() && contentFile.length() > 0L && !isContentThumbnail -> contentFile
        else -> null
    }

    // Thumbnail file: prefer thumbnailCacheFile, or generate from original (sent messages only)
    val thumbCache = thumbnailCacheFile(context, message.messageId)
    val thumbFile = when {
        thumbCache.exists() && thumbCache.length() > 0L -> thumbCache
        originalFile != null -> {
            val generated = generateThumbnail(originalFile.absolutePath)
            if (generated != null) File(generated).takeIf { it.exists() && it.length() > 0L } else null
        }
        else -> null
    }

    val extraObj = runCatching { JSONObject(snapshot.extra ?: "{}") }.getOrElse { JSONObject() }

    val stableContent = when {
        thumbFile != null -> thumbFile.absolutePath
        originalFile != null -> originalFile.absolutePath
        snapshot.content.isNotBlank() -> snapshot.content
        else -> "[图片]"
    }
    return snapshot.copy(
        content = stableContent,
        extra = extraObj.takeIf { it.length() > 0 }?.toString() ?: snapshot.extra
    )
}

private fun resolveSingleReceiverId(conversation: Conversation?, currentUserId: String): String? {
    if (conversation?.type != com.lightchat.model.ConversationType.SINGLE) return null
    if (conversation.targetId.isNotBlank() && conversation.targetId != currentUserId) return conversation.targetId
    val parts = Regex("^single_(.+)_(.+)$").find(conversation.conversationId)?.groupValues ?: return null
    return listOf(parts[1], parts[2]).firstOrNull { it != currentUserId }
}

fun displayForwardContent(snapshot: ForwardSnapshot): String {
    return when (snapshot.messageType) {
        MessageType.IMAGE -> "[图片]"
        MessageType.USER_CARD -> "[名片]${snapshot.content}"
        MessageType.GROUP_CARD -> "[群名片]${snapshot.content}"
        MessageType.MERGE_FORWARD -> "[聊天记录]"
        else -> snapshot.content
    }
}

@Composable
private fun ForwardSearchBar(query: String, onQueryChange: (String) -> Unit) {
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
private fun ForwardSectionHeader(title: String) {
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
fun ForwardSelectItem(conversation: Conversation, isSelected: Boolean, onClick: () -> Unit) {
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
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = WeChatGreen)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = DividerColor, thickness = 0.5.dp)
}

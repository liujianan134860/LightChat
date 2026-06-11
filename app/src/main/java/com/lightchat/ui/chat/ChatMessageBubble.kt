package com.lightchat.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightchat.LightChatApplication
import com.lightchat.model.Message
import com.lightchat.model.MessageStatus
import com.lightchat.model.MessageType
import com.lightchat.model.User
import com.lightchat.ui.components.AvatarCacheLoader
import com.lightchat.ui.forward.parseMergeForwardLines
import com.lightchat.ui.theme.*
import org.json.JSONObject
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: Message,
    showStatus: Boolean,
    groupReadText: String? = null,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    isGroupChat: Boolean = true,
    onAvatarClick: (String) -> Unit = {},
    onUserCardClick: (String) -> Unit = {},
    onRecallEdit: () -> Unit = {},
    onImageClick: () -> Unit = {},
    onRetry: () -> Unit = {},
    onLongPress: (Rect) -> Unit,
    onTap: () -> Unit
) {
    val app = LightChatApplication.instance
    val isMine = message.senderId == app.userSession.currentUserId
    val isRecalled = message.isRecalled
    val isFailed = message.status == MessageStatus.FAILED
    val isSending = message.status == MessageStatus.SENDING
    var showSendingSpinner by remember(message.messageId, message.status) { mutableStateOf(false) }
    var previousStatus by remember(message.messageId) { mutableStateOf(MessageStatus.SENT) }
    LaunchedEffect(message.messageId, message.status) {
        showSendingSpinner = false
        if (message.status == MessageStatus.SENDING) {
            if (previousStatus == MessageStatus.FAILED) {
                showSendingSpinner = true
            } else {
                delay(500)
                showSendingSpinner = true
            }
        }
        previousStatus = message.status
    }
    val senderUser = remember(message.senderId) { app.userDao.getById(message.senderId) }
    val senderInitial = senderUser?.nickname?.take(1) ?: message.senderId.take(1)
    var avatarBitmap by remember(senderUser?.userId) { mutableStateOf<Bitmap?>(null) }
    val avatarColor = remember(senderUser?.avatar) {
        senderUser?.avatar?.takeIf { it.startsWith("#") }?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { WeChatGreen }
        } ?: WeChatGreen
    }
    LaunchedEffect(senderUser?.avatarUrl, senderUser?.avatarVersion, senderUser?.avatar) {
        val user = senderUser
        if (user != null && user.avatarUrl.isNotBlank()) {
            val result = AvatarCacheLoader.loadAvatar(
                context = app.applicationContext,
                userId = user.userId,
                avatarUrl = user.avatarUrl,
                avatarVersion = user.avatarVersion,
                avatarFallback = "",
                allowNetwork = true
            )
            avatarBitmap = result.bitmap
        } else if (user != null && user.avatar.startsWith("lightchat://")) {
            val result = AvatarCacheLoader.loadAvatar(
                context = app.applicationContext,
                userId = user.userId,
                avatarUrl = "",
                avatarVersion = 0,
                avatarFallback = user.avatar,
                allowNetwork = false
            )
            avatarBitmap = result.bitmap
        } else {
            avatarBitmap = null
        }
    }

    if (isRecalled) {
        RecalledMessageHint(
            isMine = isMine,
            senderName = senderUser?.nickname?.takeIf { it.isNotBlank() && it != message.senderId } ?: message.senderId,
            isGroupChat = isGroupChat,
            messageType = message.messageType,
            canReedit = isMine && message.messageType == MessageType.TEXT,
            onRecallEdit = onRecallEdit
        )
        return
    }
    if (message.messageType == MessageType.SYSTEM) {
        SystemMessageHint(message.content)
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val avatarSize = 36.dp
        val avatarGap = 6.dp
        val sideReserve = avatarSize + avatarGap
        val bubbleMaxWidth = (maxWidth - sideReserve * 2).coerceAtLeast(120.dp)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = if (isMultiSelectMode || !isMine) Arrangement.Start else Arrangement.End
            ) {
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onTap() },
                        modifier = Modifier.align(Alignment.CenterVertically),
                        colors = CheckboxDefaults.colors(checkedColor = WeChatGreen)
                    )
                }
                if (isMultiSelectMode && isMine) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (!isMine && message.messageType != MessageType.SYSTEM) {
                    MessageAvatar(
                        avatarBitmap = avatarBitmap,
                        avatarColor = avatarColor,
                        initial = senderInitial,
                        onClick = { onAvatarClick(message.senderId) },
                        size = avatarSize
                    )
                    Spacer(modifier = Modifier.width(avatarGap))
                }

                // Status indicators for non-IMAGE messages (IMAGE renders its own inside ImageMessageContent)
                val isImageType = message.messageType == MessageType.IMAGE
                if (isMine && isSending && showSendingSpinner && !isMultiSelectMode && !isImageType) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                if (isMine && isFailed && !isMultiSelectMode && !isImageType) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "重新发送",
                        tint = UnreadRed,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .size(22.dp)
                            .clickable(onClick = onRetry)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Column(
                    modifier = Modifier.widthIn(max = bubbleMaxWidth),
                    horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                ) {
                    if (!isMine && isGroupChat && message.messageType != MessageType.SYSTEM) {
                        Text(
                            text = senderUser?.nickname ?: message.senderId,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }
                    MessageBubbleContent(
                        message = message,
                        isMine = isMine,
                        isSelected = isSelected,
                        bubbleMaxWidth = bubbleMaxWidth,
                        showSendingSpinner = showSendingSpinner,
                        onTap = onTap,
                        onLongPress = onLongPress,
                        onImageClick = onImageClick,
                        onUserCardClick = onUserCardClick,
                        onRetry = onRetry
                    )
                }

                if (isMine && message.messageType != MessageType.SYSTEM) {
                    Spacer(modifier = Modifier.width(avatarGap))
                    MessageAvatar(
                        avatarBitmap = avatarBitmap,
                        avatarColor = avatarColor,
                        initial = senderInitial,
                        onClick = { onAvatarClick(message.senderId) },
                        size = avatarSize
                    )
                }

            }

            val statusText = groupReadText ?: if (message.status == MessageStatus.READ) "已读" else null
            if (isMine && showStatus && !isMultiSelectMode && statusText != null) {
                MessageStatusLine(
                    statusText = statusText,
                    sideReserve = sideReserve
                )
            }
        }
    }
}

@Composable
private fun MessageAvatar(
    avatarBitmap: android.graphics.Bitmap?,
    avatarColor: Color,
    initial: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor.copy(alpha = 0.2f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap.asImageBitmap(),
                contentDescription = "头像",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initial,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = avatarColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleContent(
    message: Message,
    isMine: Boolean,
    isSelected: Boolean,
    bubbleMaxWidth: androidx.compose.ui.unit.Dp,
    showSendingSpinner: Boolean,
    onTap: () -> Unit,
    onLongPress: (Rect) -> Unit,
    onImageClick: () -> Unit,
    onUserCardClick: (String) -> Unit,
    onRetry: () -> Unit
) {
    if (message.messageType == MessageType.IMAGE) {
        ImageMessageContent(message = message, isMine = isMine, showSendingSpinner = showSendingSpinner, onClick = onImageClick, onLongPress = onLongPress, onRetry = onRetry)
        return
    }
    var bubbleBounds by remember(message.messageId) { mutableStateOf(Rect.Zero) }

    Box(
        modifier = Modifier
            .widthIn(max = bubbleMaxWidth)
            .onGloballyPositioned { bubbleBounds = it.boundsInWindow() }
            .clip(
                RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 16.dp
                )
            )
            .background(
                when {
                    message.messageType == MessageType.USER_CARD -> Color.Transparent
                    message.messageType == MessageType.MERGE_FORWARD -> Color.Transparent
                    isSelected -> WeChatGreen.copy(alpha = 0.3f)
                    isMine -> BubbleGreen
                    else -> BubbleWhite
                }
            )
            .combinedClickable(
                onClick = onTap,
                onLongClick = { onLongPress(bubbleBounds) }
            )
            .padding(
                horizontal = if (message.messageType == MessageType.USER_CARD || message.messageType == MessageType.MERGE_FORWARD) 0.dp else 12.dp,
                vertical = if (message.messageType == MessageType.USER_CARD || message.messageType == MessageType.MERGE_FORWARD) 0.dp else 8.dp
            )
    ) {
        when (message.messageType) {
            MessageType.USER_CARD -> {
                UserCardPreview(
                    message = message,
                    onClick = {
                        val info = parseUserCardInfo(message)
                        saveUserCardSnapshot(info)
                        val userId = info.userId
                        if (userId.isNotBlank()) onUserCardClick(userId)
                    },
                    onLongPress = { onLongPress(bubbleBounds) }
                )
            }
            MessageType.GROUP_CARD -> {
                Text(
                    text = "[群名片] ${message.content}",
                    fontSize = 14.sp,
                    color = GroupAccentBlue,
                    fontWeight = FontWeight.Medium
                )
            }
            MessageType.MERGE_FORWARD -> MergeForwardContent(message)
            else -> {
                Text(
                    text = mentionHighlightedText(message),
                    fontSize = 15.sp,
                    color = TextPrimary
                )
            }
        }
    }
}

private fun mentionHighlightedText(message: Message) = buildAnnotatedString {
    val hasMentions = try {
        val ids = org.json.JSONObject(message.extra ?: "{}").optJSONArray("atUserIds")
        ids != null && ids.length() > 0
    } catch (_: Exception) {
        false
    }
    if (!hasMentions) {
        append(message.content)
        return@buildAnnotatedString
    }
    val mentionRegex = Regex("@[^\\s@]+")
    var cursor = 0
    mentionRegex.findAll(message.content).forEach { match ->
        append(message.content.substring(cursor, match.range.first))
        withStyle(SpanStyle(color = GroupAccentBlue, fontWeight = FontWeight.Medium)) {
            append(match.value)
        }
        cursor = match.range.last + 1
    }
    append(message.content.substring(cursor))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageMessageContent(
    message: Message,
    isMine: Boolean,
    showSendingSpinner: Boolean,
    onClick: () -> Unit,
    onLongPress: (Rect) -> Unit,
    onRetry: () -> Unit = {}
) {
    val context = LocalContext.current
    val progressivePath = rememberProgressiveImagePath(context, message)
    val imageLoadFailed = progressivePath == IMAGE_LOAD_FAILED_PATH
    val imageFile = remember(progressivePath) { java.io.File(progressivePath) }
    val hasRemoteImageSource = remember(message.extra) { hasRemoteImage(message) }
    var imageBounds by remember(message.messageId) { mutableStateOf(Rect.Zero) }
    val isSending = message.status == MessageStatus.SENDING
    val isFailed = message.status == MessageStatus.FAILED

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isMine && isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (isMine && isFailed) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "重新发送",
                    tint = UnreadRed,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(onClick = onRetry)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            BoxWithConstraints(modifier = Modifier.widthIn(max = 240.dp)) {
            val imageMaxWidth = maxWidth * 0.6f

            if (!imageLoadFailed && progressivePath.isNotBlank() && imageFile.exists()) {
                val bitmap = remember(progressivePath) {
                    decodeSampledBitmap(progressivePath, 480)
                }
                if (bitmap != null) {
                    val bubbleImageSize = remember(bitmap.width, bitmap.height, imageMaxWidth) {
                        fitImageSize(
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height,
                            maxWidth = imageMaxWidth,
                            maxHeight = 240.dp
                        )
                    }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "图片",
                        modifier = Modifier
                            .size(bubbleImageSize.first, bubbleImageSize.second)
                            .clip(RoundedCornerShape(8.dp))
                            .onGloballyPositioned {
                                imageBounds = it.boundsInWindow()
                                LightChatApplication.instance.imageBubbleBounds[message.messageId] = imageBounds
                            }
                            .combinedClickable(
                                onClick = onClick,
                                onLongClick = { onLongPress(imageBounds) }
                            ),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else {
                    InlineMessagePlaceholder(icon = Icons.Default.Image, text = "[图片]")
                }
            } else if (hasRemoteImageSource && !imageLoadFailed) {
                Box(
                    modifier = Modifier
                        .size(width = 150.dp, height = 150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BubbleGray)
                        .onGloballyPositioned {
                            imageBounds = it.boundsInWindow()
                            LightChatApplication.instance.imageBubbleBounds[message.messageId] = imageBounds
                        }
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = { onLongPress(imageBounds) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = WeChatGreen
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BubbleGray)
                        .onGloballyPositioned {
                            imageBounds = it.boundsInWindow()
                            LightChatApplication.instance.imageBubbleBounds[message.messageId] = imageBounds
                        }
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = { onLongPress(imageBounds) }
                        )
                        .padding(12.dp)
                ) {
                    InlineMessagePlaceholder(icon = Icons.Default.Error, text = "[图片已过期]")
                }
            }
            }
        }
    }
}

private fun fitImageSize(
    imageWidth: Int,
    imageHeight: Int,
    maxWidth: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp
): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> {
    if (imageWidth <= 0 || imageHeight <= 0) return maxWidth to maxWidth
    val aspect = imageWidth.toFloat() / imageHeight.toFloat()
    var width = maxWidth
    var height = width / aspect
    if (height > maxHeight) {
        height = maxHeight
        width = height * aspect
    }
    return width to height
}

@Composable
private fun InlineMessagePlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextSecondary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 14.sp, color = TextSecondary)
    }
}

@Composable
private fun MergeForwardContent(message: Message) {
    Column(
        modifier = Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(WeChatWhite)
            .padding(top = 10.dp, start = 10.dp, end = 10.dp, bottom = 8.dp)
    ) {
        Text(
            text = mergeForwardTitle(message),
            fontSize = 15.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(8.dp))
        mergePreviewLines(message.extra, 3).forEach { line ->
            Text(
                text = line,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(6.dp))
        Text("聊天记录", fontSize = 12.sp, color = TextSecondary)
    }
}

private fun mergeForwardTitle(message: Message): String {
    val extra = message.extra
    if (extra.isNullOrBlank()) return "聊天记录"
    return try {
        val obj = JSONObject(extra)
        val sourceType = obj.optInt("sourceType", -1)
        val sourceTitle = obj.optString("sourceTitle", "")
        if (sourceType == com.lightchat.model.ConversationType.SINGLE.value) {
            val ownerName = obj.optString("sourceOwnerName", "")
            if (sourceTitle.isNotBlank() && ownerName.isNotBlank()) {
                "${ownerName}和${sourceTitle}的聊天记录"
            } else if (sourceTitle.isNotBlank()) {
                "${sourceTitle}的聊天记录"
            } else {
                "聊天记录"
            }
        } else if (sourceType == com.lightchat.model.ConversationType.GROUP.value) {
            if (sourceTitle.isNotBlank()) "${sourceTitle}的群聊记录" else "群聊记录"
        } else {
            val names = parseMergeForwardLines(extra)
                .map { it.sender }
                .filter { it.isNotBlank() }
                .distinct()
                .take(2)
            if (names.isNotEmpty()) "${names.joinToString("与")}的聊天记录" else "聊天记录"
        }
    } catch (_: Exception) {
        "聊天记录"
    }
}

@Composable
private fun MessageStatusLine(
    statusText: String?,
    sideReserve: androidx.compose.ui.unit.Dp
) {
    Row(
        modifier = Modifier.padding(end = sideReserve + 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = statusText.orEmpty(),
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun RecalledMessageHint(
    isMine: Boolean,
    senderName: String,
    isGroupChat: Boolean,
    messageType: MessageType,
    canReedit: Boolean,
    onRecallEdit: () -> Unit
) {
    val typeText = when (messageType) {
        MessageType.IMAGE -> "一张图片"
        else -> "一条消息"
    }
    val text = when {
        isMine -> "你撤回了$typeText"
        isGroupChat -> "${senderName}撤回了$typeText"
        else -> "对方撤回了$typeText"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = TextSecondary
        )
        if (canReedit) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "重新编辑",
                fontSize = 12.sp,
                color = GroupAccentBlue,
                modifier = Modifier.clickable(onClick = onRecallEdit)
            )
        }
    }
}

@Composable
private fun SystemMessageHint(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}

private fun mergePreviewLines(extra: String?, limit: Int): List<String> {
    return parseMergeForwardLines(extra)
        .take(limit)
        .map { "${it.sender}: ${it.displayContent}" }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserCardPreview(message: Message, onClick: () -> Unit, onLongPress: () -> Unit) {
    val info = remember(message.extra, message.content) { parseUserCardInfo(message) }
    val context = LocalContext.current
    var avatarBitmap by remember(info.userId) {
        mutableStateOf(AvatarCacheLoader.getCachedBitmap(context, info.userId, info.avatarVersion))
    }
    val avatarColor = remember(info.avatar) {
        info.avatar.takeIf { it.startsWith("#") }?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { WeChatGreen }
        } ?: WeChatGreen
    }
    LaunchedEffect(info.avatarUrl, info.avatarVersion, info.avatar) {
        if (info.avatarUrl.isNotBlank()) {
            val result = AvatarCacheLoader.loadAvatar(
                context = context,
                userId = info.userId,
                avatarUrl = info.avatarUrl,
                avatarVersion = info.avatarVersion,
                avatarFallback = "",
                allowNetwork = true
            )
            avatarBitmap = result.bitmap
        } else if (info.avatar.startsWith("lightchat://")) {
            val result = AvatarCacheLoader.loadAvatar(
                context = context,
                userId = info.userId,
                avatarUrl = "",
                avatarVersion = 0,
                avatarFallback = info.avatar,
                allowNetwork = false
            )
            avatarBitmap = result.bitmap
        } else {
            avatarBitmap = null
        }
    }
    Column(
        modifier = Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(WeChatWhite)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(top = 10.dp, start = 10.dp, end = 10.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(avatarColor.copy(alpha = 0.16f)),
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
                    Text(
                        text = info.nickname.take(1).ifBlank { "名" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = avatarColor
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = info.nickname,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(6.dp))
        Text("个人名片", fontSize = 12.sp, color = TextSecondary)
    }
}

private data class UserCardInfo(
    val userId: String,
    val nickname: String,
    val avatar: String,
    val avatarUrl: String = "",
    val avatarVersion: Int = 0
)

private fun parseUserCardInfo(message: Message): UserCardInfo {
    return try {
        val obj = org.json.JSONObject(message.extra ?: "{}")
        UserCardInfo(
            userId = obj.optString("userId", ""),
            nickname = obj.optString("nickname", message.content).ifBlank { message.content },
            avatar = obj.optString("avatar", ""),
            avatarUrl = obj.optString("avatarUrl", ""),
            avatarVersion = obj.optInt("avatarVersion", 0)
        )
    } catch (_: Exception) {
        UserCardInfo("", message.content, "")
    }
}

private fun saveUserCardSnapshot(info: UserCardInfo) {
    if (info.userId.isBlank()) return
    LightChatApplication.instance.userDao.upsertPreservingExisting(
        User(
            userId = info.userId,
            nickname = info.nickname.ifBlank { info.userId },
            avatar = info.avatar,
            avatarUrl = info.avatarUrl,
            avatarVersion = info.avatarVersion
        )
    )
}

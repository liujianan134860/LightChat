package com.lightchat.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.lightchat.ui.theme.WeChatGreen

@Composable
fun LightChatAvatar(
    avatar: String = "",
    avatarUrl: String = "",
    avatarVersion: Int = 0,
    userId: String = "",
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    allowNetwork: Boolean = false
) {
    val context = LocalContext.current
    var loadedBitmap by remember(avatar, avatarUrl, avatarVersion, userId) { mutableStateOf<Bitmap?>(null) }

    val showColor = remember(avatar) { parseAvatarColor(avatar) }

    LaunchedEffect(avatarUrl, avatarVersion, avatar) {
        if (avatarUrl.isNotBlank()) {
            val cacheUserId = userId.ifBlank { avatarUrl.hashCode().toString() }
            val result = AvatarCacheLoader.loadAvatar(
                context = context,
                userId = cacheUserId,
                avatarUrl = avatarUrl,
                avatarVersion = avatarVersion,
                avatarFallback = "",
                allowNetwork = allowNetwork
            )
            loadedBitmap = result.bitmap
        } else if (avatar.startsWith("lightchat://")) {
            val cacheUserId = userId.ifBlank { "assistant" }
            val result = AvatarCacheLoader.loadAvatar(
                context = context,
                userId = cacheUserId,
                avatarUrl = "",
                avatarVersion = 0,
                avatarFallback = avatar,
                allowNetwork = false
            )
            loadedBitmap = result.bitmap
        } else {
            loadedBitmap = null
        }
    }

    val displayBitmap = loadedBitmap
    val bgColor = (showColor ?: WeChatGreen).copy(alpha = 0.18f)
    val textColor = showColor ?: WeChatGreen

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (displayBitmap != null) {
            Image(
                bitmap = displayBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = name.take(1).ifBlank { "聊" },
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp
            )
        }
    }
}

private fun parseAvatarColor(avatar: String): Color? {
    if (!avatar.startsWith("#")) return null
    return try {
        Color(android.graphics.Color.parseColor(avatar))
    } catch (_: Exception) {
        null
    }
}

package com.lightchat.ui.forward

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.lightchat.LightChatApplication
import com.lightchat.model.Message
import com.lightchat.model.MessageType
import com.lightchat.model.User
import com.lightchat.ui.chat.PictureCacheManager
import com.lightchat.ui.chat.decodeSampledBitmap
import com.lightchat.ui.chat.originalCacheFile
import com.lightchat.ui.chat.rememberProgressiveImagePath
import com.lightchat.ui.chat.saveImageToGallery
import com.lightchat.ui.chat.thumbnailCacheFile
import com.lightchat.ui.chat.zoomPanGesture
import com.lightchat.ui.components.LightChatAvatar
import com.lightchat.ui.theme.*
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.sign

data class MergeForwardLine(
    val senderId: String,
    val sender: String,
    val messageType: MessageType,
    val content: String,
    val displayContent: String,
    val extra: String?,
    val time: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeForwardDetailScreen(
    messageId: String,
    onBack: () -> Unit,
    onForwardSnapshot: (String) -> Unit = {},
    onUserCardClick: (String) -> Unit = {}
) {
    val app = LightChatApplication.instance
    val message = remember(messageId) { app.messageDao.getById(messageId) }
    var extraStack by remember(message?.extra) { mutableStateOf(listOf(message?.extra.orEmpty())) }
    val currentExtra = extraStack.lastOrNull().orEmpty()
    val lines = remember(currentExtra) { parseMergeForwardLines(currentExtra) }
    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }
    var viewedFullscreenKeys by remember { mutableStateOf(setOf<String>()) }
    val allSameDay = remember(lines) { isSameDay(lines) }
    val imageLines = remember(lines) { lines.filter { it.messageType == MessageType.IMAGE } }

    BackHandler(enabled = extraStack.size > 1) {
        extraStack = extraStack.dropLast(1)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("聊天记录") },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (extraStack.size > 1) {
                                extraStack = extraStack.dropLast(1)
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarBackground)
                )
                HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = extraStack.size,
            transitionSpec = {
                val goingForward = targetState > initialState
                if (goingForward) {
                    (slideInHorizontally(
                        animationSpec = tween(250),
                        initialOffsetX = { it }
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(250),
                        targetOffsetX = { -it / 3 }
                    ))
                } else {
                    (slideInHorizontally(
                        animationSpec = tween(250),
                        initialOffsetX = { -it / 3 }
                    ) togetherWith fadeOut(tween(250)))
                }
            },
            label = "merge_forward_detail"
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(SectionBackground),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = WeChatWhite)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(mergeForwardTitle(currentExtra), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            val dateRange = formatDateRange(lines)
                            if (dateRange.isNotBlank()) {
                                Text(dateRange, fontSize = 12.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text("共 ${lines.size} 条消息", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                items(lines) { line ->
                    MergeForwardLineCard(
                        line = line,
                        allSameDay = allSameDay,
                        viewedFullscreenKeys = viewedFullscreenKeys,
                        onOpenImage = {
                            val index = imageLines.indexOf(line).coerceAtLeast(0)
                            viewerStartIndex = index
                        },
                        onOpenNested = {
                            if (!line.extra.isNullOrBlank()) {
                                extraStack = extraStack + line.extra
                            }
                        },
                        onUserCardClick = onUserCardClick,
                        parentMessageId = messageId
                    )
                }
            }
        }
    }

    viewerStartIndex?.let { startIndex ->
        val initialLine = imageLines.getOrNull(startIndex)
        val initialKey = initialLine?.stableSyntheticMessageId()
        val initialSourceBounds = remember(initialKey) { initialKey?.let { app.imageBubbleBounds[it] } }
        MergeForwardImageViewer(
            lines = imageLines,
            initialIndex = startIndex,
            initialSourceBounds = initialSourceBounds,
            onBack = { viewerStartIndex = null },
            onForwardSnapshot = {
                viewerStartIndex = null
                onForwardSnapshot(forwardSnapshotToJson(it))
            },
            onImageViewed = { key -> viewedFullscreenKeys = viewedFullscreenKeys + key },
            parentMessageId = messageId
        )
    }
}

fun parseMergeForwardLines(extra: String?): List<MergeForwardLine> {
    if (extra.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONObject(extra).optJSONArray("messages") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val messageType = MessageType.fromInt(obj.optInt("messageType", MessageType.TEXT.value))
                val content = obj.optString("content", obj.optString("displayContent", ""))
                add(
                    MergeForwardLine(
                        senderId = obj.optString("senderId", obj.optString("sender", "")),
                        sender = obj.optString("sender"),
                        messageType = messageType,
                        content = content,
                        displayContent = obj.optString("displayContent", displayLineContent(messageType, content)),
                        extra = obj.optString("extra").takeIf { it.isNotBlank() },
                        time = obj.optLong("time", 0)
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun isSameDay(lines: List<MergeForwardLine>): Boolean {
    if (lines.size <= 1) return true
    val cal = java.util.Calendar.getInstance()
    val first = lines.first().time
    cal.timeInMillis = first
    val firstDay = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    return lines.all {
        if (it.time == 0L) return@all true
        cal.timeInMillis = it.time
        (cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)) == firstDay
    }
}

private fun formatDateRange(lines: List<MergeForwardLine>): String {
    val valid = lines.filter { it.time > 0 }
    if (valid.isEmpty()) return ""
    val fmt = java.text.SimpleDateFormat("yyyy年M月d日", java.util.Locale.CHINA)
    if (isSameDay(lines)) {
        return fmt.format(java.util.Date(valid.first().time))
    }
    val minTime = valid.minOf { it.time }
    val maxTime = valid.maxOf { it.time }
    return "${fmt.format(java.util.Date(minTime))} - ${fmt.format(java.util.Date(maxTime))}"
}

private fun formatMessageTime(time: Long, allSameDay: Boolean): String {
    if (time == 0L) return ""
    return if (allSameDay) {
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date(time))
    } else {
        java.text.SimpleDateFormat("M月d日 HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date(time))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MergeForwardLineCard(
    line: MergeForwardLine,
    allSameDay: Boolean,
    viewedFullscreenKeys: Set<String>,
    onOpenImage: () -> Unit,
    onOpenNested: () -> Unit,
    onUserCardClick: (String) -> Unit,
    parentMessageId: String = ""
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = WeChatWhite)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(line.sender, fontWeight = FontWeight.Medium, color = WeChatGreen, fontSize = 14.sp, modifier = Modifier.weight(1f))
                val timeStr = formatMessageTime(line.time, allSameDay)
                if (timeStr.isNotBlank()) {
                    Text(timeStr, fontSize = 11.sp, color = TextSecondary)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            when (line.messageType) {
                MessageType.IMAGE -> MergeForwardImageThumb(line, viewedFullscreenKeys, onOpenImage, parentMessageId = parentMessageId)
                MessageType.USER_CARD -> MergeUserCard(line, onUserCardClick)
                MessageType.GROUP_CARD -> MergeGroupCard(line)
                MessageType.MERGE_FORWARD -> MergeNestedBubble(line, onOpenNested)
                else -> Text(line.content, fontSize = 15.sp, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun MergeForwardImageThumb(line: MergeForwardLine, viewedFullscreenKeys: Set<String>, onClick: () -> Unit, parentMessageId: String = "") {
    val context = LocalContext.current
    val syntheticKey = remember(line) { line.stableSyntheticMessageId() }
    val source = remember(line.extra) { parseMergeImageSource(line.extra) }
    val originalFile = remember(source.objectKey, source.imageUrl) {
        PictureCacheManager.originalFile(context, source.objectKey, source.imageUrl)
    }
    val forceOriginal = syntheticKey in viewedFullscreenKeys || (originalFile != null && originalFile.exists())
    val path = rememberMergeImagePath(context, line, downloadOriginal = false, forceOriginal = forceOriginal, parentMessageId = parentMessageId)
    val file = remember(path) { File(path) }
    val bitmap = remember(path, line.extra) { decodeMergeBitmap(path, line.extra) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "图片",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .onGloballyPositioned { coordinates ->
                    LightChatApplication.instance.imageBubbleBounds[syntheticKey] = coordinates.boundsInWindow()
                }
                .clickable(onClick = onClick)
        )
    } else if (file.exists()) {
        Text("[图片]", color = TextSecondary, modifier = Modifier.clickable(onClick = onClick))
    } else {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(BubbleGray)
                .clickable(onClick = onClick)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Image, contentDescription = null, tint = TextSecondary)
            Spacer(modifier = Modifier.width(6.dp))
            Text("[图片]", color = TextSecondary)
        }
    }
}

@Composable
private fun MergeUserCard(line: MergeForwardLine, onUserCardClick: (String) -> Unit) {
    val obj = remember(line.extra) { runCatching { JSONObject(line.extra ?: "{}") }.getOrNull() }
    val userId = obj?.optString("userId").orEmpty()
    val nickname = obj?.optString("nickname", line.content).orEmpty().ifBlank { line.content }
    val avatar = obj?.optString("avatar").orEmpty()
    val avatarUrl = obj?.optString("avatarUrl").orEmpty()
    val avatarVersion = obj?.optInt("avatarVersion", 0) ?: 0
    Box(
        modifier = Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BottomBarBackground)
            .clickable {
                saveUserCardSnapshot(userId, nickname, avatar, avatarUrl, avatarVersion)
                if (userId.isNotBlank()) onUserCardClick(userId)
            }
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LightChatAvatar(avatar = avatar, avatarUrl = avatarUrl, avatarVersion = avatarVersion, userId = userId, name = nickname, size = 42.dp, allowNetwork = true)
                Spacer(modifier = Modifier.width(12.dp))
                Text(nickname, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(6.dp))
            Text("个人名片", fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun MergeGroupCard(line: MergeForwardLine) {
    Text("[群名片] ${line.content}", color = GroupAccentBlue, fontWeight = FontWeight.Medium)
}

@Composable
private fun MergeNestedBubble(line: MergeForwardLine, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(BottomBarBackground)
            .clickable(onClick = onClick)
            .padding(top = 10.dp, start = 10.dp, end = 10.dp, bottom = 8.dp)
    ) {
        Text(
            mergeForwardTitle(line.extra),
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(8.dp))
        parseMergeForwardLines(line.extra).take(3).forEach {
            Text("${it.sender}: ${it.displayContent}", color = TextSecondary, fontSize = 12.sp, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(6.dp))
        Text("聊天记录", fontSize = 12.sp, color = TextSecondary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MergeForwardImageViewer(
    lines: List<MergeForwardLine>,
    initialIndex: Int,
    initialSourceBounds: Rect?,
    onBack: () -> Unit,
    onForwardSnapshot: (ForwardSnapshot) -> Unit,
    onImageViewed: (String) -> Unit = {},
    parentMessageId: String = ""
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val app = LightChatApplication.instance
    val scope = rememberCoroutineScope()
    val transitionProgress = remember { Animatable(0f) }
    var viewerSize by remember { mutableStateOf(IntSize.Zero) }
    var viewerWindowOffset by remember { mutableStateOf(Offset.Zero) }
    var isClosing by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(true) }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        transitionProgress.animateTo(1f, tween(FullscreenAnimationMs))
    }

    val view = LocalView.current
    val savedStatusColor = remember { (view.context as? android.app.Activity)?.window?.statusBarColor }
    val savedNavColor = remember { (view.context as? android.app.Activity)?.window?.navigationBarColor }
    val savedLightStatus = remember {
        val window = (view.context as? android.app.Activity)?.window
        window?.let { WindowCompat.getInsetsController(it, view) }?.isAppearanceLightStatusBars
    }
    val savedLightNav = remember {
        val window = (view.context as? android.app.Activity)?.window
        window?.let { WindowCompat.getInsetsController(it, view) }?.isAppearanceLightNavigationBars
    }
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        window?.statusBarColor = android.graphics.Color.BLACK
        window?.navigationBarColor = android.graphics.Color.BLACK
        window?.let { w -> WindowCompat.getInsetsController(w, view) }?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        onDispose { }
    }

    fun restoreBars() {
        val window = (view.context as? android.app.Activity)?.window
        savedStatusColor?.let { window?.statusBarColor = it }
        savedNavColor?.let { window?.navigationBarColor = it }
        window?.let { w -> WindowCompat.getInsetsController(w, view) }?.apply {
            savedLightStatus?.let { isAppearanceLightStatusBars = it }
            savedLightNav?.let { isAppearanceLightNavigationBars = it }
        }
    }

    val pagerState = rememberPagerState(
        pageCount = { lines.size.coerceAtLeast(1) },
        initialPage = initialIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
    )

    LaunchedEffect(pagerState.currentPage) {
        val line = lines.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        onImageViewed(line.stableSyntheticMessageId())
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    fun closeViewer() {
        if (isClosing) return
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        isClosing = true
        restoreBars()
        scope.launch {
            transitionProgress.animateTo(0f, tween(FullscreenAnimationMs))
            onBack()
        }
    }

    BackHandler { closeViewer() }

    var rawOverscroll by remember { mutableStateOf(0f) }
    val overscrollAnim = remember { Animatable(0f) }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && rawOverscroll != 0f) {
            overscrollAnim.snapTo(rawOverscroll)
            rawOverscroll = 0f
            overscrollAnim.animateTo(0f, spring(dampingRatio = 0.7f))
        }
    }
    LaunchedEffect(rawOverscroll) {
        if (rawOverscroll != 0f) {
            overscrollAnim.snapTo(rawOverscroll)
        }
    }
    val displayOverscroll = overscrollAnim.value

    val nestedScrollConnection = remember(pagerState, lines.size) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                val atFirst = pagerState.currentPage == 0
                val atLast = pagerState.currentPage == lines.size - 1
                if ((atFirst && available.x > 0) || (atLast && available.x < 0)) {
                    rawOverscroll += available.x * 0.35f
                    return available
                }
                return Offset.Zero
            }
        }
    }

    val progress = transitionProgress.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                viewerSize = it.size
                viewerWindowOffset = it.positionInWindow()
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = progress))
        )
        if (lines.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有图片", color = WeChatWhite)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = scale <= 1f,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = displayOverscroll }
                    .nestedScroll(nestedScrollConnection)
            ) { page ->
                val line = lines[page]
                val path = rememberMergeImagePath(context, line, downloadOriginal = true, parentMessageId = parentMessageId)
                val bitmap = remember(path, line.extra) { decodeMergeBitmap(path, line.extra) }

                var containerSize by remember { mutableStateOf(IntSize.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { containerSize = it.size },
                    contentAlignment = Alignment.Center
                ) {
                    val containerW = containerSize.width.toFloat()
                    val containerH = containerSize.height.toFloat()

                    if (bitmap != null && containerW > 0f && containerH > 0f) {
                        val bitmapW = bitmap.width.toFloat()
                        val bitmapH = bitmap.height.toFloat()
                        val fitScale = minOf(containerW / bitmapW, containerH / bitmapH)
                        val displayW = bitmapW * fitScale
                        val displayH = bitmapH * fitScale
                        val displayLeft = (containerW - displayW) / 2f
                        val displayTop = (containerH - displayH) / 2f
                        val displayCenterX = displayLeft + displayW / 2f
                        val displayCenterY = displayTop + displayH / 2f

                        val syntheticKey = line.stableSyntheticMessageId()
                        val sourceBounds = app.imageBubbleBounds[syntheticKey]
                            ?: if (page == initialIndex) initialSourceBounds else null
                        val boundsOnScreen = sourceBounds != null &&
                            sourceBounds.top < viewerSize.height && sourceBounds.bottom > 0 &&
                            sourceBounds.left < viewerSize.width && sourceBounds.right > 0
                        val useSharedElementTransform =
                            boundsOnScreen &&
                                ((isClosing && page == pagerState.currentPage) ||
                                    (!isClosing && page == initialIndex))
                        val isFallbackClose = isClosing && page == pagerState.currentPage && !boundsOnScreen
                        val imageProgress = if (useSharedElementTransform || isFallbackClose) progress else 1f
                        val effectiveSourceBounds = if (useSharedElementTransform) sourceBounds else null
                        val targetScaleX = effectiveSourceBounds?.let { it.width / displayW } ?: if (isFallbackClose) 0.01f else 1f
                        val targetScaleY = effectiveSourceBounds?.let { it.height / displayH } ?: if (isFallbackClose) 0.01f else 1f
                        val targetTranslationX = effectiveSourceBounds?.let { it.center.x - viewerWindowOffset.x - displayCenterX } ?: 0f
                        val targetTranslationY = effectiveSourceBounds?.let { it.center.y - viewerWindowOffset.y - displayCenterY } ?: 0f

                        val effectiveW = displayW * scale
                        val effectiveH = displayH * scale
                        val maxOffsetX = maxOf(0f, (effectiveW - containerW) / 2f)
                        val maxOffsetY = maxOf(0f, (effectiveH - containerH) / 2f)

                        fun rubberBand(value: Float, maxAbs: Float): Float {
                            if (maxAbs <= 0f) return 0f
                            return if (abs(value) > maxAbs) {
                                val over = abs(value) - maxAbs
                                sign(value) * (maxAbs + over * 0.35f)
                            } else value
                        }

                        val clampedX = rubberBand(offsetX, maxOffsetX)
                        val clampedY = rubberBand(offsetY, maxOffsetY)

                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "图片",
                            modifier = Modifier
                                .size(
                                    width = with(density) { displayW.toDp() },
                                    height = with(density) { displayH.toDp() }
                                )
                                .graphicsLayer {
                                    val sharedScaleX = lerp(targetScaleX, 1f, imageProgress)
                                    val sharedScaleY = lerp(targetScaleY, 1f, imageProgress)
                                    scaleX = sharedScaleX * scale
                                    scaleY = sharedScaleY * scale
                                    translationX = lerp(targetTranslationX, 0f, imageProgress) + clampedX
                                    translationY = lerp(targetTranslationY, 0f, imageProgress) + clampedY
                                    alpha = 1f
                                }
                                .zoomPanGesture(currentScale = { scale }) { pan, zoom ->
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    if (newScale == 1f) {
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                    scale = newScale
                                }
                                .pointerInput(page) {
                                    detectTapGestures(onTap = { closeViewer() })
                                },
                            contentScale = ContentScale.Fit
                        )
                    } else if (containerW <= 0f) {
                        // not measured yet
                    } else {
                        Text("[图片已过期]", color = WeChatWhite)
                    }
                }
            }
        }

        if (showButtons && !isClosing && lines.size > 1) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${pagerState.currentPage + 1}/${lines.size}",
                    color = WeChatWhite.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }

        AnimatedVisibility(
            visible = showButtons && !isClosing && lines.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Row(
                modifier = Modifier.padding(end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = {
                    val line = lines.getOrNull(pagerState.currentPage) ?: return@IconButton
                    val source = parseMergeImageSource(line.extra)
                    val original = PictureCacheManager.originalFile(context, source.objectKey, source.imageUrl)
                    val thumb = PictureCacheManager.thumbnailFile(context, source.thumbnailObjectKey, source.thumbnailUrl)
                    val contentFile = File(line.content).takeIf { it.exists() }
                    val existing = when {
                        original != null && original.exists() -> original
                        thumb != null && thumb.exists() -> thumb
                        contentFile != null -> contentFile
                        else -> null
                    }
                    if (existing != null) {
                        scope.launch {
                            Toast.makeText(
                                context,
                                if (saveImageToGallery(context, existing)) "已保存到系统相册" else "保存失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(context, "图片文件不存在", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Default.SaveAlt, contentDescription = "保存", tint = WeChatWhite)
                }
                IconButton(onClick = {
                    val line = lines.getOrNull(pagerState.currentPage) ?: return@IconButton
                    onForwardSnapshot(line.toForwardSnapshot())
                }) {
                    Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = "转发", tint = WeChatWhite)
                }
            }
        }
    }
}

private const val FullscreenAnimationMs = 300

private fun MergeForwardLine.toSyntheticMessage(messageId: String): Message {
    return Message(
        messageId = messageId,
        conversationId = "merge_forward_detail",
        senderId = senderId,
        messageType = messageType,
        content = content,
        extra = extra
    )
}

private fun MergeForwardLine.stableSyntheticMessageId(): String {
    val key = listOf(messageType.value.toString(), content, extra.orEmpty(), time.toString()).joinToString("|")
    return "merge_${key.hashCode().toString().replace("-", "n")}"
}

private fun MergeForwardLine.toForwardSnapshot(): ForwardSnapshot {
    return ForwardSnapshot(
        senderId = senderId,
        sender = sender,
        messageType = messageType,
        content = content,
        extra = extra,
        time = time
    )
}

private fun displayLineContent(type: MessageType, content: String): String {
    return when (type) {
        MessageType.IMAGE -> "[图片]"
        MessageType.USER_CARD -> "[名片]$content"
        MessageType.GROUP_CARD -> "[群名片]$content"
        MessageType.MERGE_FORWARD -> "[聊天记录]"
        else -> content
    }
}

@Composable
private fun rememberMergeImagePath(
    context: android.content.Context,
    line: MergeForwardLine,
    downloadOriginal: Boolean,
    forceOriginal: Boolean = false,
    parentMessageId: String = ""
): String {
    val synthetic = remember(line) { line.toSyntheticMessage(line.stableSyntheticMessageId()) }
    val progressive = rememberProgressiveImagePath(context, synthetic, downloadOriginal)
    val source = remember(line.extra) { parseMergeImageSource(line.extra) }
    val fullImage = remember(source.objectKey, source.imageUrl) {
        PictureCacheManager.originalFile(context, source.objectKey, source.imageUrl)
            ?: originalCacheFile(context, synthetic.messageId)
    }
    val thumbImage = remember(source.thumbnailObjectKey, source.thumbnailUrl) {
        PictureCacheManager.thumbnailFile(context, source.thumbnailObjectKey, source.thumbnailUrl)
            ?: thumbnailCacheFile(context, synthetic.messageId)
    }
    var cacheTick by remember(line.extra, downloadOriginal) { mutableIntStateOf(0) }

    LaunchedEffect(line.extra, downloadOriginal) {
        withContext(Dispatchers.IO) {
            val target = if (downloadOriginal) fullImage else thumbImage
            val url = if (downloadOriginal) source.imageUrl else source.thumbnailUrl
            val objectKey = if (downloadOriginal) source.objectKey else source.thumbnailObjectKey
            val urlKey = if (downloadOriginal) "imageUrl" else "thumbnailUrl"
            if (target != null && !target.exists() && url.isNotBlank()) {
                PictureCacheManager.downloadToFile(target, url, objectKey) { refreshedUrl ->
                    if (parentMessageId.isNotBlank()) {
                        persistMergeRefreshedUrl(parentMessageId, line, urlKey, refreshedUrl)
                    }
                }
            }
        }
        cacheTick++
    }

    return remember(progressive, cacheTick, line.content, downloadOriginal, forceOriginal) {
        when {
            downloadOriginal && fullImage != null && fullImage.exists() -> fullImage.absolutePath
            !downloadOriginal && thumbImage != null && thumbImage.exists() && !forceOriginal -> thumbImage.absolutePath
            fullImage != null && fullImage.exists() -> fullImage.absolutePath
            progressive.isNotBlank() -> progressive
            File(line.content).exists() -> line.content
            else -> ""
        }
    }
}

private data class MergeImageSource(
    val thumbnailUrl: String = "",
    val imageUrl: String = "",
    val thumbnailObjectKey: String = "",
    val objectKey: String = ""
)

private fun parseMergeImageSource(extra: String?): MergeImageSource {
    return try {
        val obj = JSONObject(extra ?: "{}")
        MergeImageSource(
            thumbnailUrl = obj.optString("thumbnailUrl", ""),
            imageUrl = obj.optString("imageUrl", ""),
            thumbnailObjectKey = obj.optString("thumbnailObjectKey", ""),
            objectKey = obj.optString("objectKey", "")
        )
    } catch (_: Exception) {
        MergeImageSource()
    }
}

private fun persistMergeRefreshedUrl(parentMessageId: String, line: MergeForwardLine, key: String, newUrl: String) {
    try {
        val app = LightChatApplication.instance
        val parent = app.messageDao.getById(parentMessageId) ?: return
        val root = JSONObject(parent.extra ?: "{}")
        val messages = root.optJSONArray("messages") ?: return
        for (i in 0 until messages.length()) {
            val obj = messages.getJSONObject(i)
            if (obj.optString("senderId") == line.senderId &&
                obj.optLong("time") == line.time &&
                obj.optString("content") == line.content) {
                val extraObj = JSONObject(obj.optString("extra", "{}"))
                extraObj.put(key, newUrl)
                obj.put("extra", extraObj.toString())
                app.messageDao.updateExtra(parentMessageId, root.toString())
                return
            }
        }
    } catch (_: Exception) {}
}

private fun mergeForwardTitle(extra: String?): String {
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
            parseMergeForwardLines(extra)
                .map { it.sender }
                .filter { it.isNotBlank() }
                .distinct()
                .let { names ->
                    when {
                        names.isEmpty() -> "聊天记录"
                        names.size > 2 -> "群聊记录"
                        else -> "${names.joinToString("和")}的聊天记录"
                    }
                }
        }
    } catch (_: Exception) {
        "聊天记录"
    }
}

private fun decodeMergeBitmap(path: String, extra: String?): android.graphics.Bitmap? {
    if (path.isNotBlank() && File(path).exists()) {
        decodeSampledBitmap(path, 2048)?.let { return it }
    }
    return null
}

private fun saveUserCardSnapshot(userId: String, nickname: String, avatar: String, avatarUrl: String = "", avatarVersion: Int = 0) {
    if (userId.isBlank()) return
    LightChatApplication.instance.userDao.upsertPreservingExisting(
        User(
            userId = userId,
            nickname = nickname.ifBlank { userId },
            avatar = avatar,
            avatarUrl = avatarUrl,
            avatarVersion = avatarVersion
        )
    )
}

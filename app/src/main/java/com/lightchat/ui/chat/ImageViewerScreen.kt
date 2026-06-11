package com.lightchat.ui.chat

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightchat.LightChatApplication
import com.lightchat.model.Message
import com.lightchat.model.MessageType
import com.lightchat.ui.theme.*
import androidx.core.view.WindowCompat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sign
import androidx.compose.ui.util.lerp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(
    conversationId: String,
    initialMessageId: String,
    onBack: () -> Unit,
    onForward: (String) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val app = LightChatApplication.instance
    val scope = rememberCoroutineScope()
    val viewerSessionId = remember { System.nanoTime() }
    val initialSourceBounds = remember(initialMessageId) { app.imageBubbleBounds[initialMessageId] }
    val transitionProgress = remember { Animatable(0f) }
    var viewerSize by remember { mutableStateOf(IntSize.Zero) }
    var viewerWindowOffset by remember { mutableStateOf(Offset.Zero) }
    var isClosing by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(true) }

    // Zoom/pan state (per-page, reset on page change)
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

    val imageMessages = remember(conversationId) {
        app.messageRepository.getMessages(conversationId)
            .filter { it.messageType == MessageType.IMAGE && !it.isRecalled }
    }
    val initialIndex = remember(imageMessages, initialMessageId) {
        imageMessages.indexOfFirst { it.messageId == initialMessageId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        pageCount = { imageMessages.size.coerceAtLeast(1) },
        initialPage = initialIndex
    )

    // Reset zoom when page changes
    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    // Overscroll with spring-back
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

    val nestedScrollConnection = remember(pagerState, imageMessages.size) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                val atFirst = pagerState.currentPage == 0
                val atLast = pagerState.currentPage == imageMessages.size - 1
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
        if (imageMessages.isEmpty()) {
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
                val message = imageMessages[page]
                val shouldDownloadOriginal = page == pagerState.currentPage
                val progressivePath = rememberProgressiveImagePath(
                    context = context,
                    message = message,
                    downloadOriginal = shouldDownloadOriginal,
                    downloadAttempt = viewerSessionId
                )
                val imageFile = remember(progressivePath) { File(progressivePath) }

                // Container size in pixels for zoom/pan calculations
                var containerSize by remember { mutableStateOf(IntSize.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { containerSize = it.size },
                    contentAlignment = Alignment.Center
                ) {
                    val containerW = containerSize.width.toFloat()
                    val containerH = containerSize.height.toFloat()

                    // Load bitmap
                    val bitmap = if (progressivePath.isNotBlank() && imageFile.exists()) {
                        remember(imageFile.absolutePath) {
                            decodeSampledBitmap(imageFile.absolutePath, 2048)
                        }
                    } else null

                    if (bitmap != null && containerW > 0f && containerH > 0f) {
                        // Calculate image display bounds with ContentScale.Fit
                        val bitmapW = bitmap.width.toFloat()
                        val bitmapH = bitmap.height.toFloat()
                        val fitScale = minOf(containerW / bitmapW, containerH / bitmapH)
                        val displayW = bitmapW * fitScale
                        val displayH = bitmapH * fitScale
                        val displayLeft = (containerW - displayW) / 2f
                        val displayTop = (containerH - displayH) / 2f
                        val displayCenterX = displayLeft + displayW / 2f
                        val displayCenterY = displayTop + displayH / 2f
                        val sourceBounds = app.imageBubbleBounds[message.messageId]
                            ?: if (message.messageId == initialMessageId) initialSourceBounds else null
                        val boundsOnScreen = sourceBounds != null &&
                            sourceBounds.top < viewerSize.height && sourceBounds.bottom > 0 &&
                            sourceBounds.left < viewerSize.width && sourceBounds.right > 0
                        val useSharedElementTransform =
                            boundsOnScreen &&
                                ((isClosing && page == pagerState.currentPage) ||
                                    (!isClosing && message.messageId == initialMessageId))
                        val isFallbackClose = isClosing && page == pagerState.currentPage && !boundsOnScreen
                        val imageProgress = if (useSharedElementTransform || isFallbackClose) progress else 1f
                        val effectiveSourceBounds = if (useSharedElementTransform) sourceBounds else null
                        val targetScaleX = effectiveSourceBounds?.let { it.width / displayW } ?: if (isFallbackClose) 0.01f else 1f
                        val targetScaleY = effectiveSourceBounds?.let { it.height / displayH } ?: if (isFallbackClose) 0.01f else 1f
                        val targetTranslationX = effectiveSourceBounds?.let {
                            it.center.x - viewerWindowOffset.x - displayCenterX
                        } ?: 0f
                        val targetTranslationY = effectiveSourceBounds?.let {
                            it.center.y - viewerWindowOffset.y - displayCenterY
                        } ?: 0f

                        // Effective size after zoom
                        val effectiveW = displayW * scale
                        val effectiveH = displayH * scale

                        // Max offsets before image edge hits container edge
                        val maxOffsetX = maxOf(0f, (effectiveW - containerW) / 2f)
                        val maxOffsetY = maxOf(0f, (effectiveH - containerH) / 2f)

                        // Rubber-band clamp
                        fun rubberBand(value: Float, maxAbs: Float): Float {
                            if (maxAbs <= 0f) return 0f
                            return if (abs(value) > maxAbs) {
                                val over = abs(value) - maxAbs
                                sign(value) * (maxAbs + over * 0.35f)
                            } else value
                        }

                        val clampedX = rubberBand(offsetX, maxOffsetX)
                        val clampedY = rubberBand(offsetY, maxOffsetY)

                        androidx.compose.foundation.Image(
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
                                    detectTapGestures(
                                        onTap = { closeViewer() }
                                    )
                                },
                            contentScale = ContentScale.Fit
                        )
                    } else if (containerW <= 0f) {
                        // Container not measured yet, show placeholder
                    } else {
                        Text("[图片已过期]", color = WeChatWhite)
                    }
                }
            }
        }

        // Page indicator — bottom center
        if (showButtons && !isClosing && imageMessages.size > 1) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${pagerState.currentPage + 1}/${imageMessages.size}",
                    color = WeChatWhite.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }

        // Two small buttons — bottom right
        AnimatedVisibility(
            visible = showButtons && !isClosing && imageMessages.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Row(
                modifier = Modifier
                    .padding(end = 16.dp, bottom = 16.dp)
                    .clip(CircleShape)
                    .background(Color(0x99444444))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Save button
                Icon(
                    Icons.Default.SaveAlt,
                    contentDescription = "保存",
                    tint = WeChatWhite,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable {
                            val msg = imageMessages.getOrNull(pagerState.currentPage) ?: return@clickable
                            scope.launch {
                                val file = resolveOriginalImageForSave(context, msg)
                                val message = if (file != null && file.exists()) {
                                    val success = withContext(Dispatchers.IO) {
                                        saveImageToGallery(context, file)
                                    }
                                    if (success) "已保存到系统相册" else "保存失败"
                                } else {
                                    "图片文件不存在"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                )

                // Forward button
                Icon(
                    Icons.AutoMirrored.Filled.Forward,
                    contentDescription = "转发",
                    tint = WeChatWhite,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable {
                            val msg = imageMessages.getOrNull(pagerState.currentPage) ?: return@clickable
                            onForward(msg.messageId)
                        }
                )
            }
        }
    }
}

private const val FullscreenAnimationMs = 300

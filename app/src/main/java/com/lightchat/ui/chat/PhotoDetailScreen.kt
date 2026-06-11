package com.lightchat.ui.chat

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightchat.LightChatApplication
import com.lightchat.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotoDetailScreen(
    conversationId: String,
    initialIndex: Int,
    onBack: () -> Unit,
    onEditClick: (Int) -> Unit,
    onSendClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = LightChatApplication.instance

    val photos = remember { app.pickerAllPhotoUris }
    var selectedIndices by remember { mutableStateOf(app.pickerSelectedIndices) }

    if (photos.isEmpty()) { onBack(); return }

    val pagerState = rememberPagerState(pageCount = { photos.size }, initialPage = initialIndex.coerceIn(0, photos.size - 1))
    var showUi by remember { mutableStateOf(true) }

    // Zoom/pan state — reset on page change
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

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

    val nestedScrollConnection = remember(pagerState, photos.size) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                val atFirst = pagerState.currentPage == 0
                val atLast = pagerState.currentPage == photos.size - 1
                if ((atFirst && available.x > 0) || (atLast && available.x < 0)) {
                    rawOverscroll += available.x * 0.35f
                    return available
                }
                return Offset.Zero
            }
        }
    }

    val currentPage = pagerState.currentPage
    val currentPhoto = photos.getOrNull(currentPage) ?: return
    val isSelected = selectedIndices.contains(currentPage)
    val selectionOrder = selectedIndices.indexOf(currentPage)
    val selectionNumber = if (selectionOrder >= 0) selectionOrder + 1 else 0

    fun purgeUnselectedEdits() {
        val paths = app.pickerEditedPaths.toMutableMap()
        if (paths.keys.retainAll(selectedIndices.toSet())) {
            app.pickerEditedPaths = paths
        }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val originalStatusColor = window?.statusBarColor
        val originalNavColor = window?.navigationBarColor
        val insetsController = window?.let { w -> WindowCompat.getInsetsController(w, view) }
        val originalLightStatus = insetsController?.isAppearanceLightStatusBars
        val originalLightNav = insetsController?.isAppearanceLightNavigationBars

        window?.statusBarColor = android.graphics.Color.BLACK
        window?.navigationBarColor = android.graphics.Color.BLACK
        insetsController?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        onDispose {
            originalStatusColor?.let { window?.statusBarColor = it }
            originalNavColor?.let { window?.navigationBarColor = it }
            insetsController?.apply {
                originalLightStatus?.let { isAppearanceLightStatusBars = it }
                originalLightNav?.let { isAppearanceLightNavigationBars = it }
            }
        }
    }

    fun goBack() {
        purgeUnselectedEdits()
        onBack()
    }

    BackHandler { goBack() }

    fun toggleSelection(index: Int) {
        val current = selectedIndices.toMutableList()
        if (current.contains(index)) {
            current.remove(index)
        } else {
            current.add(index)
        }
        selectedIndices = current
        app.pickerSelectedIndices = current
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = displayOverscroll }
                .nestedScroll(nestedScrollConnection)
        ) { page ->
            val photo = photos[page]
            val editedPath = app.pickerEditedPaths[page]
            val displayUri = if (editedPath != null) Uri.parse("file://$editedPath") else photo
            val bitmap = remember(displayUri) { loadBitmapFromUri(context, displayUri) }

            var containerSize by remember { mutableStateOf(IntSize.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { containerSize = it.size },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    val containerW = containerSize.width.toFloat()
                    val containerH = containerSize.height.toFloat()

                    if (containerW > 0f && containerH > 0f) {
                        val bitmapW = bitmap.width.toFloat()
                        val bitmapH = bitmap.height.toFloat()
                        val fitScale = minOf(containerW / bitmapW, containerH / bitmapH)
                        val displayW = bitmapW * fitScale
                        val displayH = bitmapH * fitScale

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
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = clampedX
                                    translationY = clampedY
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
                                        onDoubleTap = { tapOffset ->
                                            if (scale > 1f) {
                                                scale = 1f
                                                offsetX = 0f
                                                offsetY = 0f
                                            } else {
                                                scale = 2.5f
                                                val cx = containerW / 2f
                                                val cy = containerH / 2f
                                                offsetX = -(tapOffset.x - cx) * (2.5f - 1f)
                                                offsetY = -(tapOffset.y - cy) * (2.5f - 1f)
                                            }
                                        },
                                        onTap = { showUi = !showUi }
                                    )
                                },
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }

        // Top bar
        AnimatedVisibility(
            visible = showUi,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Text(
                        "${currentPage + 1}/${photos.size}",
                        color = WeChatWhite,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = WeChatWhite)
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (isSelected) WeChatGreen else Color.White.copy(alpha = 0.6f), CircleShape)
                            .background(if (isSelected) WeChatGreen else Color.Transparent)
                            .clickable { toggleSelection(currentPage) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text(
                                "$selectionNumber",
                                color = WeChatWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0x99000000))
            )
        }

        // Bottom bar
        AnimatedVisibility(
            visible = showUi,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xCC000000))) {
                if (selectedIndices.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(selectedIndices.size) { idx ->
                            val photoIdx = selectedIndices[idx]
                            val isCurrentPreview = photoIdx == currentPage
                            val thumbPhoto = photos.getOrNull(photoIdx)
                            val thumbEdited = app.pickerEditedPaths[photoIdx]
                            val thumbUri = if (thumbEdited != null) Uri.parse("file://$thumbEdited") else thumbPhoto

                            if (thumbUri != null) {
                                val thumbBitmap = remember(thumbUri) { decodeThumb(context, thumbUri, 80) }
                                val borderMod = if (isCurrentPreview) {
                                    Modifier.border(2.dp, WeChatWhite, RoundedCornerShape(4.dp))
                                } else Modifier
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(BubbleGray)
                                        .then(borderMod)
                                        .clickable {
                                            scope.launch { pagerState.animateScrollToPage(photoIdx) }
                                        }
                                ) {
                                    if (thumbBitmap != null) {
                                        Image(
                                            bitmap = thumbBitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(2.dp)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(WeChatGreen),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${idx + 1}",
                                            color = WeChatWhite,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onEditClick(currentPage) }) {
                        Text("编辑", color = WeChatWhite, fontSize = 15.sp)
                    }

                    Button(
                        onClick = {
                            val uris = selectedIndices.map { idx ->
                                app.pickerEditedPaths[idx]?.let { Uri.parse("file://$it") }
                                    ?: photos.getOrNull(idx) ?: Uri.EMPTY
                            }
                            app.pendingImageUris = uris
                            onSendClick()
                        },
                        enabled = selectedIndices.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WeChatGreen,
                            disabledContainerColor = Color.Gray.copy(alpha = 0.5f)
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (selectedIndices.isNotEmpty()) "发送(${selectedIndices.size})" else "发送",
                            color = WeChatWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

private fun loadBitmapFromUri(context: android.content.Context, uri: Uri): android.graphics.Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    } catch (_: Exception) { null }
}

private fun decodeThumb(context: android.content.Context, uri: Uri, maxDim: Int): android.graphics.Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            val opts = BitmapFactory.Options()
            opts.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            opts.inJustDecodeBounds = false
            opts.inSampleSize = calculateBitmapInSampleSize(opts.outWidth, opts.outHeight, maxDim)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
    } catch (_: Exception) { null }
}

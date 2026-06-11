package com.lightchat.ui.chat

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lightchat.LightChatApplication
import com.lightchat.ui.theme.*
import com.lightchat.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ImageEditScreen(
    conversationId: String,
    onBack: () -> Unit,
    onSent: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val app = LightChatApplication.instance
    val imageUris = remember { app.pendingImageUris.toList() }
    val pagerState = rememberPagerState(pageCount = { imageUris.size.coerceAtLeast(1) })

    // Doodle data per image index
    val doodlesPerImage = remember { mutableStateMapOf<Int, MutableList<DoodlePath>>() }
    var isDrawingMode by remember { mutableStateOf(false) }
    var currentColor by remember { mutableStateOf(Color.Red) }
    var currentStrokeWidth by remember { mutableStateOf(4f) }
    var showColorPicker by remember { mutableStateOf(false) }

    val doodleColors = listOf(
        Color.Red, Color(0xFFFF9900), Color.Yellow, Color.Green,
        Color.Blue, Color.Magenta, Color.White, Color.Black
    )

    LaunchedEffect(Unit) {
        viewModel.loadConversation(conversationId)
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

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    "${pagerState.currentPage + 1}/${imageUris.size}",
                    color = WeChatWhite
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    app.pendingImageUris = emptyList()
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = WeChatWhite)
                }
            },
            actions = {
                TextButton(onClick = {
                    viewModel.sendMultipleImages(imageUris, doodlesPerImage, conversationId)
                    app.pendingImageUris = emptyList()
                    onSent()
                }) {
                    Text("发送", color = WeChatGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xCC000000))
        )

        // Image pager
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (imageUris.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有图片", color = WeChatWhite)
                }
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val uri = imageUris[page]
                    val bitmap = remember(uri) {
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val bytes = inputStream?.readBytes()
                            inputStream?.close()
                            bytes?.let { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                        } catch (_: Exception) { null }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "图片预览",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit
                            )
                            // Doodle overlay
                            val currentPage = page
                            val pageDoodles = remember(currentPage) {
                                doodlesPerImage.getOrPut(currentPage) { mutableListOf() }
                            }
                            ImageDoodleCanvas(
                                modifier = Modifier.matchParentSize(),
                                currentColor = currentColor,
                                currentStrokeWidth = currentStrokeWidth,
                                isDrawingEnabled = isDrawingMode,
                                paths = pageDoodles,
                                onPathAdded = { pageDoodles.add(it) }
                            )
                        } else {
                            Text("无法加载图片", color = WeChatWhite)
                        }
                    }
                }
            }
        }

        // Bottom toolbar
        Surface(
            color = Color(0xCC000000),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (showColorPicker) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        doodleColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (color == currentColor) 3.dp else 1.dp,
                                        color = if (color == currentColor) WeChatWhite else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { currentColor = color }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Toggle draw mode
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { isDrawingMode = !isDrawingMode }) {
                            Icon(
                                Icons.Default.Brush,
                                contentDescription = "涂鸦",
                                tint = if (isDrawingMode) WeChatGreen else WeChatWhite
                            )
                        }
                        Text("涂鸦", fontSize = 10.sp, color = WeChatWhite)
                    }
                    // Color picker toggle
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { showColorPicker = !showColorPicker }) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(currentColor)
                            )
                        }
                        Text("颜色", fontSize = 10.sp, color = WeChatWhite)
                    }
                    // Undo
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = {
                            val currentDoodles = doodlesPerImage[pagerState.currentPage]
                            if (currentDoodles != null && currentDoodles.isNotEmpty()) {
                                currentDoodles.removeAt(currentDoodles.lastIndex)
                            }
                        }) {
                            Icon(Icons.Default.Undo, contentDescription = "撤销", tint = WeChatWhite)
                        }
                        Text("撤销", fontSize = 10.sp, color = WeChatWhite)
                    }
                    // Stroke width
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Slider(
                            value = currentStrokeWidth,
                            onValueChange = { currentStrokeWidth = it },
                            valueRange = 2f..12f,
                            modifier = Modifier.width(80.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = WeChatGreen,
                                activeTrackColor = WeChatGreen
                            ),
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(WeChatGreen, CircleShape)
                                )
                            }
                        )
                        Text("粗细", fontSize = 10.sp, color = WeChatWhite)
                    }
                }
            }
        }
    }
}

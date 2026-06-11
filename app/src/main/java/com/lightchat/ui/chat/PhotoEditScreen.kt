package com.lightchat.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.lightchat.LightChatApplication
import com.lightchat.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

data class DrawStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

private enum class EditMode { NONE, DRAW, CROP }

private enum class DrawTool { BRUSH, ERASER }

private enum class CropHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT, CENTER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditScreen(
    @Suppress("UNUSED_PARAMETER") conversationId: String,
    photoIndex: Int,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val app = LightChatApplication.instance
    val photoUri = app.pickerAllPhotoUris.getOrNull(photoIndex) ?: run { onBack(); return }

    val initialDisplayUri = remember {
        val editedPath = app.pickerEditedPaths[photoIndex]
        if (editedPath != null) Uri.parse("file://$editedPath") else photoUri
    }
    var stagedEditedPath by remember { mutableStateOf(app.pickerEditedPaths[photoIndex]) }
    var currentBitmapUri by remember { mutableStateOf(initialDisplayUri) }
    val workingBitmap = remember(currentBitmapUri) { loadBitmapFromUri(context, currentBitmapUri) }

    var editMode by remember { mutableStateOf(EditMode.NONE) }
    val isDrawing = editMode == EditMode.DRAW
    val isCropping = editMode == EditMode.CROP

    var imageDisplaySize by remember { mutableStateOf(IntSize.Zero) }

    // Crop state
    var cropRect by remember { mutableStateOf<Rect?>(null) }

    // Draw state
    val completedStrokes = remember { mutableStateListOf<DrawStroke>() }
    val currentStrokePoints = remember { mutableStateListOf<Offset>() }
    var isDrawingActive by remember { mutableStateOf(false) }
    var currentColor by remember { mutableStateOf(Color.Red) }
    var currentStrokeWidth by remember { mutableStateOf(4f) }
    var activeDrawTool by remember { mutableStateOf<DrawTool?>(DrawTool.BRUSH) }
    var eraserPos by remember { mutableStateOf<Offset?>(null) }
    var eraserWidth by remember { mutableStateOf(24f) }

    val isEraser = activeDrawTool == DrawTool.ERASER
    val isBrushActive = activeDrawTool == DrawTool.BRUSH
    val eraseRadiusPx = with(density) { eraserWidth.dp.toPx() }

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
            originalStatusColor?.let { if (it != window.statusBarColor) window.statusBarColor = it }
            originalNavColor?.let { if (it != window.navigationBarColor) window.navigationBarColor = it }
            insetsController?.apply {
                originalLightStatus?.let { isAppearanceLightStatusBars = it }
                originalLightNav?.let { isAppearanceLightNavigationBars = it }
            }
        }
    }

    val drawColors = listOf(
        Color.Black, Color.White, Color.Red, Color(0xFFFF9900),
        Color.Yellow, Color.Green, Color.Blue
    )

    val touchSlopPx = with(density) { 48.dp.toPx() }
    val minCropSizePx = with(density) { 100.dp.toPx() }
    val handleSizePx = with(density) { 6.dp.toPx() }

    fun saveEditedBitmap(bitmap: Bitmap) {
        val editedFile = File(context.filesDir, "images/edited_${System.currentTimeMillis()}.jpg")
        editedFile.parentFile?.mkdirs()
        FileOutputStream(editedFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        stagedEditedPath = editedFile.absolutePath
        currentBitmapUri = Uri.parse("file://${editedFile.absolutePath}")
    }

    fun commitEditedBitmapIfSelected() {
        val paths = app.pickerEditedPaths.toMutableMap()
        val stagedPath = stagedEditedPath
        if (stagedPath != null) {
            paths[photoIndex] = stagedPath
        } else {
            paths.remove(photoIndex)
        }
        app.pickerEditedPaths = paths
    }

    fun mergeDrawingsToBitmap(source: Bitmap): Bitmap {
        if (completedStrokes.isEmpty()) return source
        val iw = imageDisplaySize.width
        val ih = imageDisplaySize.height
        if (iw <= 0 || ih <= 0) return source
        val scaleX = source.width.toFloat() / iw
        val scaleY = source.height.toFloat() / ih
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        completedStrokes.forEach { stroke ->
            paint.color = stroke.color.toArgb()
            paint.strokeWidth = stroke.strokeWidth * scaleX
            val path = android.graphics.Path()
            stroke.points.forEachIndexed { i, pt ->
                if (i == 0) path.moveTo(pt.x * scaleX, pt.y * scaleY)
                else path.lineTo(pt.x * scaleX, pt.y * scaleY)
            }
            canvas.drawPath(path, paint)
        }
        return result
    }

    fun applyCropAndSave() {
        val bitmap = workingBitmap ?: return
        val rect = cropRect ?: return
        val iw = imageDisplaySize.width
        val ih = imageDisplaySize.height
        if (iw <= 0 || ih <= 0) return

        // Crop rect is now in inner Box (image display) coordinates
        val imageRect = Rect(
            rect.left.coerceIn(0f, iw.toFloat()),
            rect.top.coerceIn(0f, ih.toFloat()),
            rect.right.coerceIn(0f, iw.toFloat()),
            rect.bottom.coerceIn(0f, ih.toFloat())
        )

        val scaleX = bitmap.width.toFloat() / iw
        val scaleY = bitmap.height.toFloat() / ih
        val cx = (imageRect.left * scaleX).roundToInt().coerceIn(0, bitmap.width)
        val cy = (imageRect.top * scaleY).roundToInt().coerceIn(0, bitmap.height)
        val cw = (imageRect.width * scaleX).roundToInt().coerceAtMost(bitmap.width - cx)
        val ch = (imageRect.height * scaleY).roundToInt().coerceAtMost(bitmap.height - cy)
        if (cw <= 0 || ch <= 0) return

        try {
            var cropped = Bitmap.createBitmap(bitmap, cx, cy, cw, ch)
            // Merge existing drawings into cropped result
            if (completedStrokes.isNotEmpty()) {
                val cropScaleX = cropped.width.toFloat() / imageRect.width
                val cropScaleY = cropped.height.toFloat() / imageRect.height
                val drawBitmap = cropped.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = android.graphics.Canvas(drawBitmap)
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                }
                completedStrokes.forEach { stroke ->
                    paint.color = stroke.color.toArgb()
                    paint.strokeWidth = stroke.strokeWidth * cropScaleX
                    val path = android.graphics.Path()
                    stroke.points.forEachIndexed { i, pt ->
                        val sx = (pt.x - imageRect.left) * cropScaleX
                        val sy = (pt.y - imageRect.top) * cropScaleY
                        if (i == 0) path.moveTo(sx, sy)
                        else path.lineTo(sx, sy)
                    }
                    canvas.drawPath(path, paint)
                }
                cropped = drawBitmap
            }
            saveEditedBitmap(cropped)
            completedStrokes.clear()
        } catch (_: Exception) {}
    }

    fun finalizeDrawing() {
        if (isDrawingActive && currentStrokePoints.size > 1) {
            completedStrokes.add(
                DrawStroke(
                    points = currentStrokePoints.toList(),
                    color = currentColor,
                    strokeWidth = currentStrokeWidth
                )
            )
            currentStrokePoints.clear()
        }
        isDrawingActive = false
    }

    fun rotateBitmap90() {
        val bitmap = workingBitmap ?: return
        try {
            val matrix = Matrix()
            matrix.postRotate(90f)
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            saveEditedBitmap(rotated)
            cropRect = null
        } catch (_: Exception) {}
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar — hide during crop mode (crop has its own top bar)
            if (!isCropping) {
                TopAppBar(
                    title = { Text("编辑图片", color = WeChatWhite, fontWeight = FontWeight.Medium) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = WeChatWhite)
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            val bitmap = workingBitmap
                            if (bitmap != null) {
                                val merged = mergeDrawingsToBitmap(bitmap)
                                if (completedStrokes.isNotEmpty() || stagedEditedPath != null) {
                                    saveEditedBitmap(merged)
                                }
                            }
                            commitEditedBitmapIfSelected()
                            onConfirm()
                        }) {
                            Text("完成", color = WeChatGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xCC000000))
                )
            }

            // Image area
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val maxW = with(density) { maxWidth.toPx() }
                val maxH = with(density) { maxHeight.toPx() }

                if (workingBitmap != null && maxW > 0f && maxH > 0f) {
                    val bmpW = workingBitmap.width.toFloat()
                    val bmpH = workingBitmap.height.toFloat()
                    val fitScale = minOf(maxW / bmpW, maxH / bmpH)
                    val displayW = (bmpW * fitScale)
                    val displayH = (bmpH * fitScale)

                    // Dark background overlay during crop mode (fills empty space around image)
                    if (isCropping) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f))
                        )
                    }

                    // Image + drawing + crop layer — all in the same coordinate space
                    Box(
                        modifier = Modifier
                            .size(
                                width = with(density) { displayW.toDp() },
                                height = with(density) { displayH.toDp() }
                            )
                            .clipToBounds()
                            .onSizeChanged { imageDisplaySize = it }
                    ) {
                        Image(
                            bitmap = workingBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )

                        // Drawing canvas overlay — visible in DRAW mode or if strokes exist
                        if (isDrawing || completedStrokes.isNotEmpty()) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(isDrawing, activeDrawTool) {
                                        if (!isDrawing) return@pointerInput
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val pos = event.changes.firstOrNull()?.position ?: continue
                                                val pressed = event.changes.firstOrNull()?.pressed == true

                                                if (activeDrawTool == DrawTool.ERASER) {
                                                    eraserPos = if (pressed) pos else null
                                                    if (pressed) {
                                                        val toRemove = mutableSetOf<Int>()
                                                        completedStrokes.forEachIndexed { idx, stroke ->
                                                            for (pt in stroke.points) {
                                                                val dx = pos.x - pt.x
                                                                val dy = pos.y - pt.y
                                                                if (dx * dx + dy * dy < eraseRadiusPx * eraseRadiusPx) {
                                                                    toRemove.add(idx)
                                                                    break
                                                                }
                                                            }
                                                        }
                                                        toRemove.sortedDescending().forEach { completedStrokes.removeAt(it) }
                                                    }
                                                } else if (activeDrawTool == DrawTool.BRUSH) {
                                                    eraserPos = null
                                                    if (pressed) {
                                                        if (!isDrawingActive) {
                                                            isDrawingActive = true
                                                            currentStrokePoints.clear()
                                                        }
                                                        currentStrokePoints.add(pos)
                                                    } else if (isDrawingActive) {
                                                        isDrawingActive = false
                                                        if (currentStrokePoints.size > 1) {
                                                            completedStrokes.add(
                                                                DrawStroke(
                                                                    points = currentStrokePoints.toList(),
                                                                    color = currentColor,
                                                                    strokeWidth = currentStrokeWidth
                                                                )
                                                            )
                                                        }
                                                        currentStrokePoints.clear()
                                                    }
                                                } else {
                                                    eraserPos = null
                                                }
                                            }
                                        }
                                    }
                            ) {
                                completedStrokes.forEach { stroke ->
                                    if (stroke.points.size >= 2) {
                                        val path = Path().apply {
                                            moveTo(stroke.points[0].x, stroke.points[0].y)
                                            for (i in 1 until stroke.points.size) {
                                                lineTo(stroke.points[i].x, stroke.points[i].y)
                                            }
                                        }
                                        drawPath(path = path, color = stroke.color,
                                            style = Stroke(width = stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                    }
                                }
                                if (currentStrokePoints.size > 1 && isDrawingActive) {
                                    val path = Path().apply {
                                        moveTo(currentStrokePoints[0].x, currentStrokePoints[0].y)
                                        for (i in 1 until currentStrokePoints.size) {
                                            lineTo(currentStrokePoints[i].x, currentStrokePoints[i].y)
                                        }
                                    }
                                    drawPath(path = path, color = currentColor,
                                        style = Stroke(width = currentStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                                // Eraser cursor
                                if (isEraser && eraserPos != null) {
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.8f),
                                        radius = eraseRadiusPx,
                                        center = eraserPos!!,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.2f),
                                        radius = eraseRadiusPx,
                                        center = eraserPos!!
                                    )
                                }
                            }
                        }

                        // Crop overlay — inside inner Box, same coordinate space as image and drawings
                        CropOverlayAnimated(
                            visible = isCropping,
                            displayWidth = displayW,
                            displayHeight = displayH,
                            handleSize = handleSizePx,
                            touchSlop = touchSlopPx,
                            minCropSize = minCropSizePx,
                            cropRect = cropRect,
                            onCropRectInit = { r -> cropRect = r },
                            onRectChanged = { cropRect = it }
                        )
                    }
                }
            }

            // Bottom toolbar
            if (isCropping) {
                CropBottomBar(
                    onRotate = { rotateBitmap90() },
                    onCancel = {
                        cropRect = null
                        editMode = EditMode.NONE
                    },
                    onConfirm = {
                        applyCropAndSave()
                        cropRect = null
                        editMode = EditMode.NONE
                    }
                )
            } else {
                // NONE or DRAW mode toolbar
                Surface(color = Color(0xCC000000), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Color picker row — only when brush is active
                        if (isDrawing && isBrushActive) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                drawColors.forEach { color ->
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

                        // Stroke width slider — brush mode
                        if (isDrawing && isBrushActive) {
                            Slider(
                                value = currentStrokeWidth,
                                onValueChange = { currentStrokeWidth = it },
                                valueRange = 2f..14f,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = WeChatGreen,
                                    activeTrackColor = WeChatGreen
                                )
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        // Eraser width slider — eraser mode
                        if (isDrawing && isEraser) {
                            Slider(
                                value = eraserWidth,
                                onValueChange = { eraserWidth = it },
                                valueRange = 12f..60f,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = WeChatGreen,
                                    activeTrackColor = WeChatGreen
                                )
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        // Main tool buttons row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Crop button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    finalizeDrawing()
                                    editMode = if (editMode == EditMode.CROP) EditMode.NONE else EditMode.CROP
                                }) {
                                    Icon(
                                        Icons.Default.Crop,
                                        contentDescription = "裁剪",
                                        tint = if (isCropping) WeChatGreen else WeChatWhite,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Text("裁剪", fontSize = 10.sp, color = if (isCropping) WeChatGreen else WeChatWhite)
                            }

                            // Draw button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    if (editMode != EditMode.DRAW) {
                                        editMode = EditMode.DRAW
                                        activeDrawTool = DrawTool.BRUSH
                                    } else {
                                        if (isBrushActive) {
                                            activeDrawTool = null
                                        } else {
                                            finalizeDrawing()
                                            activeDrawTool = DrawTool.BRUSH
                                        }
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Brush,
                                        contentDescription = "涂鸦",
                                        tint = if (isDrawing) WeChatGreen else WeChatWhite,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Text("涂鸦", fontSize = 10.sp, color = if (isDrawing) WeChatGreen else WeChatWhite)
                            }

                            // Eraser + Undo — only in DRAW mode
                            if (isDrawing) {
                                // Eraser button
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(onClick = {
                                        if (isEraser) {
                                            activeDrawTool = null
                                        } else {
                                            finalizeDrawing()
                                            activeDrawTool = DrawTool.ERASER
                                        }
                                    }) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (isEraser) WeChatGreen else Color.White.copy(alpha = 0.3f),
                                                    RoundedCornerShape(4.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp, 16.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(Color.White)
                                            )
                                        }
                                    }
                                    Text("橡皮擦", fontSize = 10.sp, color = if (isEraser) WeChatGreen else WeChatWhite)
                                }

                                val canUndo = completedStrokes.isNotEmpty()
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = {
                                            if (canUndo) completedStrokes.removeAt(completedStrokes.lastIndex)
                                        },
                                        enabled = canUndo
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = "撤销",
                                            tint = if (canUndo) WeChatWhite else Color.Gray,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Text("撤销", fontSize = 10.sp, color = if (canUndo) WeChatWhite else Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CropOverlayAnimated(
    visible: Boolean,
    displayWidth: Float,
    displayHeight: Float,
    handleSize: Float,
    touchSlop: Float,
    minCropSize: Float,
    cropRect: Rect?,
    onCropRectInit: (Rect) -> Unit,
    onRectChanged: (Rect) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) +
                slideInVertically(animationSpec = tween(300)) { it / 2 },
        exit = fadeOut(animationSpec = tween(200)) +
                slideOutVertically(animationSpec = tween(200)) { it / 2 }
    ) {
        val dw = displayWidth
        val dh = displayHeight

        LaunchedEffect(dw, dh) {
            if (cropRect == null && dw > 0 && dh > 0) {
                onCropRectInit(Rect(0f, 0f, dw, dh))
            }
        }

        val rect = cropRect ?: Rect(0f, 0f, dw, dh)

        CropOverlay(
            rect = rect,
            displayWidth = dw,
            displayHeight = dh,
            handleSize = handleSize,
            touchSlop = touchSlop,
            minCropSize = minCropSize,
            onRectChanged = onRectChanged
        )
    }
}

@Composable
private fun CropBottomBar(
    onRotate: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Surface(color = Color(0xCC000000), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rotate button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onRotate, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.RotateRight,
                        contentDescription = "旋转",
                        tint = WeChatWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text("旋转", fontSize = 10.sp, color = WeChatWhite)
            }

            TextButton(onClick = onCancel) {
                Text("取消", color = WeChatWhite, fontSize = 16.sp)
            }

            TextButton(onClick = onConfirm) {
                Text("确认", color = WeChatGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun CropOverlay(
    rect: Rect,
    displayWidth: Float,
    displayHeight: Float,
    handleSize: Float,
    touchSlop: Float,
    minCropSize: Float,
    onRectChanged: (Rect) -> Unit
) {
    var activeHandle by remember { mutableStateOf<CropHandle?>(null) }
    val halfH = handleSize / 2f

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(rect) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pos = event.changes.firstOrNull()?.position ?: continue
                        val pressed = event.changes.firstOrNull()?.pressed == true

                        if (pressed && activeHandle == null) {
                            activeHandle = hitTestHandle(rect, pos, touchSlop)
                        } else if (!pressed) {
                            activeHandle = null
                        }

                        if (activeHandle != null && pressed) {
                            val prev = event.changes.firstOrNull()?.previousPosition ?: pos
                            val delta = Offset(pos.x - prev.x, pos.y - prev.y)
                            if (delta.x != 0f || delta.y != 0f) {
                                val newRect = adjustCropRect(rect, activeHandle!!, delta, displayWidth, displayHeight, minCropSize)
                                onRectChanged(newRect)
                            }
                            event.changes.firstOrNull()?.consume()
                        }
                    }
                }
            }
    ) {
        // Dark overlay outside crop
        drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset.Zero, size = Size(displayWidth, rect.top))
        drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, rect.bottom), size = Size(displayWidth, displayHeight - rect.bottom))
        drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, rect.top), size = Size(rect.left, rect.height))
        drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(rect.right, rect.top), size = Size(displayWidth - rect.right, rect.height))

        // White border
        drawRect(color = Color.White, topLeft = rect.topLeft, size = rect.size, style = Stroke(width = 2.dp.toPx()))

        // Corner handles — small white squares half overlapping the border
        drawRect(color = Color.White, topLeft = Offset(rect.left - halfH, rect.top - halfH), size = Size(handleSize, handleSize))
        drawRect(color = Color.White, topLeft = Offset(rect.right - halfH, rect.top - halfH), size = Size(handleSize, handleSize))
        drawRect(color = Color.White, topLeft = Offset(rect.left - halfH, rect.bottom - halfH), size = Size(handleSize, handleSize))
        drawRect(color = Color.White, topLeft = Offset(rect.right - halfH, rect.bottom - halfH), size = Size(handleSize, handleSize))

        // Edge handles — small white bars
        val cx = rect.left + rect.width / 2
        val cy = rect.top + rect.height / 2
        val barW = handleSize * 3f
        val barH = handleSize * 0.6f
        drawRect(color = Color.White, topLeft = Offset(cx - barW / 2, rect.top - barH / 2), size = Size(barW, barH))
        drawRect(color = Color.White, topLeft = Offset(cx - barW / 2, rect.bottom - barH / 2), size = Size(barW, barH))
        drawRect(color = Color.White, topLeft = Offset(rect.left - barH / 2, cy - barW / 2), size = Size(barH, barW))
        drawRect(color = Color.White, topLeft = Offset(rect.right - barH / 2, cy - barW / 2), size = Size(barH, barW))

        // Grid lines
        for (i in 1..2) {
            val lx = rect.left + rect.width * i / 3
            drawLine(Color.White.copy(alpha = 0.3f), Offset(lx, rect.top), Offset(lx, rect.bottom), strokeWidth = 1.dp.toPx())
            val ly = rect.top + rect.height * i / 3
            drawLine(Color.White.copy(alpha = 0.3f), Offset(rect.left, ly), Offset(rect.right, ly), strokeWidth = 1.dp.toPx())
        }
    }
}

private fun hitTestHandle(rect: Rect, pos: Offset, touchSlop: Float): CropHandle {
    fun near(p: Offset) = abs(pos.x - p.x) < touchSlop && abs(pos.y - p.y) < touchSlop
    val cx = rect.left + rect.width / 2
    val cy = rect.top + rect.height / 2
    return when {
        near(rect.topLeft) -> CropHandle.TOP_LEFT
        near(Offset(rect.right, rect.top)) -> CropHandle.TOP_RIGHT
        near(Offset(rect.left, rect.bottom)) -> CropHandle.BOTTOM_LEFT
        near(rect.bottomRight) -> CropHandle.BOTTOM_RIGHT
        near(Offset(cx, rect.top)) -> CropHandle.TOP
        near(Offset(cx, rect.bottom)) -> CropHandle.BOTTOM
        near(Offset(rect.left, cy)) -> CropHandle.LEFT
        near(Offset(rect.right, cy)) -> CropHandle.RIGHT
        rect.contains(pos) -> CropHandle.CENTER
        else -> CropHandle.CENTER
    }
}

private fun adjustCropRect(rect: Rect, handle: CropHandle, delta: Offset, maxW: Float, maxH: Float, minSize: Float): Rect {
    return when (handle) {
        CropHandle.TOP_LEFT -> {
            val nl = (rect.left + delta.x).coerceIn(0f, rect.right - minSize)
            val nt = (rect.top + delta.y).coerceIn(0f, rect.bottom - minSize)
            Rect(nl, nt, rect.right, rect.bottom)
        }
        CropHandle.TOP_RIGHT -> {
            val nr = (rect.right + delta.x).coerceIn(rect.left + minSize, maxW)
            val nt = (rect.top + delta.y).coerceIn(0f, rect.bottom - minSize)
            Rect(rect.left, nt, nr, rect.bottom)
        }
        CropHandle.BOTTOM_LEFT -> {
            val nl = (rect.left + delta.x).coerceIn(0f, rect.right - minSize)
            val nb = (rect.bottom + delta.y).coerceIn(rect.top + minSize, maxH)
            Rect(nl, rect.top, rect.right, nb)
        }
        CropHandle.BOTTOM_RIGHT -> {
            val nr = (rect.right + delta.x).coerceIn(rect.left + minSize, maxW)
            val nb = (rect.bottom + delta.y).coerceIn(rect.top + minSize, maxH)
            Rect(rect.left, rect.top, nr, nb)
        }
        CropHandle.TOP -> {
            val nt = (rect.top + delta.y).coerceIn(0f, rect.bottom - minSize)
            Rect(rect.left, nt, rect.right, rect.bottom)
        }
        CropHandle.BOTTOM -> {
            val nb = (rect.bottom + delta.y).coerceIn(rect.top + minSize, maxH)
            Rect(rect.left, rect.top, rect.right, nb)
        }
        CropHandle.LEFT -> {
            val nl = (rect.left + delta.x).coerceIn(0f, rect.right - minSize)
            Rect(nl, rect.top, rect.right, rect.bottom)
        }
        CropHandle.RIGHT -> {
            val nr = (rect.right + delta.x).coerceIn(rect.left + minSize, maxW)
            Rect(rect.left, rect.top, nr, rect.bottom)
        }
        CropHandle.CENTER -> {
            val ndx = delta.x.coerceIn(-rect.left, maxW - rect.right)
            val ndy = delta.y.coerceIn(-rect.top, maxH - rect.bottom)
            rect.translate(ndx, ndy)
        }
    }
}

private fun loadBitmapFromUri(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val path = uri.path ?: uri.toString()
        if (path.startsWith("/") && File(path).exists()) {
            BitmapFactory.decodeFile(path)
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
    } catch (_: Exception) { null }
}

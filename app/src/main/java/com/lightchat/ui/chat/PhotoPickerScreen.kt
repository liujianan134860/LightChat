package com.lightchat.ui.chat

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

import com.lightchat.LightChatApplication
import com.lightchat.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPickerScreen(
    @Suppress("UNUSED_PARAMETER") conversationId: String,
    onBack: () -> Unit,
    onSend: () -> Unit,
    onPhotoClick: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = LightChatApplication.instance

    val allPhotos = remember { mutableStateListOf<PhotoItem>() }
    var selectedIndices by remember { mutableStateOf(emptyList<Int>()) }
    val editedPaths = remember { mutableStateOf(app.pickerEditedPaths) }
    var isLoading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var loadingOffset by remember { mutableIntStateOf(-1) }
    val pageSize = 48

    // Album state
    val albums = remember { mutableStateListOf<AlbumInfo>() }
    var currentAlbum by remember { mutableStateOf<AlbumInfo?>(null) }
    var showAlbumDropdown by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()

    fun updateSelectedIndices(next: List<Int>) {
        val normalized = next.distinct().filter { it in 0 until allPhotos.size }
        selectedIndices = normalized
        app.pickerSelectedIndices = normalized
    }

    suspend fun loadPhotoPage(reset: Boolean) {
        val offset = if (reset) 0 else allPhotos.size
        if (!reset && !hasMore) return
        if (isLoading || loadingOffset == offset) return
        isLoading = true
        loadingOffset = offset
        try {
            if (reset) {
                allPhotos.clear()
                hasMore = true
                runCatching { gridState.scrollToItem(0) }
            }
            val beforeSize = allPhotos.size
            loadPhotos(context, allPhotos, pageSize, offset, currentAlbum?.bucketId) { nextHasMore ->
                hasMore = nextHasMore
            }
            if (!reset && allPhotos.size == beforeSize) {
                hasMore = false
            }
        } finally {
            isLoading = false
            loadingOffset = -1
        }
    }

    LaunchedEffect(Unit) {
        selectedIndices = app.pickerSelectedIndices
        editedPaths.value = app.pickerEditedPaths
    }

    // Sync edited paths when returning from edit screen
    LaunchedEffect(app.pickerEditedVersion) {
        if (app.pickerEditedVersion > 0) {
            editedPaths.value = app.pickerEditedPaths
        }
    }

    // Sync to app
    LaunchedEffect(selectedIndices) {
        app.pickerSelectedIndices = selectedIndices
    }
    LaunchedEffect(allPhotos.size) {
        app.pickerAllPhotoUris = allPhotos.map { it.uri }
    }

    fun clearPickerState() {
        selectedIndices = emptyList()
        editedPaths.value = emptyMap()
        app.pickerSelectedIndices = emptyList()
        app.pickerEditedPaths = emptyMap()
        app.pickerAllPhotoUris = emptyList()
    }

    BackHandler {
        clearPickerState()
        onBack()
    }

    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        @Suppress("DEPRECATION")
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, mediaPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) {
            scope.launch {
                loadPhotoPage(reset = true)
                loadAlbums(context, albums)
            }
        }
    }

    // Load albums and initial photos
    LaunchedEffect(permissionGranted) {
        if (permissionGranted && allPhotos.isEmpty()) {
            delay(120)
            loadPhotoPage(reset = true)
            loadAlbums(context, albums)
        }
    }

    // Progressive loading on scroll
    LaunchedEffect(gridState, currentAlbum?.bucketId) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            PhotoLoadSignal(
                lastVisible = lastVisible,
                loadedCount = allPhotos.size,
                hasMore = hasMore,
                isLoading = isLoading
            )
        }
            .distinctUntilChanged()
            .filter { it.loadedCount > 0 && it.hasMore && !it.isLoading && it.lastVisible >= it.loadedCount - 18 }
            .collect {
                loadPhotoPage(reset = false)
            }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Top bar
        Column {
            TopAppBar(
                title = {
                    Box(contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showAlbumDropdown = true }
                        ) {
                            Text(
                                currentAlbum?.displayName ?: "所有图片",
                                color = Color.Black,
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "切换相册",
                                tint = Color.Black
                            )
                        }
                        DropdownMenu(
                            expanded = showAlbumDropdown,
                            onDismissRequest = { showAlbumDropdown = false },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .heightIn(max = 400.dp)
                                .background(PlaceholderGray),
                            offset = DpOffset(0.dp, 8.dp)
                        ) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    val allThumbUri = allPhotos.firstOrNull()?.uri
                                    Box(
                                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(BubbleGray),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        allThumbUri?.let { AsyncPickerThumbnail(uri = it, maxDim = 100, modifier = Modifier.fillMaxSize()) }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text("所有图片", fontSize = 15.sp)
                                    if (currentAlbum == null) {
                                        Spacer(Modifier.weight(1f))
                                        Text("✓", color = WeChatGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            },
                            onClick = {
                                currentAlbum = null
                                showAlbumDropdown = false
                                scope.launch {
                                    loadPhotoPage(reset = true)
                                }
                            }
                        )
                        HorizontalDivider()
                        albums.forEach { album ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(BubbleGray),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            album.sampleUri?.let { AsyncPickerThumbnail(uri = it, maxDim = 100, modifier = Modifier.fillMaxSize()) }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text("${album.displayName} (${album.photoCount})", fontSize = 15.sp)
                                        if (currentAlbum == album) {
                                            Spacer(Modifier.weight(1f))
                                            Text("✓", color = WeChatGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                    }
                                },
                                onClick = {
                                    currentAlbum = album
                                    showAlbumDropdown = false
                                    scope.launch {
                                        loadPhotoPage(reset = true)
                                    }
                                }
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = {
                    clearPickerState()
                    onBack()
                }) {
                    Text("✕", fontSize = 20.sp, color = Color.Black)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarBackground)
        )
        HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
    }

        if (!permissionGranted) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("需要访问相册权限", fontSize = 16.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { permissionLauncher.launch(mediaPermission) },
                        colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen)
                    ) { Text("授予权限") }
                }
            }
        } else if (allPhotos.isEmpty() && !isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无图片", fontSize = 16.sp, color = TextSecondary)
            }
        } else {
            // Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(allPhotos.size, key = { allPhotos[it].mediaStoreId }) { index ->
                    val isSelected = selectedIndices.contains(index)
                    val selectionNumber = selectedIndices.indexOf(index).let { if (it >= 0) it + 1 else 0 }
                    val editPath = editedPaths.value[index]
                    val displayUri = if (editPath != null) Uri.parse("file://$editPath") else allPhotos[index].uri
                    PhotoGridCell(
                        uri = displayUri,
                        isSelected = isSelected,
                        selectionNumber = selectionNumber,
                        onClickPhoto = { onPhotoClick(index) },
                        onToggleCheckbox = {
                            val current = selectedIndices.toMutableList()
                            if (current.contains(index)) current.remove(index) else current.add(index)
                            updateSelectedIndices(current)
                        }
                    )
                }

                if (isLoading) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = WeChatGreen
                            )
                        }
                    }
                }
            }
        }

        // Bottom bar (visible when selections exist)
        AnimatedVisibility(visible = selectedIndices.isNotEmpty()) {
            Surface(color = WeChatWhite, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Selected thumbnails
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(selectedIndices.size) { idx ->
                            val photoIdx = selectedIndices[idx]
                            val photo = allPhotos.getOrNull(photoIdx) ?: return@items
                            val displayUri = editedPaths.value[photoIdx]?.let { Uri.parse("file://$it") } ?: photo.uri
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BubbleGray)
                            ) {
                                AsyncPickerThumbnail(uri = displayUri, maxDim = 80, modifier = Modifier.fillMaxSize())
                                // Number badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(1.dp)
                                        .size(15.dp)
                                        .clip(CircleShape)
                                        .background(WeChatGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${idx + 1}",
                                        color = WeChatWhite,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Send button
                    Button(
                        onClick = {
                            val uris = selectedIndices.map { idx ->
                                editedPaths.value[idx]?.let { Uri.parse("file://$it") }
                                    ?: allPhotos.getOrNull(idx)?.uri ?: Uri.EMPTY
                            }
                            app.pendingImageUris = uris
                            clearPickerState()
                            onSend()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text("发送(${selectedIndices.size})", color = WeChatWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoGridCell(
    uri: Uri,
    isSelected: Boolean,
    selectionNumber: Int,
    onClickPhoto: () -> Unit,
    onToggleCheckbox: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(BubbleGray)
            .clickable { onClickPhoto() },
        contentAlignment = Alignment.Center
    ) {
        AsyncPickerThumbnail(uri = uri, maxDim = 220, modifier = Modifier.fillMaxSize())

        // Selection overlay
        if (isSelected) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f))
            )
        }

        // Checkbox (top-right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isSelected) WeChatGreen else Color.Black.copy(alpha = 0.3f))
                .border(1.5.dp, if (isSelected) WeChatGreen else Color.White.copy(alpha = 0.8f), CircleShape)
                .clickable { onToggleCheckbox() },
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
    }
}

@Composable
private fun AsyncPickerThumbnail(
    uri: Uri,
    maxDim: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = PhotoThumbnailMemoryCache.get(uri, maxDim), uri, maxDim) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                PhotoThumbnailMemoryCache.get(uri, maxDim)
                    ?: decodeThumbForPicker(context.applicationContext, uri, maxDim)
                        ?.also { PhotoThumbnailMemoryCache.put(uri, maxDim, it) }
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

data class PhotoItem(
    val uri: Uri,
    val bucketId: Long,
    val dateAdded: Long,
    val mediaStoreId: Long
)

data class AlbumInfo(
    val bucketId: Long?,
    val displayName: String,
    val photoCount: Int,
    val sampleUri: Uri? = null
)

private data class PhotoLoadSignal(
    val lastVisible: Int,
    val loadedCount: Int,
    val hasMore: Boolean,
    val isLoading: Boolean
)

private object PhotoThumbnailMemoryCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 10).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(uri: Uri, maxDim: Int): Bitmap? = cache.get(cacheKey(uri, maxDim))
    fun put(uri: Uri, maxDim: Int, bitmap: Bitmap) {
        cache.put(cacheKey(uri, maxDim), bitmap)
    }

    private fun cacheKey(uri: Uri, maxDim: Int): String = "$uri@$maxDim"
}

private fun decodeThumbForPicker(context: android.content.Context, uri: Uri, maxDim: Int): Bitmap? {
    return try {
        val path = uri.path ?: uri.toString()
        if (path.startsWith("/") && java.io.File(path).exists()) {
            val opts = BitmapFactory.Options()
            opts.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, opts)
            val scale = maxOf(1, maxOf(opts.outWidth, opts.outHeight) / maxDim)
            opts.inJustDecodeBounds = false
            opts.inSampleSize = scale
            BitmapFactory.decodeFile(path, opts)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(maxDim, maxDim), null)
        } else {
            MediaStore.Images.Thumbnails.getThumbnail(
                context.contentResolver,
                ContentUris.parseId(uri),
                MediaStore.Images.Thumbnails.MINI_KIND,
                null
            )
        }
    } catch (_: Exception) { null }
}

private suspend fun loadAlbums(
    context: android.content.Context,
    targetList: MutableList<AlbumInfo>
) {
    val start = System.currentTimeMillis()
    val result = withContext(Dispatchers.IO) {
        val loadedAlbums = mutableListOf<AlbumInfo>()
    try {
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media._ID
        )
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )
        val bucketCounts = mutableMapOf<Long, Int>()
        val bucketNames = mutableMapOf<Long, String>()
        val bucketSamples = mutableMapOf<Long, Long>() // bucketId -> first mediaId
        cursor?.use {
            val bucketCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (it.moveToNext()) {
                val bucketId = it.getLong(bucketCol)
                bucketCounts[bucketId] = (bucketCounts[bucketId] ?: 0) + 1
                if (!bucketNames.containsKey(bucketId)) {
                    bucketNames[bucketId] = it.getString(nameCol) ?: "Unknown"
                    bucketSamples[bucketId] = it.getLong(idCol)
                }
            }
        }
        bucketNames.forEach { (id, name) ->
            val sampleUri = bucketSamples[id]?.let { mediaId ->
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
            }
            loadedAlbums.add(AlbumInfo(id, name, bucketCounts[id] ?: 0, sampleUri))
        }
    } catch (e: Exception) {
        Log.w("LightChatPhotoPicker", "loadAlbums failed: ${e.message}")
    }
        loadedAlbums
    }
    targetList.clear()
    targetList.addAll(result)
    Log.d("LightChatPhotoPicker", "loadAlbums count=${result.size} cost=${System.currentTimeMillis() - start}ms")
}

private suspend fun loadPhotos(
    context: android.content.Context,
    targetList: MutableList<PhotoItem>,
    pageSize: Int,
    offset: Int,
    bucketId: Long?,
    onResult: (hasMore: Boolean) -> Unit
) {
    val start = System.currentTimeMillis()
    val result = withContext(Dispatchers.IO) {
        try {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val cursor: Cursor? = queryImageCursor(context, projection, bucketId)
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val dateCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val batch = mutableListOf<PhotoItem>()
            var skipped = 0
            while (skipped < offset && it.moveToNext()) {
                skipped++
            }
            while (batch.size < pageSize && it.moveToNext()) {
                val mediaId = it.getLong(idCol)
                batch.add(
                    PhotoItem(
                        uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId),
                        bucketId = it.getLong(bucketCol),
                        dateAdded = it.getLong(dateCol),
                        mediaStoreId = mediaId
                    )
                )
            }
            return@withContext batch to it.moveToNext()
        }
        emptyList<PhotoItem>() to false
    } catch (e: Exception) {
        Log.w(
            "LightChatPhotoPicker",
            "loadPhotos failed offset=$offset bucketId=$bucketId: ${e.message}"
        )
        emptyList<PhotoItem>() to false
    }
    }
    targetList.addAll(result.first)
    onResult(result.second)
    Log.d(
        "LightChatPhotoPicker",
        "loadPhotos offset=$offset count=${result.first.size} hasMore=${result.second} cost=${System.currentTimeMillis() - start}ms"
    )
}

private fun queryImageCursor(
    context: android.content.Context,
    projection: Array<String>,
    bucketId: Long?
): Cursor? {
    val resolver = context.contentResolver
    val selection = if (bucketId != null) {
        "${MediaStore.Images.Media.BUCKET_ID} = ?"
    } else {
        null
    }
    val selectionArgs = if (bucketId != null) arrayOf(bucketId.toString()) else null

    runCatching {
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )
    }.onSuccess { cursor ->
        if (cursor != null) return cursor
    }.onFailure { e ->
        Log.w("LightChatPhotoPicker", "legacy query failed, fallback to bundle query: ${e.message}")
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    return runCatching {
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media._ID)
            )
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        }
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            args,
            null
        )
    }.getOrElse { e ->
        Log.w("LightChatPhotoPicker", "bundle query failed: ${e.message}")
        null
    }
}

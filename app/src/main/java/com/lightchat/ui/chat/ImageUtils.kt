package com.lightchat.ui.chat

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lightchat.LightChatApplication
import com.lightchat.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val imageCacheVersions = mutableStateMapOf<String, Int>()
const val IMAGE_LOAD_FAILED_PATH = "__lightchat_image_load_failed__"

private fun notifyImageCacheChanged(messageId: String) {
    imageCacheVersions[messageId] = (imageCacheVersions[messageId] ?: 0) + 1
}

fun calculateBitmapInSampleSize(outWidth: Int, outHeight: Int, maxDim: Int): Int {
    if (outWidth <= 0 || outHeight <= 0 || maxDim <= 0) return 1
    val ratio = maxOf(outWidth, outHeight).toFloat() / maxDim.toFloat()
    var sampleSize = 1
    while (sampleSize * 2 <= ratio) {
        sampleSize *= 2
    }
    return sampleSize
}

fun decodeSampledBitmap(path: String, maxDim: Int = 2048): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        val sampleSize = calculateBitmapInSampleSize(opts.outWidth, opts.outHeight, maxDim)
        BitmapFactory.Options().apply { inSampleSize = sampleSize }.let {
            BitmapFactory.decodeFile(path, it)
        }
    } catch (_: Exception) {
        null
    }
}

fun generateThumbnail(originalPath: String, maxDim: Int = 200): String? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(originalPath, opts)
        if (opts.outWidth > 0 && opts.outHeight > 0 && opts.outWidth <= maxDim && opts.outHeight <= maxDim) {
            return originalPath
        }
        val bitmap = decodeSampledBitmap(originalPath, maxDim) ?: return null
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else bitmap
        val origFile = File(originalPath)
        // If the source is already a thumbnail file, overwrite it to avoid thumb_thumb_ naming
        val thumbFile = if (origFile.name.startsWith("thumb_")) {
            origFile
        } else {
            File(origFile.parent, "thumb_${origFile.name}")
        }
        FileOutputStream(thumbFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
        }
        if (scaled != bitmap) scaled.recycle()
        thumbFile.absolutePath
    } catch (_: Exception) {
        null
    }
}

private fun imageDir(context: Context): File {
    return PictureCacheManager.picturesDir(context)
}

fun thumbnailCacheFile(context: Context, messageId: String): File {
    return File(imageDir(context), "thumb_$messageId.jpg")
}

fun originalCacheFile(context: Context, messageId: String): File {
    return File(imageDir(context), "$messageId.jpg")
}

private fun originalFileForMessage(context: Context, message: Message): File? {
    val objectKeys = parseImageObjectKeys(message)
    val urls = parseImageUrls(message)
    return PictureCacheManager.originalFile(context, objectKeys.second, urls.second)
        ?: originalCacheFile(context, message.messageId)
}

private fun thumbnailFileForMessage(context: Context, message: Message): File? {
    val objectKeys = parseImageObjectKeys(message)
    val urls = parseImageUrls(message)
    return PictureCacheManager.thumbnailFile(context, objectKeys.first, urls.first)
        ?: thumbnailCacheFile(context, message.messageId)
}

@Composable
fun rememberProgressiveImagePath(
    context: Context,
    message: Message,
    downloadOriginal: Boolean = false,
    downloadAttempt: Long = 0L
): String {
    val urls = remember(message.extra) { parseImageUrls(message) }
    val objectKeys = remember(message.extra) { parseImageObjectKeys(message) }
    val fullImage = remember(objectKeys.second, urls.second) {
        originalFileForMessage(context, message)
    }
    val thumbImage = remember(objectKeys.first, urls.first) {
        thumbnailFileForMessage(context, message)
    }
    val localPath = remember(message.extra, message.content) {
        try {
            org.json.JSONObject(message.extra ?: "{}").optString("localPath", "")
                .takeIf { it.isNotBlank() && File(it).exists() }
        } catch (_: Exception) { null }
    }
    val cacheVersion = imageCacheVersions[message.messageId] ?: 0
    var displayPath by remember(message.messageId, message.content, urls.first, urls.second, cacheVersion) {
        mutableStateOf(
            when {
                fullImage != null && fullImage.exists() -> fullImage.absolutePath
                thumbImage != null && thumbImage.exists() -> thumbImage.absolutePath
                localPath != null -> localPath
                urls.first.isNotBlank() || urls.second.isNotBlank() -> ""
                message.content.isNotBlank() && File(message.content).exists() -> message.content
                else -> ""
            }
        )
    }
    LaunchedEffect(message.messageId, urls.first, urls.second, downloadOriginal, downloadAttempt) {
        val thumbnailUrl = urls.first
        val imageUrl = urls.second
        val thumbnailObjectKey = objectKeys.first
        val imageObjectKey = objectKeys.second
        if (thumbnailUrl.isNotBlank() && thumbImage != null && !thumbImage.exists() && (fullImage == null || !fullImage.exists())) {
            val result = PictureCacheManager.downloadToFile(
                target = thumbImage,
                imageUrl = thumbnailUrl,
                objectKey = thumbnailObjectKey,
                messageId = message.messageId,
                urlField = "thumbnailUrl"
            ) { refreshedUrl ->
                persistRefreshedUrl(message.messageId, message.extra, "thumbnailUrl", refreshedUrl)
            }
            if (result.success) {
                displayPath = thumbImage.absolutePath
                notifyImageCacheChanged(message.messageId)
            } else {
                displayPath = IMAGE_LOAD_FAILED_PATH
            }
        }
        if (!downloadOriginal || imageUrl.isBlank()) return@LaunchedEffect
        if (fullImage != null && fullImage.exists()) return@LaunchedEffect
        if (fullImage == null) return@LaunchedEffect
        val result = PictureCacheManager.downloadToFile(
            target = fullImage,
            imageUrl = imageUrl,
            objectKey = imageObjectKey,
            messageId = message.messageId,
            urlField = "imageUrl"
        ) { refreshedUrl ->
            persistRefreshedUrl(message.messageId, message.extra, "imageUrl", refreshedUrl)
        }
        if (result.success) {
            displayPath = fullImage.absolutePath
            notifyImageCacheChanged(message.messageId)
        } else if (displayPath.isBlank()) {
            displayPath = IMAGE_LOAD_FAILED_PATH
        }
    }
    return displayPath
}

private fun persistRefreshedUrl(messageId: String, extra: String?, key: String, newUrl: String) {
    try {
        val obj = org.json.JSONObject(extra ?: "{}")
        obj.put(key, newUrl)
        LightChatApplication.instance.messageDao.updateExtra(messageId, obj.toString())
    } catch (_: Exception) {}
}

fun hasRemoteImage(message: Message): Boolean {
    val urls = parseImageUrls(message)
    return urls.first.isNotBlank() || urls.second.isNotBlank()
}

suspend fun resolveOriginalImageForSave(context: Context, message: Message): File? {
    return withContext(Dispatchers.IO) {
        val fullImage = originalFileForMessage(context, message)
        if (fullImage != null && fullImage.exists()) return@withContext fullImage

        val localPath = try {
            org.json.JSONObject(message.extra ?: "{}").optString("localPath", "")
                .takeIf { it.isNotBlank() && File(it).exists() }
        } catch (_: Exception) { null }
        if (localPath != null) return@withContext File(localPath)

        val contentFile = message.content.takeIf { it.isNotBlank() }?.let { File(it) }
        if (contentFile != null && contentFile.exists()) return@withContext contentFile

        if (fullImage == null) return@withContext null
        val imageUrl = parseImageUrls(message).second
        val objectKey = parseImageObjectKeys(message).second
        if (imageUrl.isBlank()) return@withContext null
        val result = PictureCacheManager.downloadToFile(
            target = fullImage,
            imageUrl = imageUrl,
            objectKey = objectKey,
            messageId = message.messageId,
            urlField = "imageUrl"
        ) { refreshedUrl ->
            persistRefreshedUrl(message.messageId, message.extra, "imageUrl", refreshedUrl)
        }
        if (result.success && fullImage.exists()) {
            notifyImageCacheChanged(message.messageId)
            fullImage
        } else null
    }
}

private fun parseImageUrls(message: Message): Pair<String, String> {
    return try {
        val obj = org.json.JSONObject(message.extra ?: "{}")
        obj.optString("thumbnailUrl", "") to obj.optString("imageUrl", "")
    } catch (_: Exception) {
        "" to ""
    }
}

private fun parseImageObjectKeys(message: Message): Pair<String, String> {
    return try {
        val obj = org.json.JSONObject(message.extra ?: "{}")
        obj.optString("thumbnailObjectKey", "") to obj.optString("objectKey", "")
    } catch (_: Exception) {
        "" to ""
    }
}

fun downloadImage(target: File, imageUrl: String, objectKey: String = ""): Boolean {
    return PictureCacheManager.downloadToFileBlocking(target, imageUrl, objectKey)
}

fun saveImageToGallery(context: Context, imageFile: File): Boolean {
    return try {
        val fileName = "LightChat_${System.currentTimeMillis()}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LightChat")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    imageFile.inputStream().use { inp -> inp.copyTo(out) }
                }
            }
            uri != null
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val target = File(dir, "LightChat/$fileName")
            target.parentFile?.mkdirs()
            imageFile.copyTo(target, overwrite = true)
            MediaStore.Images.Media.insertImage(
                context.contentResolver, target.absolutePath, fileName, null
            )
            true
        }
    } catch (_: Exception) {
        false
    }
}

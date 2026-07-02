package com.lightchat.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.lightchat.LightChatApplication
import com.lightchat.core.network.NetworkClients
import com.lightchat.data.remote.AuthApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

object PictureCacheManager {

    private val httpClient = NetworkClients.image

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(4 * 1024 * 1024)
    private val memoryCache = object : LruCache<String, Bitmap>(maxMemory) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount
    }

    fun picturesDir(context: Context): File {
        val dir = File(context.filesDir, "pictures")
        if (!dir.exists()) {
            val oldDir = File(context.filesDir, "images")
            if (oldDir.exists()) {
                try { oldDir.copyRecursively(dir, overwrite = true) } catch (_: Exception) {}
            }
        }
        return dir.apply { mkdirs() }
    }

    private fun cacheKey(objectKey: String, url: String): String? {
        if (objectKey.isNotBlank()) return sha1(objectKey)
        if (url.isNotBlank()) return sha1(url)
        return null
    }

    private fun sha1(input: String): String {
        return MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun originalFile(context: Context, objectKey: String, imageUrl: String): File? {
        val key = cacheKey(objectKey, imageUrl) ?: return null
        return File(picturesDir(context), "${key}.jpg")
    }

    fun thumbnailFile(context: Context, thumbnailObjectKey: String, thumbnailUrl: String): File? {
        val key = cacheKey(thumbnailObjectKey, thumbnailUrl) ?: return null
        return File(picturesDir(context), "${key}_thumb.jpg")
    }

    fun getCachedBitmap(context: Context, objectKey: String, url: String, isThumb: Boolean): Bitmap? {
        val key = cacheKey(objectKey, url) ?: return null
        val memKey = if (isThumb) "thumb_$key" else key
        memoryCache.get(memKey)?.let { return it }

        val file = if (isThumb) {
            File(picturesDir(context), "${key}_thumb.jpg")
        } else {
            File(picturesDir(context), "${key}.jpg")
        }
        if (file.exists() && file.length() > 0) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                memoryCache.put(memKey, bitmap)
                return bitmap
            }
        }
        return null
    }

    data class DownloadResult(val success: Boolean, val refreshedUrl: String?)

    suspend fun downloadToFile(
        target: File,
        imageUrl: String,
        objectKey: String,
        messageId: String = "",
        urlField: String = "",
        onUrlRefreshed: ((String) -> Unit)? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (tryDownload(target, imageUrl)) return@withContext DownloadResult(true, null)
        val freshUrl = refreshImageUrl(imageUrl, objectKey, messageId, urlField)
        if (freshUrl != null && freshUrl != imageUrl) {
            onUrlRefreshed?.invoke(freshUrl)
            if (tryDownload(target, freshUrl)) return@withContext DownloadResult(true, freshUrl)
        }
        DownloadResult(false, null)
    }

    private fun tryDownload(target: File, imageUrl: String): Boolean {
        return try {
            target.parentFile?.mkdirs()
            val resolvedUrl = if (imageUrl.startsWith("http")) imageUrl
                else "${AuthApiClient.DEFAULT_BASE_URL}$imageUrl"
            val request = Request.Builder().url(resolvedUrl).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val bytes = response.body?.bytes() ?: return false
                target.writeBytes(bytes)
            }
            true
        } catch (_: Exception) { false }
    }

    fun downloadToFileBlocking(target: File, imageUrl: String, objectKey: String = ""): Boolean {
        if (tryDownload(target, imageUrl)) return true
        val freshUrl = refreshImageUrl(imageUrl, objectKey)
        return freshUrl != null && freshUrl != imageUrl && tryDownload(target, freshUrl)
    }

    private fun refreshImageUrl(url: String, objectKey: String, messageId: String = "", urlField: String = ""): String? {
        return try {
            val app = LightChatApplication.instance
            val token = app.tokenManager.getToken() ?: return null
            AuthApiClient().refreshImageUrl(url, objectKey, token, messageId, urlField)
        } catch (_: Exception) { null }
    }

    fun putMemoryCache(objectKey: String, url: String, bitmap: Bitmap, isThumb: Boolean) {
        val key = cacheKey(objectKey, url) ?: return
        val memKey = if (isThumb) "thumb_$key" else key
        memoryCache.put(memKey, bitmap)
    }
}

package com.lightchat.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.lightchat.data.remote.AuthApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class AvatarResult(
    val bitmap: Bitmap?,
    val colorHex: String?,
    val isLoading: Boolean
)

object AvatarCacheLoader {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val memoryCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            return size > 40
        }
    }

    private val downloadMutex = Mutex()
    private val downloading = mutableSetOf<String>()

    fun avatarDir(context: Context): File {
        val dir = File(context.filesDir, "avatars")
        // Migrate from old cache dir if filesDir is empty
        val oldDir = File(context.cacheDir, "avatars")
        if (!dir.exists() && oldDir.exists()) {
            try { oldDir.copyRecursively(dir, overwrite = true) } catch (_: Exception) {}
        }
        return dir.apply { mkdirs() }
    }

    private fun cacheFile(context: Context, userId: String): File {
        return File(avatarDir(context), "${userId}.jpg")
    }

    private fun versionFile(context: Context, userId: String): File {
        return File(avatarDir(context), "${userId}.version")
    }

    fun getCachedBitmap(context: Context, userId: String, avatarVersion: Int): Bitmap? {
        val memKey = "${userId}_$avatarVersion"
        memoryCache[memKey]?.let { return it }

        val cache = cacheFile(context, userId)
        val verFile = versionFile(context, userId)
        val cachedVersion = if (verFile.exists()) verFile.readText().trim().toIntOrNull() ?: 0 else 0
        if (cachedVersion == avatarVersion && cache.exists() && cache.length() > 0) {
            val bitmap = BitmapFactory.decodeFile(cache.absolutePath)
            if (bitmap != null) {
                memoryCache[memKey] = bitmap
                return bitmap
            }
        }
        return null
    }

    suspend fun loadAvatar(
        context: Context,
        userId: String,
        avatarUrl: String,
        avatarVersion: Int,
        avatarFallback: String,
        allowNetwork: Boolean = true
    ): AvatarResult = withContext(Dispatchers.IO) {
        // Hex color — no bitmap needed
        if (avatarFallback.startsWith("#")) {
            return@withContext AvatarResult(null, avatarFallback, false)
        }

        // App icon marker — load from local resources
        if (avatarFallback == "lightchat://app-icon") {
            val icon = loadAppIconBitmap(context)
            return@withContext AvatarResult(icon, null, false)
        }

        // No URL, no bitmap
        if (avatarUrl.isBlank()) {
            return@withContext AvatarResult(null, null, false)
        }

        val memKey = "${userId}_$avatarVersion"
        memoryCache[memKey]?.let { return@withContext AvatarResult(it, null, false) }

        val cache = cacheFile(context, userId)
        val verFile = versionFile(context, userId)
        val cachedVersion = if (verFile.exists()) verFile.readText().trim().toIntOrNull() ?: 0 else 0

        // Exact version match in disk cache
        if (cachedVersion == avatarVersion && cache.exists() && cache.length() > 0) {
            val bitmap = BitmapFactory.decodeFile(cache.absolutePath)
            if (bitmap != null) {
                memoryCache[memKey] = bitmap
                return@withContext AvatarResult(bitmap, null, false)
            }
        }

        // If network not allowed, use whatever is cached (even old version)
        if (!allowNetwork) {
            if (cache.exists() && cache.length() > 0) {
                val oldBitmap = BitmapFactory.decodeFile(cache.absolutePath)
                if (oldBitmap != null) {
                    memoryCache[memKey] = oldBitmap
                    return@withContext AvatarResult(oldBitmap, null, false)
                }
            }
            // Cache is empty — fall through to download to populate it
        }

        val result = if (isNetworkAvailable(context)) {
            downloadAvatar(context, userId, avatarUrl, avatarVersion)
        } else {
            null
        }
        if (result != null) {
            memoryCache[memKey] = result
            AvatarResult(result, null, false)
        } else {
            // Return cached old version as fallback, or null
            if (cache.exists() && cache.length() > 0) {
                val oldBitmap = BitmapFactory.decodeFile(cache.absolutePath)
                AvatarResult(oldBitmap, null, false)
            } else {
                AvatarResult(null, null, false)
            }
        }
    }

    private fun downloadAvatar(context: Context, userId: String, url: String, version: Int): Bitmap? {
        val bytes = downloadBytes(url) ?: run {
            val freshUrl = refreshExpiredUrl(url, "")
            if (freshUrl != null && freshUrl != url) downloadBytes(freshUrl) else null
        } ?: return null

        val cache = cacheFile(context, userId)
        cache.parentFile?.mkdirs()
        cache.writeBytes(bytes)

        val verFile = versionFile(context, userId)
        verFile.writeText(version.toString())

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun downloadBytes(url: String): ByteArray? {
        return try {
            val resolvedUrl = if (url.startsWith("http")) url else "${AuthApiClient.DEFAULT_BASE_URL}$url"
            val request = Request.Builder().url(resolvedUrl).get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            response.body?.bytes()
        } catch (_: Exception) {
            null
        }
    }

    private fun refreshExpiredUrl(url: String, objectKey: String): String? {
        return try {
            if (!com.lightchat.LightChatApplication.isInitialized()) return null
            val app = com.lightchat.LightChatApplication.instance
            val token = app.tokenManager.getToken() ?: return null
            val apiClient = AuthApiClient()
            apiClient.refreshImageUrl(url, objectKey, token)
        } catch (_: Exception) { null }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun loadAppIconBitmap(context: Context): Bitmap? {
        return try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            (drawable as? BitmapDrawable)?.bitmap
        } catch (_: Exception) { null }
    }

    fun cleanStaleAvatars(context: Context, activeVersions: Map<String, Int>) {
        try {
            val dir = avatarDir(context)
            dir.listFiles()?.forEach { file ->
                val name = file.nameWithoutExtension
                if (file.extension == "jpg" && !name.startsWith("thumb_")) {
                    val verFile = versionFile(context, name)
                    val cachedVersion = if (verFile.exists()) verFile.readText().trim().toIntOrNull() ?: -1 else -1
                    val activeVersion = activeVersions[name]
                    if (activeVersion == null || cachedVersion != activeVersion) {
                        file.delete()
                        verFile.delete()
                    }
                }
            }
            memoryCache.clear()
        } catch (_: Exception) {}
    }
}

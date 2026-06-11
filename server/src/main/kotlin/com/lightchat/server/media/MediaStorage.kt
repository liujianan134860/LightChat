package com.lightchat.server.media

data class StoredMedia(
    val fileId: String,
    val imageUrl: String,
    val thumbnailUrl: String,
    val objectKey: String,
    val thumbnailObjectKey: String,
    val storageProvider: String
)

interface MediaStorage {
    fun storeImage(originalBytes: ByteArray, thumbnailBytes: ByteArray?): StoredMedia
    fun refreshSignedUrl(expiredUrl: String): String? = null
}

fun createMediaStorage(): MediaStorage {
    val provider = System.getenv("MEDIA_STORAGE_PROVIDER")?.lowercase()?.trim()
        ?: System.getenv("LIGHTCHAT_MEDIA_STORAGE")?.lowercase()?.trim()
        ?: "aliyun_oss"
    return when (provider) {
        "oss", "aliyun_oss", "aliyun" -> AliyunOssMediaStorage.fromEnv()
        else -> error("不支持的媒体存储类型: $provider，仅支持 aliyun_oss")
    }
}

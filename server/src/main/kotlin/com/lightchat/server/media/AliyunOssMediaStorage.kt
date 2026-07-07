package com.lightchat.server.media

import com.aliyun.oss.OSS
import com.aliyun.oss.OSSClientBuilder
import com.aliyun.oss.model.CannedAccessControlList
import com.aliyun.oss.model.ObjectMetadata
import com.aliyun.oss.model.PutObjectRequest
import java.io.ByteArrayInputStream
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID

class AliyunOssMediaStorage(
    private val client: OSS,
    private val signingClient: OSS,
    private val bucket: String,
    private val endpoint: String,
    private val keyPrefix: String,
    private val publicRead: Boolean,
    private val signedUrlSeconds: Long,
    private val publicBaseUrl: String?
) : MediaStorage {
    override fun storeImage(originalBytes: ByteArray, thumbnailBytes: ByteArray?): StoredMedia {
        val fileId = UUID.randomUUID().toString()
        val day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val originalKey = normalizeKey("$keyPrefix/images/original/$day/$fileId.jpg")
        val thumbKey = normalizeKey("$keyPrefix/images/thumb/$day/${fileId}_thumb.jpg")

        putBytes(originalKey, originalBytes)
        putBytes(thumbKey, thumbnailBytes?.takeIf { it.isNotEmpty() } ?: originalBytes)

        return StoredMedia(
            fileId = fileId,
            imageUrl = buildUrl(originalKey),
            thumbnailUrl = buildUrl(thumbKey),
            objectKey = originalKey,
            thumbnailObjectKey = thumbKey,
            storageProvider = "aliyun_oss"
        )
    }

    private fun putBytes(key: String, bytes: ByteArray) {
        val metadata = ObjectMetadata().apply {
            contentLength = bytes.size.toLong()
            contentType = "image/jpeg"
            cacheControl = if (publicRead) {
                "public, max-age=31536000"
            } else {
                "private, max-age=3600"
            }
        }
        ByteArrayInputStream(bytes).use { input ->
            client.putObject(PutObjectRequest(bucket, key, input, metadata))
            client.setObjectAcl(
                bucket,
                key,
                if (publicRead) CannedAccessControlList.PublicRead else CannedAccessControlList.Private
            )
        }
    }

    private fun buildUrl(key: String): String {
        publicBaseUrl?.takeIf { it.isNotBlank() }?.let { base ->
            return base.trimEnd('/') + "/" + key
        }
        if (publicRead) {
            val cleanEndpoint = endpoint
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('/')
            return "https://$bucket.$cleanEndpoint/$key"
        }
        val expireAt = Date(System.currentTimeMillis() + signedUrlSeconds * 1000)
        val url: URL = client.generatePresignedUrl(bucket, key, expireAt)
        return url.toString()
    }

    override fun refreshSignedUrl(expiredUrl: String): String? {
        val key = extractObjectKey(expiredUrl) ?: return null
        return generateSignedUrl(key)
    }

    fun generateSignedUrl(objectKey: String): String {
        val expireAt = Date(System.currentTimeMillis() + signedUrlSeconds * 1000)
        val url: URL = signingClient.generatePresignedUrl(bucket, objectKey, expireAt)
        return url.toString()
    }

    private fun extractObjectKey(url: String): String? {
        return try {
            val uri = java.net.URI(url)
            val path = uri.rawPath.trimStart('/')
            if (path.startsWith("$bucket/")) path.removePrefix("$bucket/") else path
        } catch (_: Exception) { null }
    }

    private fun normalizeKey(raw: String): String {
        return raw.replace("\\", "/")
            .replace(Regex("/+"), "/")
            .trim('/')
    }

    companion object {
        fun fromEnv(): AliyunOssMediaStorage {
            val accessKeyId = requireEnv("ALIYUN_OSS_ACCESS_KEY_ID")
            val accessKeySecret = requireEnv("ALIYUN_OSS_ACCESS_KEY_SECRET")
            val bucket = requireEnv("ALIYUN_OSS_BUCKET")
            val endpoint = normalizeEndpoint(requireEnv("ALIYUN_OSS_ENDPOINT"))
            val publicEndpoint = normalizeEndpoint(
                System.getenv("ALIYUN_OSS_PUBLIC_ENDPOINT")?.takeIf { it.isNotBlank() }
                    ?: derivePublicEndpoint(endpoint)
            )
            val keyPrefix = System.getenv("ALIYUN_OSS_KEY_PREFIX") ?: "lightchat"
            val publicRead = (System.getenv("ALIYUN_OSS_PUBLIC_READ") ?: "false").equals("true", ignoreCase = true)
            val signedUrlSeconds = (System.getenv("ALIYUN_OSS_SIGNED_URL_SECONDS") ?: "3600").toLong()
            val publicBaseUrl = System.getenv("ALIYUN_OSS_PUBLIC_BASE_URL")

            val client = OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret)
            val signingClient = OSSClientBuilder().build(publicEndpoint, accessKeyId, accessKeySecret)
            return AliyunOssMediaStorage(
                client = client,
                signingClient = signingClient,
                bucket = bucket,
                endpoint = endpoint,
                keyPrefix = keyPrefix,
                publicRead = publicRead,
                signedUrlSeconds = signedUrlSeconds,
                publicBaseUrl = publicBaseUrl
            )
        }

        private fun requireEnv(name: String): String {
            return System.getenv(name)?.takeIf { it.isNotBlank() }
                ?: error("$name 未配置，无法启用阿里云 OSS")
        }

        private fun normalizeEndpoint(value: String): String {
            return if (value.startsWith("http://") || value.startsWith("https://")) {
                value.trimEnd('/')
            } else {
                "https://${value.trimEnd('/')}"
            }
        }

        private fun derivePublicEndpoint(endpoint: String): String {
            return endpoint.replace("-internal.aliyuncs.com", ".aliyuncs.com")
        }
    }
}

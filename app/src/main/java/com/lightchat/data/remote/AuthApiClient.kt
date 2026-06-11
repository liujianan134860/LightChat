package com.lightchat.data.remote

import com.lightchat.model.User
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class AuthResponse(
    val token: String,
    val user: User
)

data class UploadedImage(
    val fileId: String,
    val imageUrl: String,
    val thumbnailUrl: String,
    val objectKey: String,
    val thumbnailObjectKey: String,
    val storageProvider: String
)

class AuthApiClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    fun login(username: String, password: String): AuthResponse {
        return postAuth("/api/login", JSONObject().apply {
            put("username", username)
            put("password", password)
        })
    }

    fun register(username: String, password: String, nickname: String): AuthResponse {
        return postAuth("/api/register", JSONObject().apply {
            put("username", username)
            put("password", password)
            put("nickname", nickname)
        })
    }

    fun searchUsers(query: String, token: String? = null): List<User> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val builder = Request.Builder()
            .url("$baseUrl/api/users/search?q=$encoded")
            .get()
        if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
        val request = builder.build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            val json = if (responseBody.isBlank()) JSONObject() else JSONObject(responseBody)
            if (!response.isSuccessful) {
                throw IOException(json.optString("message", "搜索用户失败: ${response.code}"))
            }
            val arr = json.getJSONArray("users")
            return (0 until arr.length())
                .map { arr.getJSONObject(it).toUser() }
                .filter { it.userId == query }
        }
    }

    fun refreshImageUrl(
        expiredUrl: String,
        objectKey: String,
        token: String,
        messageId: String = "",
        urlField: String = ""
    ): String? {
        val body = JSONObject().apply {
            if (expiredUrl.isNotBlank()) put("url", expiredUrl)
            if (objectKey.isNotBlank()) put("objectKey", objectKey)
            if (messageId.isNotBlank()) put("messageId", messageId)
            if (urlField.isNotBlank()) put("urlField", urlField)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/images/refresh-url")
            .addHeader("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                val json = if (responseBody.isBlank()) JSONObject() else JSONObject(responseBody)
                if (!response.isSuccessful) null
                else json.optString("url", "").takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) { null }
    }

    fun getUserProfile(userId: String, token: String): User {
        val encoded = URLEncoder.encode(userId, "UTF-8")
        val request = Request.Builder()
            .url("$baseUrl/api/users/profile?id=$encoded")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            val json = if (responseBody.isBlank()) JSONObject() else JSONObject(responseBody)
            if (!response.isSuccessful) {
                throw IOException(json.optString("message", "拉取用户资料失败: ${response.code}"))
            }
            return json.getJSONObject("user").toUser()
        }
    }

    fun bootstrap(token: String): JSONObject {
        val request = Request.Builder()
            .url("$baseUrl/api/bootstrap")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            val json = if (responseBody.isBlank()) JSONObject() else JSONObject(responseBody)
            if (!response.isSuccessful) {
                throw IOException(json.optString("message", "拉取账号数据失败: ${response.code}"))
            }
            return json
        }
    }

    fun uploadImage(imageBytes: ByteArray, token: String, thumbnailBytes: ByteArray? = null): UploadedImage {
        val imagePart = imageBytes.toRequestBody("image/jpeg".toMediaType())
        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "image.jpg", imagePart)
        if (thumbnailBytes != null) {
            val thumbPart = thumbnailBytes.toRequestBody("image/jpeg".toMediaType())
            bodyBuilder.addFormDataPart("thumbnail", "thumb.jpg", thumbPart)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/images/upload")
            .addHeader("Authorization", "Bearer $token")
            .post(bodyBuilder.build())
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            val json = if (responseBody.isBlank()) JSONObject() else JSONObject(responseBody)
            if (!response.isSuccessful) {
                throw IOException(json.optString("message", "上传图片失败: ${response.code}"))
            }
            fun absoluteUrl(value: String): String {
                return if (value.startsWith("http")) value else "$baseUrl$value"
            }
            val imageUrl = json.optString("imageUrl", "")
            if (imageUrl.isEmpty()) {
                throw IOException("服务端返回缺少 imageUrl 字段")
            }
            return UploadedImage(
                fileId = json.optString("fileId", ""),
                imageUrl = absoluteUrl(imageUrl),
                thumbnailUrl = absoluteUrl(json.optString("thumbnailUrl", imageUrl)),
                objectKey = json.optString("objectKey", ""),
                thumbnailObjectKey = json.optString("thumbnailObjectKey", ""),
                storageProvider = json.optString("storageProvider", "")
            )
        }
    }

    private fun postAuth(path: String, body: JSONObject): AuthResponse {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(body.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            val json = if (responseBody.isBlank()) JSONObject() else JSONObject(responseBody)
            if (!response.isSuccessful) {
                throw IOException(json.optString("message", "服务端请求失败: ${response.code}"))
            }
            return AuthResponse(
                token = json.getString("token"),
                user = json.getJSONObject("user").toUser()
            )
        }
    }

    private fun JSONObject.toUser(): User = User(
        userId = getString("userId"),
        nickname = optString("nickname", getString("userId")),
        avatar = optString("avatar", ""),
        avatarUrl = optString("avatarUrl", ""),
        avatarVersion = optInt("avatarVersion", 0),
        signature = optString("signature", ""),
        region = optString("region", "")
    )

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        const val DEFAULT_BASE_URL = "http://10.129.97.70:8081"
    }
}

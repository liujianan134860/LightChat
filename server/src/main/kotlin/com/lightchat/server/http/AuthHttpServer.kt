package com.lightchat.server.http

import com.lightchat.server.model.ServerUser
import com.lightchat.server.media.createMediaStorage
import com.lightchat.server.security.JwtClaims
import com.lightchat.server.security.JwtService
import com.lightchat.server.push.MockVendorPushGateway
import com.lightchat.server.store.DataStore
import com.lightchat.server.store.EventService
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

import java.util.concurrent.Executors

class AuthHttpServer(
    private val port: Int,
    private val dataStore: DataStore,
    private val jwtService: JwtService,
    private val pushGateway: MockVendorPushGateway,
    private val eventService: EventService
) {
    private val server: HttpServer = HttpServer.create(InetSocketAddress(port), 0)
    private val mediaStorage = createMediaStorage()
    private val mockPushKey = System.getenv("MOCK_PUSH_KEY") ?: "lightchat-local-mock"

    fun start() {
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/api/register") { exchange -> handleRegister(exchange) }
        server.createContext("/api/login") { exchange -> handleLogin(exchange) }
        server.createContext("/api/users/search") { exchange -> handleUserSearch(exchange) }
        server.createContext("/api/users/profile") { exchange -> handleUserProfile(exchange) }
        server.createContext("/api/bootstrap") { exchange -> handleBootstrap(exchange) }
        server.createContext("/api/images/upload") { exchange -> handleImageUpload(exchange) }
        server.createContext("/api/images/refresh-url") { exchange -> handleRefreshImageUrl(exchange) }
        server.createContext("/api/mock-push/pending") { exchange -> handleMockPushPending(exchange) }
        server.start()
        println("[START] LightChat HTTP API listening on port $port")
    }

    fun stop(delaySeconds: Int = 0) {
        server.stop(delaySeconds)
    }

    private fun handleRegister(exchange: HttpExchange) {
        if (!requirePost(exchange)) return
        try {
            val body = readJson(exchange)
            val username = body.optString("username").trim()
            val password = body.optString("password")
            val nickname = body.optString("nickname").trim().ifBlank { username }
            if (username.isBlank() || password.isBlank()) {
                writeError(exchange, 400, "用户名和密码不能为空")
                return
            }

            dataStore.registerUser(username, password, nickname).fold(
                onSuccess = { writeAuth(exchange, it, exchange) },
                onFailure = { writeError(exchange, 409, it.message ?: "用户已存在") }
            )
        } catch (e: Exception) {
            writeError(exchange, 400, "Invalid JSON: ${e.message}")
        }
    }

    private fun handleLogin(exchange: HttpExchange) {
        if (!requirePost(exchange)) return
        try {
            val body = readJson(exchange)
            val username = body.optString("username").trim()
            val password = body.optString("password")
            if (username.isBlank() || password.isBlank()) {
                writeError(exchange, 400, "用户名和密码不能为空")
                return
            }

            dataStore.loginUser(username, password).fold(
                onSuccess = { writeAuth(exchange, it, exchange) },
                onFailure = {
                    val code = if (it is NoSuchElementException) 404 else 401
                    writeError(exchange, code, it.message ?: "登录失败")
                }
            )
        } catch (e: Exception) {
            writeError(exchange, 400, "Invalid JSON: ${e.message}")
        }
    }

    private fun handleUserSearch(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            writeError(exchange, 405, "Method Not Allowed")
            return
        }
        if (authenticate(exchange) == null) return
        val query = parseQuery(exchange.requestURI.rawQuery)["q"].orEmpty()
        val users = dataStore.searchUsers(query)
        val response = JSONObject().apply {
            put("users", org.json.JSONArray().apply {
                users.forEach { put(it.toJson()) }
            })
        }
        writeJson(exchange, 200, response)
    }

    private fun handleUserProfile(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            writeError(exchange, 405, "Method Not Allowed")
            return
        }
        if (authenticate(exchange) == null) return
        val userId = parseQuery(exchange.requestURI.rawQuery)["id"].orEmpty()
        if (userId.isBlank()) {
            writeError(exchange, 400, "用户ID不能为空")
            return
        }
        val user = dataStore.getUser(userId)
        if (user == null) {
            writeError(exchange, 404, "用户不存在")
            return
        }
        writeJson(exchange, 200, JSONObject().put("user", user.toJson()))
    }

    private fun handleBootstrap(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            writeError(exchange, 405, "Method Not Allowed")
            return
        }
        val claims = authenticate(exchange) ?: return
        val userId = claims.userId
        if (userId.isBlank() || !dataStore.userExists(userId)) {
            writeError(exchange, 404, "用户不存在")
            return
        }
        val json = dataStore.buildBootstrapJson(userId)
        val maxUserSeq = eventService.getLatestUserSeq(userId)
        json.put("maxUserSeq", maxUserSeq)
        println(
            "[BOOTSTRAP] user=$userId users=${json.optJSONArray("users")?.length() ?: 0} " +
                "friends=${json.optJSONArray("friends")?.length() ?: 0} " +
                "groups=${json.optJSONArray("groups")?.length() ?: 0} " +
                "conversations=${json.optJSONArray("conversations")?.length() ?: 0} " +
                "messages=${json.optJSONArray("messages")?.length() ?: 0} " +
                "settings=${json.optJSONObject("conversationSettings")?.length() ?: 0} " +
                "maxUserSeq=$maxUserSeq"
        )
        writeJson(exchange, 200, json)
    }

    private fun handleImageUpload(exchange: HttpExchange) {
        if (!requirePost(exchange)) return
        if (authenticate(exchange) == null) return
        try {
            val contentType = exchange.requestHeaders.getFirst("Content-Type") ?: ""
            val boundary = contentType.substringAfter("boundary=", "").takeIf { it.isNotBlank() }
                ?: run { writeError(exchange, 400, "缺少 multipart boundary"); return }

            val rawBytes = exchange.requestBody.readBytes()
            val parts = parseMultipart(rawBytes, boundary)

            val imageBytes = parts["image"] ?: run { writeError(exchange, 400, "缺少 image 字段"); return }
            if (imageBytes.size > MAX_IMAGE_BYTES) {
                writeError(exchange, 413, "图片不能超过 ${MAX_IMAGE_BYTES / 1024 / 1024}MB")
                return
            }
            val thumbnailBytes = parts["thumbnail"]
            val stored = mediaStorage.storeImage(imageBytes, thumbnailBytes)
            writeJson(exchange, 200, JSONObject().apply {
                put("fileId", stored.fileId)
                put("imageUrl", stored.imageUrl)
                put("thumbnailUrl", stored.thumbnailUrl)
                put("objectKey", stored.objectKey)
                put("thumbnailObjectKey", stored.thumbnailObjectKey)
                put("storageProvider", stored.storageProvider)
            })
        } catch (e: Exception) {
            writeError(exchange, 400, "图片上传失败: ${e.message}")
        }
    }

    private fun parseMultipart(body: ByteArray, boundary: String): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        val delimiter = "--$boundary".toByteArray(Charsets.UTF_8)
        val endDelimiter = "--$boundary--".toByteArray(Charsets.UTF_8)
        val crlfcrlf = byteArrayOf(13, 10, 13, 10) // \r\n\r\n
        val crlf = byteArrayOf(13, 10)

        var pos = 0
        while (pos < body.size) {
            val nextDelim = indexOfBytes(body, delimiter, pos) ?: break
            pos = nextDelim + delimiter.size
            if (pos >= body.size) break
            if (body[pos] == 13.toByte()) pos += 2 // skip \r\n after delimiter
            if (pos + endDelimiter.size - delimiter.size <= body.size &&
                body.copyOfRange(pos, pos + (endDelimiter.size - delimiter.size)).contentEquals(byteArrayOf(45, 45))) break // "--" marks end

            val headerEnd = indexOfBytes(body, crlfcrlf, pos) ?: break
            val headerText = String(body, pos, headerEnd - pos, Charsets.UTF_8)
            val name = Regex("name=\"([^\"]+)\"").find(headerText)?.groupValues?.get(1) ?: "unknown"
            pos = headerEnd + 4

            val nextPart = indexOfBytes(body, crlf, pos)?.let { crlfPos ->
                // Check if what follows is a boundary
                val afterCrlf = body.copyOfRange(crlfPos + 2, minOf(crlfPos + 2 + delimiter.size, body.size))
                if (afterCrlf.contentEquals(delimiter)) crlfPos else null
            }

            val dataEnd = if (nextPart != null) nextPart else body.size
            // Trim trailing \r\n before boundary
            var actualEnd = dataEnd
            if (actualEnd >= 2 && body[actualEnd - 2] == 13.toByte() && body[actualEnd - 1] == 10.toByte()) {
                actualEnd -= 2
            }
            val data = body.copyOfRange(pos, actualEnd)
            result[name] = data
            pos = dataEnd
        }
        return result
    }

    private fun indexOfBytes(haystack: ByteArray, needle: ByteArray, start: Int): Int? {
        outer@ for (i in start..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return null
    }

    private fun handleRefreshImageUrl(exchange: HttpExchange) {
        if (!requirePost(exchange)) return
        if (authenticate(exchange) == null) return
        try {
            val body = readJson(exchange)
            val url = body.optString("url", "").takeIf { it.isNotBlank() }
            val objectKey = body.optString("objectKey", "").takeIf { it.isNotBlank() }
            val messageId = body.optString("messageId", "").takeIf { it.isNotBlank() }
            val urlField = body.optString("urlField", "").takeIf {
                it == "imageUrl" || it == "thumbnailUrl"
            }

            val newUrl: String = when {
                url != null -> {
                    mediaStorage.refreshSignedUrl(url)
                        ?: run { writeError(exchange, 400, "无法从过期URL中提取object key，请同时提供objectKey字段"); return }
                }
                objectKey != null && mediaStorage is com.lightchat.server.media.AliyunOssMediaStorage -> {
                    mediaStorage.generateSignedUrl(objectKey)
                }
                else -> {
                    writeError(exchange, 400, "请提供 url 或 objectKey 字段"); return
                }
            }

            if (messageId != null && urlField != null) {
                updateMessageImageUrl(messageId, urlField, newUrl)
            }

            writeJson(exchange, 200, JSONObject().apply {
                put("url", newUrl)
            })
        } catch (e: Exception) {
            writeError(exchange, 500, "刷新图片URL失败: ${e.message}")
        }
    }

    private fun updateMessageImageUrl(messageId: String, urlField: String, newUrl: String) {
        val message = dataStore.getMessage(messageId) ?: return
        val extra = try {
            JSONObject(message.extra ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        extra.put(urlField, newUrl)
        dataStore.updateMessageExtra(messageId, extra.toString())
    }

    private fun handleMockPushPending(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            writeError(exchange, 405, "Method Not Allowed")
            return
        }
        if (exchange.requestHeaders.getFirst("X-Mock-Push-Key") != mockPushKey) {
            writeError(exchange, 403, "Mock push key 无效")
            return
        }
        val userId = parseQuery(exchange.requestURI.rawQuery)["userId"].orEmpty()
        if (userId.isBlank()) {
            writeError(exchange, 400, "userId 不能为空")
            return
        }
        writeJson(exchange, 200, JSONObject().apply {
            put("userId", userId)
            put("pushes", pushGateway.drain(userId))
        })
    }

    private fun requirePost(exchange: HttpExchange): Boolean {
        if (exchange.requestMethod.equals("POST", ignoreCase = true)) return true
        writeError(exchange, 405, "Method Not Allowed")
        return false
    }

    private fun readJson(exchange: HttpExchange): JSONObject {
        val body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
        return JSONObject(body)
    }

    private fun writeAuth(exchange: HttpExchange, user: ServerUser, request: HttpExchange) {
        val (token, claims) = jwtService.issue(user.userId)
        dataStore.saveAuthSession(
            claims,
            deviceName = request.requestHeaders.getFirst("X-Device-Name").orEmpty(),
            clientIp = request.remoteAddress?.address?.hostAddress.orEmpty()
        )
        val response = JSONObject().apply {
            put("token", token)
            put("expiresAt", claims.expiresAt)
            put("user", user.toJson())
        }
        writeJson(exchange, 200, response)
    }

    private fun authenticate(exchange: HttpExchange): JwtClaims? {
        val token = bearerToken(exchange)
        if (token.isNullOrBlank()) {
            writeError(exchange, 401, "缺少 Authorization Bearer Token")
            return null
        }
        val claims = jwtService.verify(token).getOrElse {
            writeError(exchange, 401, it.message ?: "Token 无效")
            return null
        }
        if (!dataStore.isAuthSessionActive(claims.tokenId, claims.userId)) {
            writeError(exchange, 401, "登录会话已失效，请重新登录")
            return null
        }
        dataStore.touchAuthSession(claims.tokenId)
        return claims
    }

    private fun bearerToken(exchange: HttpExchange): String? {
        val header = exchange.requestHeaders.getFirst("Authorization") ?: return null
        return header.removePrefix("Bearer").trim().ifBlank { null }
    }

    private fun writeError(exchange: HttpExchange, status: Int, message: String) {
        writeJson(exchange, status, JSONObject().put("message", message))
    }

    private fun writeJson(exchange: HttpExchange, status: Int, body: JSONObject) {
        val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull { part ->
            val pieces = part.split("=", limit = 2)
            if (pieces.isEmpty()) null else {
                val key = URLDecoder.decode(pieces[0], StandardCharsets.UTF_8)
                val value = URLDecoder.decode(pieces.getOrElse(1) { "" }, StandardCharsets.UTF_8)
                key to value
            }
        }.toMap()
    }

    private fun ServerUser.toJson(): JSONObject = JSONObject().apply {
        put("userId", userId)
        put("nickname", nickname)
        put("avatar", avatar)
        put("avatarUrl", avatarUrl)
        put("avatarVersion", avatarVersion)
        put("signature", signature)
        put("region", region)
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
    }
}

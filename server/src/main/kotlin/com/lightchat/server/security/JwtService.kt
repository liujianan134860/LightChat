package com.lightchat.server.security

import org.json.JSONObject
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class JwtClaims(
    val userId: String,
    val tokenId: String,
    val issuedAt: Long,
    val expiresAt: Long
)

class JwtService(
    private val secret: String = System.getenv("JWT_SECRET") ?: "lightchat-dev-secret-change-me",
    private val issuer: String = "LightChat",
    private val ttlSeconds: Long = (System.getenv("JWT_TTL_SECONDS")?.toLongOrNull() ?: 7 * 24 * 60 * 60)
) {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun issue(userId: String): Pair<String, JwtClaims> {
        val now = Instant.now().epochSecond
        val claims = JwtClaims(
            userId = userId,
            tokenId = UUID.randomUUID().toString(),
            issuedAt = now,
            expiresAt = now + ttlSeconds
        )
        val header = JSONObject()
            .put("alg", "HS256")
            .put("typ", "JWT")
        val payload = JSONObject()
            .put("iss", issuer)
            .put("sub", claims.userId)
            .put("jti", claims.tokenId)
            .put("iat", claims.issuedAt)
            .put("exp", claims.expiresAt)
        val signingInput = "${base64(header.toString())}.${base64(payload.toString())}"
        val signature = base64Bytes(hmac(signingInput))
        return "$signingInput.$signature" to claims
    }

    fun verify(token: String): Result<JwtClaims> = runCatching {
        val parts = token.split(".")
        require(parts.size == 3) { "JWT 格式错误" }
        val signingInput = "${parts[0]}.${parts[1]}"
        val expected = base64Bytes(hmac(signingInput))
        require(constantTimeEquals(expected, parts[2])) { "JWT 签名无效" }

        val payload = JSONObject(String(decoder.decode(parts[1]), Charsets.UTF_8))
        require(payload.optString("iss") == issuer) { "JWT 签发方无效" }
        val now = Instant.now().epochSecond
        val expiresAt = payload.getLong("exp")
        require(expiresAt > now) { "JWT 已过期" }
        JwtClaims(
            userId = payload.getString("sub"),
            tokenId = payload.getString("jti"),
            issuedAt = payload.optLong("iat", 0),
            expiresAt = expiresAt
        )
    }

    private fun base64(value: String): String = base64Bytes(value.toByteArray(Charsets.UTF_8))

    private fun base64Bytes(value: ByteArray): String = encoder.encodeToString(value)

    private fun hmac(value: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8))
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        return MessageDigestCompat.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }
}

private object MessageDigestCompat {
    fun isEqual(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }
}

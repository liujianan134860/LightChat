package com.lightchat.server.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object PasswordHasher {
    private const val PREFIX = "sha256"
    private val random = SecureRandom()

    fun hash(password: String): String {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val digest = digest(salt, password)
        return listOf(
            PREFIX,
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(digest)
        ).joinToString("$")
    }

    fun verify(password: String, stored: String?): Boolean {
        if (stored.isNullOrBlank()) return false
        if (!stored.startsWith("$PREFIX$")) return stored == password
        val parts = stored.split("$")
        if (parts.size != 3) return false
        val salt = runCatching { Base64.getDecoder().decode(parts[1]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val actual = digest(salt, password)
        return MessageDigest.isEqual(expected, actual)
    }

    fun needsUpgrade(stored: String?): Boolean = stored != null && !stored.startsWith("$PREFIX$")

    private fun digest(salt: ByteArray, password: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(password.toByteArray(Charsets.UTF_8))
        return md.digest()
    }
}

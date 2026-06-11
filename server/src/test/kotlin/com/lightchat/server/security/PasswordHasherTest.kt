package com.lightchat.server.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    @Test
    fun hashAndVerify_acceptsCorrectPasswordOnly() {
        val stored = PasswordHasher.hash("123456")

        assertTrue(PasswordHasher.verify("123456", stored))
        assertFalse(PasswordHasher.verify("bad-password", stored))
    }

    @Test
    fun needsUpgrade_detectsLegacyPlainTextPassword() {
        assertTrue(PasswordHasher.needsUpgrade("123456"))
        assertFalse(PasswordHasher.needsUpgrade(PasswordHasher.hash("123456")))
    }
}


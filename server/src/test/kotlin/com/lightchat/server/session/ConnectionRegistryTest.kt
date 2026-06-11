package com.lightchat.server.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionRegistryTest {

    @Test
    fun `register maps connection to user and online state follows connection state`() {
        val registry = ConnectionRegistry()
        val conn = FakeConnection(isOpen = true)

        val previous = registry.register(conn, "u1")

        assertNull(previous)
        assertEquals("u1", registry.getUserId(conn))
        assertSame(conn, registry.getConnection("u1"))
        assertTrue(registry.isOnline("u1"))
        assertEquals(1, registry.onlineCount())

        conn.isOpen = false
        assertFalse(registry.isOnline("u1"))
    }

    @Test
    fun `unregister removes both directions`() {
        val registry = ConnectionRegistry()
        val conn = FakeConnection()
        registry.register(conn, "u1")

        val removed = registry.unregister(conn)

        assertEquals("u1", removed)
        assertNull(registry.getUserId(conn))
        assertNull(registry.getConnection("u1"))
        assertEquals(0, registry.onlineCount())
    }

    private class FakeConnection(
        override var isOpen: Boolean = true
    ) : ClientConnection {
        override val remoteAddress: String = "test"
        override fun send(data: ByteArray) = Unit
        override fun close(code: Int, reason: String) {
            isOpen = false
        }
    }
}

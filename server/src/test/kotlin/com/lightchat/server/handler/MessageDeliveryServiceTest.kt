package com.lightchat.server.handler

import com.lightchat.server.model.ServerMessage
import com.lightchat.server.protocol.ProtocolCodec
import com.lightchat.server.push.MockVendorPushGateway
import com.lightchat.server.session.ClientConnection
import com.lightchat.server.session.ConnectionRegistry
import com.lightchat.server.store.DataStore
import com.lightchat.server.store.EventService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDeliveryServiceTest {

    @Test
    fun `offline single recipient gets inbox event and pending push`() {
        val fixture = Fixture()
        fixture.dataStore.getOrCreateUser("u1", "Alice")
        fixture.dataStore.getOrCreateUser("u2", "Bob")

        val delivered = fixture.service.deliver(
            senderId = "u1",
            message = message(receiverId = "u2")
        )

        assertEquals(listOf("u2"), delivered)
        assertEquals(1, fixture.eventService.getEventsSince("u2", 0).size)
        assertEquals(1, fixture.pushGateway.pendingCount("u2"))
        assertEquals(setOf("u1", "u2"), fixture.dataStore.getParticipants("single_u1_u2"))
    }

    @Test
    fun `online recipient is notified without pending push`() {
        val fixture = Fixture()
        fixture.dataStore.getOrCreateUser("u1", "Alice")
        fixture.dataStore.getOrCreateUser("u2", "Bob")
        val conn = RecordingConnection()
        fixture.registry.register(conn, "u2")

        fixture.service.deliver(
            senderId = "u1",
            message = message(receiverId = "u2")
        )

        assertEquals(1, conn.sent.size)
        assertEquals(0, fixture.pushGateway.pendingCount("u2"))
    }

    @Test
    fun `group recipients come from group members and exclude sender in result`() {
        val fixture = Fixture()
        listOf("u1", "u2", "u3").forEach { fixture.dataStore.getOrCreateUser(it, it) }
        fixture.dataStore.createGroup("g1", "Group", "u1", listOf("u2", "u3"))

        val delivered = fixture.service.deliver(
            senderId = "u1",
            message = message(conversationId = "group_g1", receiverId = null, groupId = "g1")
        )

        assertEquals(setOf("u2", "u3"), delivered.toSet())
        assertTrue(fixture.eventService.getEventsSince("u2", 0).isNotEmpty())
        assertTrue(fixture.eventService.getEventsSince("u3", 0).isNotEmpty())
        assertEquals(0, fixture.eventService.getEventsSince("u1", 0).size)
    }

    private class Fixture {
        val dataStore = DataStore()
        val eventService = EventService(dataStore)
        val registry = ConnectionRegistry()
        val codec = ProtocolCodec()
        val pushGateway = MockVendorPushGateway(dataStore)
        val service = MessageDeliveryService(dataStore, eventService, registry, codec, pushGateway)
    }

    private class RecordingConnection : ClientConnection {
        override val isOpen: Boolean = true
        override val remoteAddress: String = "test"
        val sent = mutableListOf<ByteArray>()
        override fun send(data: ByteArray) {
            sent.add(data)
        }
        override fun close(code: Int, reason: String) = Unit
    }

    private fun message(
        conversationId: String = "single_u1_u2",
        receiverId: String? = "u2",
        groupId: String? = null
    ) = ServerMessage(
        messageId = "m1",
        conversationId = conversationId,
        senderId = "u1",
        receiverId = receiverId,
        groupId = groupId,
        messageType = 0,
        content = "hello",
        clientSeq = 1,
        conversationSeq = 1,
        sendTime = 100
    )
}

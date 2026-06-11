package com.lightchat.protocol

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCodecTest {
    private val codec = ProtocolCodec()

    @Test
    fun encodeDecode_roundTripsPacket() {
        val body = """{"hello":"world"}""".toByteArray()
        val encoded = codec.encode(Packet(cmd = Cmd.SEND_MESSAGE.toByte(), seq = 42, body = body))

        val decoded = codec.decode(encoded)

        requireNotNull(decoded)
        assertEquals(Packet.MAGIC_NUMBER, decoded.magic)
        assertEquals(Packet.VERSION, decoded.version)
        assertEquals(Cmd.SEND_MESSAGE.toByte(), decoded.cmd)
        assertEquals(42, decoded.seq)
        assertArrayEquals(body, decoded.body)
    }

    @Test
    fun decode_rejectsCorruptedCrc() {
        val encoded = codec.encode(Packet(cmd = Cmd.HEARTBEAT.toByte(), seq = 1))
        encoded[encoded.lastIndex] = (encoded.last() + 1).toByte()

        assertNull(codec.decode(encoded))
    }

    @Test
    fun encodeSendMessage_containsExpectedJsonFields() {
        val packet = codec.decode(
            codec.encodeSendMessage(
                conversationId = "single_a_b",
                messageType = 0,
                content = "hello",
                clientSeq = 7,
                messageId = "m1",
                sendTime = 1000,
                receiverId = "b",
                groupId = null,
                extra = """{"k":"v"}""",
                seq = 99
            )
        )

        requireNotNull(packet)
        val json = JSONObject(codec.getBodyAsString(packet))
        assertEquals("single_a_b", json.getString("conversationId"))
        assertEquals("hello", json.getString("content"))
        assertEquals(7, json.getLong("clientSeq"))
        assertEquals("m1", json.getString("messageId"))
        assertEquals("b", json.getString("receiverId"))
        assertTrue(json.has("extra"))
    }
}


package com.lightchat.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryPacketCodecTest {
    @Test
    fun packetRoundTripsAcrossSharedCodec() {
        val original = Packet(
            cmd = Cmd.SEND_MESSAGE.toByte(),
            seq = 42,
            body = """{"messageId":"m-1"}""".toByteArray()
        )

        val decoded = BinaryPacketCodec.decode(BinaryPacketCodec.encode(original))

        requireNotNull(decoded)
        assertEquals(original.cmd, decoded.cmd)
        assertEquals(original.seq, decoded.seq)
        assertTrue(original.body.contentEquals(decoded.body))
    }

    @Test
    fun corruptedPacketIsRejected() {
        val encoded = BinaryPacketCodec.encode(Packet(cmd = Cmd.HEARTBEAT.toByte(), seq = 1))
        encoded[3] = Cmd.ERROR.toByte()

        assertNull(BinaryPacketCodec.decode(encoded))
    }

    @Test
    fun packetWithTrailingBytesIsRejected() {
        val encoded = BinaryPacketCodec.encode(Packet(cmd = Cmd.HEARTBEAT.toByte(), seq = 1))

        assertNull(BinaryPacketCodec.decode(encoded + byteArrayOf(0)))
    }
}

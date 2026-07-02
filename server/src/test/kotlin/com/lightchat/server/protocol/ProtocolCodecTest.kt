package com.lightchat.server.protocol

import com.lightchat.protocol.Cmd
import com.lightchat.protocol.Packet
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocolCodecTest {
    private val codec = ProtocolCodec()

    @Test
    fun encodeDecode_roundTripsPacket() {
        val body = """{"latestUserSeq":10}""".toByteArray()
        val encoded = codec.encode(Packet(cmd = Cmd.SYNC_RESULT.toByte(), seq = 5, body = body))

        val decoded = codec.decode(encoded)

        requireNotNull(decoded)
        assertEquals(Packet.MAGIC_NUMBER, decoded.magic)
        assertEquals(Packet.VERSION, decoded.version)
        assertEquals(Cmd.SYNC_RESULT.toByte(), decoded.cmd)
        assertEquals(5, decoded.seq)
        assertArrayEquals(body, decoded.body)
    }

    @Test
    fun decode_rejectsInvalidMagic() {
        val encoded = codec.encode(Packet(cmd = Cmd.HEARTBEAT_ACK.toByte(), seq = 1))
        encoded[0] = 0

        assertNull(codec.decode(encoded))
    }
}

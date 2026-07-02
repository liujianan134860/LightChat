package com.lightchat.protocol

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32

object BinaryPacketCodec {
    fun encode(packet: Packet): ByteArray {
        val header = ByteBuffer.allocate(Packet.HEADER_SIZE)
            .putShort(packet.magic)
            .put(packet.version)
            .put(packet.cmd)
            .putLong(packet.seq)
            .putInt(packet.body.size)
            .array()

        val payload = ByteArrayOutputStream().apply {
            write(header)
            write(packet.body)
        }.toByteArray()

        val crc = CRC32().apply { update(payload) }.value.toInt()
        return ByteArrayOutputStream().apply {
            write(payload)
            write(ByteBuffer.allocate(Packet.CRC_SIZE).putInt(crc).array())
        }.toByteArray()
    }

    fun decode(data: ByteArray): Packet? {
        if (data.size < Packet.MIN_PACKET_SIZE) return null
        val buffer = ByteBuffer.wrap(data)
        val magic = buffer.getShort(0)
        if (magic != Packet.MAGIC_NUMBER) return null

        val version = buffer.get(2)
        if (version != Packet.VERSION) return null

        val bodyLength = buffer.getInt(12)
        if (bodyLength < 0) return null
        val expectedSize = Packet.HEADER_SIZE.toLong() + bodyLength + Packet.CRC_SIZE
        if (expectedSize != data.size.toLong()) return null

        val receivedCrc = ByteBuffer.wrap(
            data,
            Packet.HEADER_SIZE + bodyLength,
            Packet.CRC_SIZE
        ).int
        val computedCrc = CRC32().apply {
            update(data, 0, Packet.HEADER_SIZE + bodyLength)
        }.value.toInt()
        if (receivedCrc != computedCrc) return null

        val body = data.copyOfRange(Packet.HEADER_SIZE, Packet.HEADER_SIZE + bodyLength)
        return Packet(
            magic = magic,
            version = version,
            cmd = buffer.get(3),
            seq = buffer.getLong(4),
            body = body,
            crc = receivedCrc
        )
    }
}

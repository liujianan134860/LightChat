package com.lightchat.server.protocol

import java.nio.ByteBuffer
import java.util.zip.CRC32

data class Packet(
    val magic: Short = MAGIC_NUMBER,
    val version: Byte = VERSION,
    val cmd: Byte,
    val seq: Long,
    val body: ByteArray = ByteArray(0),
    val crc: Int = 0
) {
    val bodyLength: Int get() = body.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Packet) return false
        return magic == other.magic && version == other.version &&
            cmd == other.cmd && seq == other.seq &&
            body.contentEquals(other.body) && crc == other.crc
    }

    override fun hashCode(): Int {
        var result = magic.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + cmd.hashCode()
        result = 31 * result + seq.hashCode()
        result = 31 * result + body.contentHashCode()
        result = 31 * result + crc
        return result
    }

    companion object {
        const val MAGIC_NUMBER: Short = 0x4C43
        const val VERSION: Byte = 1
        const val HEADER_SIZE: Int = 16
        const val CRC_SIZE: Int = 4
        const val MIN_PACKET_SIZE: Int = HEADER_SIZE + CRC_SIZE
    }
}

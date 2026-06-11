package com.lightchat.server.protocol

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32

class ProtocolCodec {

    private val crc32 = CRC32()

    fun encode(packet: Packet): ByteArray {
        val headerBuffer = ByteBuffer.allocate(Packet.HEADER_SIZE)
        headerBuffer.putShort(packet.magic)
        headerBuffer.put(packet.version)
        headerBuffer.put(packet.cmd)
        headerBuffer.putLong(packet.seq)
        headerBuffer.putInt(packet.body.size)

        val headerBytes = headerBuffer.array()
        val output = ByteArrayOutputStream()
        output.write(headerBytes)
        output.write(packet.body)

        val headerAndBody = output.toByteArray()
        crc32.reset()
        crc32.update(headerAndBody)
        val crcValue = crc32.value.toInt()
        output.write(ByteBuffer.allocate(4).putInt(crcValue).array())

        return output.toByteArray()
    }

    fun decode(data: ByteArray): Packet? {
        if (data.size < Packet.MIN_PACKET_SIZE) return null

        val buffer = ByteBuffer.wrap(data)
        val magic = buffer.getShort(0)
        if (magic != Packet.MAGIC_NUMBER) return null

        val version = buffer.get(2)
        val cmd = buffer.get(3)
        val seq = buffer.getLong(4)
        val bodyLen = buffer.getInt(12)

        val totalExpected = Packet.HEADER_SIZE + bodyLen + Packet.CRC_SIZE
        if (data.size < totalExpected) return null

        val body = ByteArray(bodyLen)
        if (bodyLen > 0) {
            System.arraycopy(data, Packet.HEADER_SIZE, body, 0, bodyLen)
        }

        val receivedCrc = ByteBuffer.wrap(data, Packet.HEADER_SIZE + bodyLen, 4).getInt()

        crc32.reset()
        crc32.update(data, 0, Packet.HEADER_SIZE + bodyLen)
        if (crc32.value.toInt() != receivedCrc) return null

        return Packet(magic = magic, version = version, cmd = cmd, seq = seq, body = body, crc = receivedCrc)
    }

    fun getBodyAsString(packet: Packet): String = String(packet.body, Charsets.UTF_8)

    fun encodeAuthAck(seq: Long): ByteArray {
        return encode(Packet(cmd = Cmd.AUTH_ACK.toByte(), seq = seq))
    }

    fun encodeHeartbeatAck(seq: Long): ByteArray {
        return encode(Packet(cmd = Cmd.HEARTBEAT_ACK.toByte(), seq = seq))
    }

    fun encodeNewEventNotify(latestUserSeq: Long, seq: Long): ByteArray {
        val body = """{"latestUserSeq":$latestUserSeq}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.NEW_EVENT_NOTIFY.toByte(), seq = seq, body = body))
    }

    fun encodeSyncResult(syncResultJson: String, seq: Long): ByteArray {
        val body = syncResultJson.toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.SYNC_RESULT.toByte(), seq = seq, body = body))
    }

    fun encodeMessageAck(messageId: String, status: Int, seq: Long, conversationSeq: Long = 0): ByteArray {
        val body = """{"messageId":"$messageId","status":$status,"conversationSeq":$conversationSeq}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.MESSAGE_ACK.toByte(), seq = seq, body = body))
    }

    fun encodeError(code: Int, message: String, seq: Long): ByteArray {
        val body = """{"code":$code,"message":"$message"}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.ERROR.toByte(), seq = seq, body = body))
    }

    fun encodeRecallAck(messageId: String, seq: Long): ByteArray {
        val body = """{"messageId":"$messageId","status":1}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.RECALL_MESSAGE.toByte(), seq = seq, body = body))
    }

    fun encodeCreateGroupAck(groupId: String, seq: Long): ByteArray {
        val body = """{"groupId":"$groupId","status":1}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.CREATE_GROUP.toByte(), seq = seq, body = body))
    }

    fun encodeFriendRequestAck(seq: Long): ByteArray {
        val body = """{"status":1}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.SEND_FRIEND_REQUEST.toByte(), seq = seq, body = body))
    }

    fun encodeFriendAcceptAck(seq: Long): ByteArray {
        val body = """{"status":1}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.ACCEPT_FRIEND_REQUEST.toByte(), seq = seq, body = body))
    }

    fun encodeFriendRejectAck(seq: Long): ByteArray {
        val body = """{"status":1}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.REJECT_FRIEND_REQUEST.toByte(), seq = seq, body = body))
    }

    fun encodeMarkReadAck(seq: Long): ByteArray {
        val body = """{"status":1}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.MARK_READ.toByte(), seq = seq, body = body))
    }

    fun encodeReadNotify(conversationId: String, readUserId: String, lastReadSeq: Long): ByteArray {
        val body = org.json.JSONObject().apply {
            put("conversationId", conversationId)
            put("readUserId", readUserId)
            put("lastReadSeq", lastReadSeq)
        }.toString().toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.READ_NOTIFY.toByte(), seq = 0, body = body))
    }

    fun encodeUpdateProfileAck(seq: Long): ByteArray {
        val body = """{"status":1}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.UPDATE_PROFILE.toByte(), seq = seq, body = body))
    }

    fun encodeAddGroupMembersAck(groupId: String, addedCount: Int, seq: Long): ByteArray {
        val body = """{"groupId":"$groupId","addedCount":$addedCount,"status":1}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.ADD_GROUP_MEMBERS.toByte(), seq = seq, body = body))
    }

    fun encodeUpdateConversationSettingsAck(seq: Long): ByteArray {
        val body = """{"status":1}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.UPDATE_CONVERSATION_SETTINGS.toByte(), seq = seq, body = body))
    }
}

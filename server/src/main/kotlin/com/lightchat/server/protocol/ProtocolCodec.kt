package com.lightchat.server.protocol

import com.lightchat.protocol.Cmd
import com.lightchat.protocol.BinaryPacketCodec
import com.lightchat.protocol.Packet

class ProtocolCodec {

    fun encode(packet: Packet): ByteArray {
        return BinaryPacketCodec.encode(packet)
    }

    fun decode(data: ByteArray): Packet? {
        return BinaryPacketCodec.decode(data)
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

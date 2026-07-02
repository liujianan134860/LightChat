package com.lightchat.protocol

import org.json.JSONArray
import org.json.JSONObject

class ProtocolCodec {

    fun encode(packet: Packet): ByteArray {
        return BinaryPacketCodec.encode(packet)
    }

    fun decode(data: ByteArray): Packet? {
        return BinaryPacketCodec.decode(data)
    }

    fun encodeAuth(token: String, seq: Long): ByteArray {
        val body = """{"token":"$token"}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.AUTH.toByte(), seq = seq, body = body))
    }

    fun encodeHeartbeat(seq: Long): ByteArray {
        return encode(Packet(cmd = Cmd.HEARTBEAT.toByte(), seq = seq))
    }

    fun encodeSync(lastUserSeq: Long, seq: Long): ByteArray {
        val body = """{"lastUserSeq":$lastUserSeq}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.SYNC.toByte(), seq = seq, body = body))
    }

    fun encodeSendMessage(
        conversationId: String,
        messageType: Int,
        content: String,
        clientSeq: Long,
        messageId: String,
        sendTime: Long,
        receiverId: String?,
        groupId: String?,
        extra: String?,
        seq: Long
    ): ByteArray {
        val body = JSONObject().apply {
            put("conversationId", conversationId)
            put("messageType", messageType)
            put("content", content)
            put("clientSeq", clientSeq)
            put("messageId", messageId)
            put("sendTime", sendTime)
            if (receiverId != null) put("receiverId", receiverId)
            if (groupId != null) put("groupId", groupId)
            if (extra != null) put("extra", extra)
        }.toString().toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.SEND_MESSAGE.toByte(), seq = seq, body = body))
    }

    fun encodeRecallMessage(messageId: String, conversationId: String, seq: Long): ByteArray {
        val body = """{"messageId":"$messageId","conversationId":"$conversationId"}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.RECALL_MESSAGE.toByte(), seq = seq, body = body))
    }

    fun encodeCreateGroup(groupId: String, groupName: String, memberIds: List<String>, seq: Long): ByteArray {
        val membersArr = JSONArray()
        memberIds.forEach { membersArr.put(it) }
        val body = JSONObject().apply {
            put("groupId", groupId)
            put("groupName", groupName)
            put("memberIds", membersArr)
        }.toString().toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.CREATE_GROUP.toByte(), seq = seq, body = body))
    }

    fun encodeAddGroupMembers(groupId: String, memberIds: List<String>, seq: Long): ByteArray {
        val membersArr = JSONArray()
        memberIds.forEach { membersArr.put(it) }
        val body = JSONObject().apply {
            put("groupId", groupId)
            put("memberIds", membersArr)
        }.toString().toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.ADD_GROUP_MEMBERS.toByte(), seq = seq, body = body))
    }

    fun encodeSendFriendRequest(toUserId: String, message: String, seq: Long): ByteArray {
        val body = JSONObject().apply {
            put("toUserId", toUserId)
            put("message", message)
        }.toString().toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.SEND_FRIEND_REQUEST.toByte(), seq = seq, body = body))
    }

    fun encodeAcceptFriendRequest(fromUserId: String, seq: Long): ByteArray {
        val body = """{"fromUserId":"$fromUserId"}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.ACCEPT_FRIEND_REQUEST.toByte(), seq = seq, body = body))
    }

    fun encodeRejectFriendRequest(fromUserId: String, seq: Long): ByteArray {
        val body = """{"fromUserId":"$fromUserId"}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.REJECT_FRIEND_REQUEST.toByte(), seq = seq, body = body))
    }

    fun encodeMarkRead(conversationId: String, lastReadSeq: Long, seq: Long): ByteArray {
        val body = """{"conversationId":"$conversationId","lastReadSeq":$lastReadSeq}""".toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.MARK_READ.toByte(), seq = seq, body = body))
    }

    fun encodeUpdateProfile(nickname: String?, avatar: String?, avatarUrl: String?, avatarVersion: Int?, signature: String?, region: String?, seq: Long): ByteArray {
        val body = JSONObject().apply {
            if (nickname != null) put("nickname", nickname)
            if (avatar != null) put("avatar", avatar)
            if (avatarUrl != null) put("avatarUrl", avatarUrl)
            if (avatarVersion != null) put("avatarVersion", avatarVersion)
            if (signature != null) put("signature", signature)
            if (region != null) put("region", region)
        }.toString().toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.UPDATE_PROFILE.toByte(), seq = seq, body = body))
    }

    fun encodeUpdateConversationSettings(
        conversationId: String,
        isPinned: Boolean,
        pinnedTime: Long,
        mute: Boolean,
        seq: Long
    ): ByteArray {
        val body = JSONObject().apply {
            put("conversationId", conversationId)
            put("isPinned", isPinned)
            put("pinnedTime", pinnedTime)
            put("mute", mute)
        }.toString().toByteArray(Charsets.UTF_8)
        return encode(Packet(cmd = Cmd.UPDATE_CONVERSATION_SETTINGS.toByte(), seq = seq, body = body))
    }

    fun getBodyAsString(packet: Packet): String {
        return String(packet.body, Charsets.UTF_8)
    }
}

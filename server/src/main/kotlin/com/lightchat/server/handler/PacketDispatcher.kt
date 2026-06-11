package com.lightchat.server.handler

import com.lightchat.server.model.InboxEvent
import com.lightchat.server.model.ServerMessage
import com.lightchat.server.protocol.Cmd
import com.lightchat.server.protocol.Packet
import com.lightchat.server.protocol.ProtocolCodec
import com.lightchat.server.security.JwtService
import com.lightchat.server.push.MockVendorPushGateway
import com.lightchat.server.session.ClientConnection
import com.lightchat.server.session.ConnectionRegistry
import com.lightchat.server.store.DataStore
import com.lightchat.server.store.EventService
import com.lightchat.server.store.ServerConversationSettings
import org.json.JSONObject
import java.util.UUID

class PacketDispatcher(
    private val connectionRegistry: ConnectionRegistry,
    private val dataStore: DataStore,
    private val eventService: EventService,
    private val codec: ProtocolCodec,
    private val deliveryService: MessageDeliveryService,
    private val jwtService: JwtService,
    private val pushGateway: MockVendorPushGateway
) {

    fun dispatch(conn: ClientConnection, packet: Packet) {
        when (packet.cmd.toInt()) {
            Cmd.AUTH -> handleAuth(conn, packet)
            Cmd.HEARTBEAT -> handleHeartbeat(conn, packet)
            Cmd.SEND_MESSAGE -> handleSendMessage(conn, packet)
            Cmd.SYNC -> handleSync(conn, packet)
            Cmd.RECALL_MESSAGE -> handleRecallMessage(conn, packet)
            Cmd.CREATE_GROUP -> handleCreateGroup(conn, packet)
            Cmd.ADD_GROUP_MEMBERS -> handleAddGroupMembers(conn, packet)
            Cmd.SEND_FRIEND_REQUEST -> handleSendFriendRequest(conn, packet)
            Cmd.ACCEPT_FRIEND_REQUEST -> handleAcceptFriendRequest(conn, packet)
            Cmd.REJECT_FRIEND_REQUEST -> handleRejectFriendRequest(conn, packet)
            Cmd.MARK_READ -> handleMarkRead(conn, packet)
            Cmd.UPDATE_PROFILE -> handleUpdateProfile(conn, packet)
            Cmd.UPDATE_CONVERSATION_SETTINGS -> handleUpdateConversationSettings(conn, packet)
            else -> {
                sendError(conn, -1, "Unknown cmd: ${packet.cmd}", packet.seq)
            }
        }
    }

    private fun handleAuth(conn: ClientConnection, packet: Packet) {
        try {
            val json = codec.getBodyAsString(packet)
            val obj = JSONObject(json)
            val token = obj.optString("token", "")

            if (token.isBlank()) {
                sendError(conn, 400, "Token required", packet.seq)
                return
            }

            val claims = jwtService.verify(token).getOrElse {
                sendError(conn, 401, it.message ?: "Token invalid", packet.seq)
                return
            }
            val username = claims.userId
            if (!dataStore.userExists(username)) {
                sendError(conn, 401, "User not found", packet.seq)
                return
            }
            if (!dataStore.isAuthSessionActive(claims.tokenId, username)) {
                sendError(conn, 401, "Session expired or revoked", packet.seq)
                return
            }
            dataStore.touchAuthSession(claims.tokenId)

            // Kick previous connection if same user reconnects
            val existingConn = connectionRegistry.getConnection(username)
            if (existingConn != null && existingConn != conn) {
                println("[AUTH] Kicking previous connection for '$username'")
                try { existingConn.close(4001, "Logged in elsewhere") } catch (_: Exception) {}
                connectionRegistry.unregister(existingConn)
            }

            connectionRegistry.register(conn, username)
            conn.send(codec.encodeAuthAck(packet.seq))
            println("[AUTH] User '$username' authenticated (${connectionRegistry.onlineCount()} online)")
        } catch (e: Exception) {
            sendError(conn, 400, "Invalid token format: ${e.message}", packet.seq)
        }
    }

    private fun handleHeartbeat(conn: ClientConnection, packet: Packet) {
        val userId = connectionRegistry.getUserId(conn)
        if (userId == null) {
            sendError(conn, 401, "Authentication required", packet.seq)
            return
        }
        conn.send(codec.encodeHeartbeatAck(packet.seq))
    }

    @Synchronized
    private fun handleSendMessage(conn: ClientConnection, packet: Packet) {
        val senderId = connectionRegistry.getUserId(conn)
        if (senderId == null) {
            sendError(conn, 401, "Authentication required", packet.seq)
            return
        }

        try {
            val json = codec.getBodyAsString(packet)
            val obj = JSONObject(json)
            val conversationId = obj.getString("conversationId")
            val messageType = obj.optInt("messageType", 0)
            val content = obj.optString("content", "")
            val clientSeq = obj.optLong("clientSeq", 0)
            val receiverId = obj.optString("receiverId", null)
            val groupId = obj.optString("groupId", null)

            val messageId = obj.optString("messageId", "")
                .takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString()
            val sendTime = obj.optLong("sendTime", System.currentTimeMillis())
            val extra = obj.optString("extra", null)
            val existing = dataStore.getMessage(messageId)
            if (existing != null) {
                if (existing.senderId != senderId || existing.conversationId != conversationId) {
                    sendError(conn, 409, "messageId already used by another message", packet.seq)
                    return
                }
                conn.send(codec.encodeMessageAck(messageId, 0, packet.seq, existing.conversationSeq))
                println("[MSG-DEDUPE] $senderId -> $conversationId: $messageId")
                return
            }
            val conversationSeq = eventService.nextConversationSeq(conversationId)

            val msg = ServerMessage(
                messageId = messageId,
                conversationId = conversationId,
                senderId = senderId,
                receiverId = receiverId,
                groupId = groupId,
                messageType = messageType,
                content = content,
                clientSeq = clientSeq,
                conversationSeq = conversationSeq,
                sendTime = sendTime,
                extra = extra
            )
            dataStore.saveMessage(msg)

            // ACK sent immediately after save — the message is durably stored.
            // Delivery (notifications) is best-effort and must not block the ACK.
            val ackStatus = 0 // SENT
            conn.send(codec.encodeMessageAck(messageId, ackStatus, packet.seq, conversationSeq))

            try {
                deliveryService.deliver(senderId, msg)
            } catch (e: Exception) {
                println("[MSG] ACK sent but delivery failed for $messageId: ${e.message}")
            }
            println("[MSG] $senderId -> $conversationId: $content")
        } catch (e: Exception) {
            sendError(conn, 400, "Invalid message format: ${e.message}", packet.seq)
        }
    }

    private fun handleSync(conn: ClientConnection, packet: Packet) {
        val userId = connectionRegistry.getUserId(conn)
        if (userId == null) {
            sendError(conn, 401, "Authentication required", packet.seq)
            return
        }

        try {
            val lastUserSeq = if (packet.body.isNotEmpty()) {
                val json = codec.getBodyAsString(packet)
                JSONObject(json).optLong("lastUserSeq", 0)
            } else 0L

            val allEvents = eventService.getEventsSince(userId, lastUserSeq)
            val deduped = dedupReadEvents(allEvents)

            val maxPerSync = 100
            val hasMore = deduped.size > maxPerSync
            val page = deduped.take(maxPerSync)
            val nextUserSeq = if (hasMore) page.last().userSeq else 0L

            val eventsJson = eventService.buildSyncResultJson(page, hasMore, nextUserSeq)
            conn.send(codec.encodeSyncResult(eventsJson, packet.seq))
            println("[SYNC] $userId: lastUserSeq=$lastUserSeq, total=${deduped.size}, returned=${page.size}, hasMore=$hasMore")
        } catch (e: Exception) {
            sendError(conn, 500, "Sync failed: ${e.message}", packet.seq)
        }
    }

    private fun dedupReadEvents(events: List<InboxEvent>): List<InboxEvent> {
        val result = mutableListOf<InboxEvent>()
        val readMap = LinkedHashMap<String, InboxEvent>()
        for (event in events) {
            val payload = event.payload
            if (payload.optInt("eventType", 0) == 3) {
                val convId = payload.optString("readConversationId", "")
                val readUserId = payload.optString("readUserId", "")
                val key = "$convId|$readUserId"
                val existing = readMap[key]
                if (existing == null || payload.optLong("lastReadSeq", 0) > existing.payload.optLong("lastReadSeq", 0)) {
                    readMap[key] = event
                }
            } else {
                result.add(event)
            }
        }
        result.addAll(readMap.values)
        result.sortBy { it.userSeq }
        return result
    }

    private fun getUserId(conn: ClientConnection): String? = connectionRegistry.getUserId(conn)

    private fun handleRecallMessage(conn: ClientConnection, packet: Packet) {
        val senderId = getUserId(conn) ?: run { sendError(conn, 401, "Authentication required", packet.seq); return }

        try {
            val json = codec.getBodyAsString(packet)
            val obj = JSONObject(json)
            val messageId = obj.getString("messageId")
            val conversationId = obj.getString("conversationId")

            val msg = dataStore.getMessage(messageId)
            if (msg == null) {
                sendError(conn, 404, "Message not found", packet.seq)
                return
            }
            if (msg.senderId != senderId) {
                sendError(conn, 403, "Only sender can recall", packet.seq)
                return
            }
            if (System.currentTimeMillis() - msg.sendTime > 120_000) {
                sendError(conn, 400, "Exceeded 2-minute recall window", packet.seq)
                return
            }

            val eventPayload = eventService.createMessageRecallEvent(messageId, conversationId, senderId)
            deliveryService.deliverEvent(senderId, conversationId, msg.receiverId, msg.groupId, eventPayload, 2)

            val senderName = dataStore.getUser(senderId)?.nickname ?: senderId
            pushGateway.recallMessage(messageId, senderName)

            conn.send(codec.encodeRecallAck(messageId, packet.seq))
            println("[RECALL] $senderId recalled $messageId from $conversationId")
        } catch (e: Exception) {
            sendError(conn, 400, "Invalid recall format: ${e.message}", packet.seq)
        }
    }

    private fun handleCreateGroup(conn: ClientConnection, packet: Packet) {
        val senderId = getUserId(conn) ?: run { sendError(conn, 401, "Authentication required", packet.seq); return }

        try {
            val json = codec.getBodyAsString(packet)
            val obj = JSONObject(json)
            val groupId = obj.getString("groupId")
            val groupName = obj.getString("groupName")
            val memberIdsArr = obj.getJSONArray("memberIds")
            val memberIds = (0 until memberIdsArr.length()).map { memberIdsArr.getString(it) }
            if (dataStore.getGroup(groupId) != null) {
                sendError(conn, 409, "Group already exists", packet.seq)
                return
            }
            val unknownMemberIds = memberIds.filterNot(dataStore::userExists)
            if (unknownMemberIds.isNotEmpty()) {
                sendError(conn, 404, "Unknown group members: ${unknownMemberIds.joinToString(",")}", packet.seq)
                return
            }

            val group = dataStore.createGroup(groupId, groupName, senderId, memberIds)

            val eventPayload = eventService.createGroupCreatedEvent(group)
            for (memberId in group.members) {
                val userSeq = eventService.nextUserSeq(memberId)
                val payload = JSONObject(eventPayload.toString())
                payload.put("userSeq", userSeq)
                val inboxEvent = InboxEvent(userSeq, 4, payload)
                eventService.appendEvent(memberId, inboxEvent)
                val memberConn = connectionRegistry.getConnection(memberId)
                if (memberConn != null && memberConn.isOpen) {
                    try { memberConn.send(codec.encodeNewEventNotify(userSeq, 0)) } catch (_: Exception) {}
                }
            }

            conn.send(codec.encodeCreateGroupAck(groupId, packet.seq))
            println("[GROUP] $senderId created group '$groupName' ($groupId) with ${memberIds.size} members")
        } catch (e: Exception) {
            sendError(conn, 400, "Invalid group format: ${e.message}", packet.seq)
        }
    }

    private fun handleAddGroupMembers(conn: ClientConnection, packet: Packet) {
        val senderId = getUserId(conn) ?: run { sendError(conn, 401, "Authentication required", packet.seq); return }

        try {
            val json = codec.getBodyAsString(packet)
            val obj = JSONObject(json)
            val groupId = obj.getString("groupId")
            val memberIdsArr = obj.getJSONArray("memberIds")
            val requestedMemberIds = (0 until memberIdsArr.length()).map { memberIdsArr.getString(it) }
            if (requestedMemberIds.isEmpty()) {
                sendError(conn, 400, "At least one member is required", packet.seq)
                return
            }

            val group = dataStore.getGroup(groupId)
            if (group == null) {
                sendError(conn, 404, "Group not found", packet.seq)
                return
            }
            if (senderId !in group.members) {
                sendError(conn, 403, "Only group members can invite", packet.seq)
                return
            }

            val addedMemberIds = dataStore.addGroupMembers(groupId, requestedMemberIds)
            val allMembers = dataStore.getGroupMembers(groupId)
                .map { memberId -> memberId to (dataStore.getUser(memberId)?.nickname ?: memberId) }
            val invitedMembers = addedMemberIds
                .map { memberId -> memberId to (dataStore.getUser(memberId)?.nickname ?: memberId) }
            val eventPayload = eventService.createGroupMemberJoinEvent(
                groupId = groupId,
                members = allMembers,
                invitedMembers = invitedMembers,
                inviterId = senderId
            )

            for (memberId in dataStore.getGroupMembers(groupId)) {
                val userSeq = eventService.nextUserSeq(memberId)
                val payload = JSONObject(eventPayload.toString())
                payload.put("userSeq", userSeq)
                val inboxEvent = InboxEvent(userSeq, 5, payload)
                eventService.appendEvent(memberId, inboxEvent)
                val memberConn = connectionRegistry.getConnection(memberId)
                if (memberConn != null && memberConn.isOpen) {
                    try { memberConn.send(codec.encodeNewEventNotify(userSeq, 0)) } catch (_: Exception) {}
                }
            }

            conn.send(codec.encodeAddGroupMembersAck(groupId, addedMemberIds.size, packet.seq))
            println("[GROUP] $senderId invited ${addedMemberIds.size} members to group $groupId")
        } catch (e: Exception) {
            sendError(conn, 400, "Invalid group invite format: ${e.message}", packet.seq)
        }
    }

    private fun handleSendFriendRequest(conn: ClientConnection, packet: Packet) {
        val senderId = getUserId(conn) ?: run { sendError(conn, 401, "Authentication required", packet.seq); return }

        try {
            val json = codec.getBodyAsString(packet)
            val obj = JSONObject(json)
            val toUserId = obj.getString("toUserId")
            val message = obj.optString("message", "")

            if (!dataStore.userExists(toUserId)) {
                sendError(conn, 404, "Target user not found", packet.seq)
                return
            }
            if (dataStore.areFriends(senderId, toUserId)) {
                sendError(conn, 400, "Already friends", packet.seq)
                return
            }
            if (dataStore.getPendingFriendRequest(senderId, toUserId) != null) {
                sendError(conn, 400, "Request already pending", packet.seq)
                return
            }

            val senderUser = dataStore.getUser(senderId)
            val fromNickname = senderUser?.nickname ?: senderId
            dataStore.saveFriendRequest(senderId, toUserId, message, fromNickname)

            val eventPayload = eventService.createFriendRequestEvent(senderId, toUserId, fromNickname, message)
            val userSeq = eventService.nextUserSeq(toUserId)
            val payload = JSONObject(eventPayload.toString())
            payload.put("userSeq", userSeq)
            val inboxEvent = InboxEvent(userSeq, 7, payload)
            eventService.appendEvent(toUserId, inboxEvent)
            pushGateway.enqueueFriendRequest(toUserId, fromNickname, message)
            val targetConn = connectionRegistry.getConnection(toUserId)
            if (targetConn != null && targetConn.isOpen) {
                try { targetConn.send(codec.encodeNewEventNotify(userSeq, 0)) } catch (_: Exception) {}
            }

            conn.send(codec.encodeFriendRequestAck(packet.seq))
            println("[FRIEND] $senderId sent friend request to $toUserId")
        } catch (e: Exception) {
            sendError(conn, 400, "Invalid friend request: ${e.message}", packet.seq)
        }
    }

    private fun handleAcceptFriendRequest(conn: ClientConnection, packet: Packet) {
        val userId = getUserId(conn) ?: run { sendError(conn, 401, "Authentication required", packet.seq); return }

        try {
            val json = codec.getBodyAsString(packet)
            val obj = JSONObject(json)
            val fromUserId = obj.getString("fromUserId")
            val request = dataStore.getPendingFriendRequest(fromUserId, userId)
            if (request == null) {
                sendError(conn, 404, "Friend request not found", packet.seq)
                return
            }

            dataStore.addFriendship(userId, fromUserId)
            dataStore.updateFriendRequestStatus(fromUserId, userId, 1)
            val conversationId = singleConversationId(userId, fromUserId)
            dataStore.addParticipant(conversationId, userId)
            dataStore.addParticipant(conversationId, fromUserId)

            // Notify the requester
            val eventPayload = eventService.createFriendAcceptedEvent(fromUserId, userId)
            val requesterSeq = eventService.nextUserSeq(fromUserId)
            val requesterPayload = JSONObject(eventPayload.toString())
            requesterPayload.put("userSeq", requesterSeq)
            eventService.appendEvent(fromUserId, InboxEvent(requesterSeq, 8, requesterPayload))
            val requesterConn = connectionRegistry.getConnection(fromUserId)
            if (requesterConn != null && requesterConn.isOpen) {
                try { requesterConn.send(codec.encodeNewEventNotify(requesterSeq, 0)) } catch (_: Exception) {}
            }

            // Also notify the acceptor
            val acceptorSeq = eventService.nextUserSeq(userId)
            val acceptorPayload = JSONObject(eventPayload.toString())
            acceptorPayload.put("userSeq", acceptorSeq)
            eventService.appendEvent(userId, InboxEvent(acceptorSeq, 8, acceptorPayload))
            val acceptorConn = connectionRegistry.getConnection(userId)
            if (acceptorConn != null && acceptorConn.isOpen) {
                try { acceptorConn.send(codec.encodeNewEventNotify(acceptorSeq, 0)) } catch (_: Exception) {}
            }

            val autoMessage = ServerMessage(
                messageId = UUID.randomUUID().toString(),
                conversationId = conversationId,
                senderId = userId,
                receiverId = fromUserId,
                messageType = 0,
                content = "我通过了你的朋友验证请求，现在我们可以开始聊天了",
                conversationSeq = eventService.nextConversationSeq(conversationId),
                sendTime = System.currentTimeMillis()
            )
            dataStore.saveMessage(autoMessage)
            listOf(fromUserId, userId).forEach { targetUserId ->
                val messagePayload = eventService.createNewMessageEvent(autoMessage)
                val seq = eventService.nextUserSeq(targetUserId)
                messagePayload.put("userSeq", seq)
                messagePayload.put("receiverId", targetUserId)
                eventService.appendEvent(targetUserId, InboxEvent(seq, 1, messagePayload))
                val targetConn = connectionRegistry.getConnection(targetUserId)
                if (targetConn != null && targetConn.isOpen) {
                    try { targetConn.send(codec.encodeNewEventNotify(seq, 0)) } catch (_: Exception) {}
                }
            }

            conn.send(codec.encodeFriendAcceptAck(packet.seq))
            println("[FRIEND] $userId accepted friend request from $fromUserId")
        } catch (e: Exception) {
            sendError(conn, 400, "Invalid accept format: ${e.message}", packet.seq)
        }
    }

    private fun handleRejectFriendRequest(conn: ClientConnection, packet: Packet) {
        val userId = getUserId(conn) ?: run { sendError(conn, 401, "Authentication required", packet.seq); return }

        try {
            val json = codec.getBodyAsString(packet)
            val obj = JSONObject(json)
            val fromUserId = obj.getString("fromUserId")
            val request = dataStore.getPendingFriendRequest(fromUserId, userId)
            if (request == null) {
                sendError(conn, 404, "Friend request not found", packet.seq)
                return
            }

            dataStore.updateFriendRequestStatus(fromUserId, userId, 2)
            conn.send(codec.encodeFriendRejectAck(packet.seq))
            println("[FRIEND] $userId rejected friend request from $fromUserId")
        } catch (e: Exception) {
            sendError(conn, 400, "Invalid reject format: ${e.message}", packet.seq)
        }
    }

    private fun handleMarkRead(conn: ClientConnection, packet: Packet) {
        val senderId = getUserId(conn) ?: run { sendError(conn, 401, "Authentication required", packet.seq); return }

        try {
            val json = codec.getBodyAsString(packet)
            val obj = JSONObject(json)
            val conversationId = obj.getString("conversationId")
            val lastReadSeq = obj.optLong("lastReadSeq", 0)

            val eventPayload = eventService.createMessageReadEvent(conversationId, senderId, lastReadSeq)
            deliveryService.deliverEvent(senderId, conversationId, null, null, eventPayload, 3)

            conn.send(codec.encodeMarkReadAck(packet.seq))
            println("[READ] $senderId marked $conversationId as read (seq=$lastReadSeq)")
        } catch (e: Exception) {
            sendError(conn, 400, "Invalid mark read format: ${e.message}", packet.seq)
        }
    }

    private fun handleUpdateProfile(conn: ClientConnection, packet: Packet) {
        val userId = getUserId(conn) ?: run { sendError(conn, 401, "Authentication required", packet.seq); return }

        try {
            val json = codec.getBodyAsString(packet)
            val obj = JSONObject(json)
            val nickname = obj.optString("nickname", null)
            val avatar = obj.optString("avatar", null)
            val avatarUrl = obj.optString("avatarUrl", null)
            val avatarVersion = if (obj.has("avatarVersion")) obj.getInt("avatarVersion") else null
            val signature = obj.optString("signature", null)
            val region = obj.optString("region", null)

            dataStore.updateUser(userId, nickname, avatar, avatarUrl, avatarVersion, signature, region)

            val eventPayload = eventService.createUserUpdateEvent(userId, nickname, avatar, avatarUrl, avatarVersion, signature, region)
            val friends = dataStore.getFriends(userId)
            for (friendId in friends) {
                val userSeq = eventService.nextUserSeq(friendId)
                val payload = JSONObject(eventPayload.toString())
                payload.put("userSeq", userSeq)
                val inboxEvent = InboxEvent(userSeq, 9, payload)
                eventService.appendEvent(friendId, inboxEvent)
                val friendConn = connectionRegistry.getConnection(friendId)
                if (friendConn != null && friendConn.isOpen) {
                    try { friendConn.send(codec.encodeNewEventNotify(userSeq, 0)) } catch (_: Exception) {}
                }
            }

            conn.send(codec.encodeUpdateProfileAck(packet.seq))
            println("[PROFILE] $userId updated profile: nickname=$nickname avatarUrl=$avatarUrl avatarVersion=$avatarVersion")
        } catch (e: Exception) {
            sendError(conn, 400, "Invalid profile format: ${e.message}", packet.seq)
        }
    }

    private fun handleUpdateConversationSettings(conn: ClientConnection, packet: Packet) {
        val userId = getUserId(conn) ?: run { sendError(conn, 401, "Authentication required", packet.seq); return }

        try {
            val json = codec.getBodyAsString(packet)
            val obj = JSONObject(json)
            val conversationId = obj.getString("conversationId")
            val isPinned = obj.optBoolean("isPinned", false)
            val pinnedTime = obj.optLong("pinnedTime", 0)
            val mute = obj.optBoolean("mute", false)

            val settings = ServerConversationSettings(
                isPinned = isPinned,
                pinnedTime = pinnedTime,
                mute = mute
            )
            dataStore.setConversationSettings(userId, conversationId, settings)

            // Push event to user's inbox for multi-device sync
            val eventPayload = eventService.createConversationSettingsUpdatedEvent(
                conversationId, isPinned, pinnedTime, mute
            )
            val userSeq = eventService.nextUserSeq(userId)
            eventService.appendEvent(userId, InboxEvent(
                userSeq = userSeq,
                eventType = com.lightchat.server.store.EventTypes.CONVERSATION_SETTINGS_UPDATED,
                payload = eventPayload,
                createdAt = System.currentTimeMillis()
            ))

            conn.send(codec.encodeUpdateConversationSettingsAck(packet.seq))
            println("[CONV-SETTINGS] $userId updated settings for $conversationId: pinned=$isPinned mute=$mute")
        } catch (e: Exception) {
            sendError(conn, 400, "Invalid conversation settings format: ${e.message}", packet.seq)
        }
    }

    private fun sendError(conn: ClientConnection, code: Int, message: String, seq: Long) {
        try {
            conn.send(codec.encodeError(code, message, seq))
        } catch (e: Exception) {
            println("[ERROR] Failed to send error: ${e.message}")
        }
    }

    private fun singleConversationId(userA: String, userB: String): String {
        val min = if (userA < userB) userA else userB
        val max = if (userA < userB) userB else userA
        return "single_${min}_$max"
    }
}

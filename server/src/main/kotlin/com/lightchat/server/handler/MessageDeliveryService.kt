package com.lightchat.server.handler

import com.lightchat.server.model.InboxEvent
import com.lightchat.server.model.ServerMessage
import com.lightchat.server.protocol.ProtocolCodec
import com.lightchat.server.push.MockVendorPushGateway
import com.lightchat.server.session.ConnectionRegistry
import com.lightchat.server.store.DataStore
import com.lightchat.server.store.EventService

class MessageDeliveryService(
    private val dataStore: DataStore,
    private val eventService: EventService,
    private val connectionRegistry: ConnectionRegistry,
    private val codec: ProtocolCodec,
    private val pushGateway: MockVendorPushGateway
) {

    fun deliver(senderId: String, message: ServerMessage): List<String> {
        val recipients = determineRecipients(senderId, message)
        println("[DELIVERY] ${message.messageId} recipients=${recipients.joinToString(",")} sender=$senderId")

        for (recipientId in recipients) {
            if (recipientId == senderId) continue

            val eventPayload = eventService.createNewMessageEvent(message)
            val userSeq = eventService.nextUserSeq(recipientId)
            eventPayload.put("userSeq", userSeq)
            eventPayload.put("receiverId", recipientId)

            val inboxEvent = InboxEvent(
                userSeq = userSeq,
                eventType = 1,
                payload = eventPayload
            )
            eventService.appendEvent(recipientId, inboxEvent)

            val recipientConn = connectionRegistry.getConnection(recipientId)
            if (recipientConn != null && recipientConn.isOpen) {
                try {
                    recipientConn.send(codec.encodeNewEventNotify(userSeq, 0))
                } catch (e: Exception) {
                    println("[DELIVERY] Failed to notify $recipientId: ${e.message}")
                }
            } else {
                pushGateway.enqueueMessage(recipientId, message)
            }
        }

        recipients.forEach { recipientId ->
            dataStore.addParticipant(message.conversationId, recipientId)
        }
        return recipients.filter { it != senderId }
    }

    fun deliverEvent(senderId: String, conversationId: String?, receiverId: String?, groupId: String?, eventPayload: org.json.JSONObject, eventType: Int): List<String> {
        val dummyMsg = ServerMessage(
            messageId = "",
            conversationId = conversationId ?: "",
            senderId = senderId,
            receiverId = receiverId,
            groupId = groupId,
            messageType = 0,
            content = "",
            clientSeq = 0,
            conversationSeq = 0,
            sendTime = System.currentTimeMillis()
        )
        val recipients = determineRecipients(senderId, dummyMsg)

        for (recipientId in recipients) {
            if (eventType == 3 && recipientId == senderId) continue

            val userSeq = eventService.nextUserSeq(recipientId)
            val payload = org.json.JSONObject(eventPayload.toString())
            payload.put("userSeq", userSeq)

            val inboxEvent = InboxEvent(
                userSeq = userSeq,
                eventType = eventType,
                payload = payload
            )
            eventService.appendEvent(recipientId, inboxEvent)

            val recipientConn = connectionRegistry.getConnection(recipientId)
            if (recipientConn != null && recipientConn.isOpen) {
                try {
                    if (eventType == 3) {
                        recipientConn.send(
                            codec.encodeReadNotify(
                                conversationId = conversationId.orEmpty(),
                                readUserId = senderId,
                                lastReadSeq = payload.optLong("lastReadSeq", 0)
                            )
                        )
                        recipientConn.send(codec.encodeNewEventNotify(userSeq, 0))
                        println("[READ_NOTIFY] $senderId -> $recipientId conversation=${conversationId.orEmpty()} seq=${payload.optLong("lastReadSeq", 0)}")
                    } else {
                        recipientConn.send(codec.encodeNewEventNotify(userSeq, 0))
                    }
                } catch (e: Exception) {
                    println("[DELIVERY] Failed to notify $recipientId: ${e.message}")
                }
            }
        }

        return recipients
    }

    fun determineRecipients(senderId: String, message: ServerMessage): List<String> {
        // Strategy 1: explicit receiverId or groupId in message body.
        val receiverId = message.receiverId
        if (!receiverId.isNullOrBlank() && receiverId != senderId) {
            return listOf(senderId, receiverId).distinct()
        }
        val groupId = message.groupId
        if (!groupId.isNullOrBlank()) {
            val members = dataStore.getGroupMembers(groupId)
            if (members.isNotEmpty()) return members.toList()
        }

        // Strategy 2: group_{groupId} → all group members
        val groupPattern = Regex("^group_(.+)$")
        val groupMatch = groupPattern.find(message.conversationId)
        if (groupMatch != null) {
            val gid = groupMatch.groupValues[1]
            val members = dataStore.getGroupMembers(gid)
            if (members.isNotEmpty()) return members.toList()
        }

        // Strategy 3: registered conversation participants
        val conv = dataStore.getConversation(message.conversationId)
        if (conv != null && conv.participants.size >= 2) {
            return conv.participants.toList()
        }

        // Strategy 4: fallback parsing for older single-chat callers without receiverId.
        val singlePrefix = "single_"
        val singleBody = message.conversationId.removePrefix(singlePrefix)
        if (message.conversationId.startsWith(singlePrefix)) {
            val otherId = when {
                singleBody.startsWith("${senderId}_") -> singleBody.removePrefix("${senderId}_")
                singleBody.endsWith("_$senderId") -> singleBody.removeSuffix("_$senderId")
                else -> null
            }
            if (!otherId.isNullOrBlank()) return listOf(senderId, otherId)
        }

        // Fallback: sender only
        return listOf(senderId)
    }
}

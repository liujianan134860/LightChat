package com.lightchat.server.store

import com.lightchat.server.model.InboxEvent
import com.lightchat.server.model.ServerGroup
import com.lightchat.server.model.ServerMessage
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

object EventTypes {
    const val NEW_MESSAGE = 1
    const val MESSAGE_RECALL = 2
    const val MESSAGE_READ = 3
    const val GROUP_CREATED = 4
    const val GROUP_MEMBER_JOIN = 5
    const val GROUP_MEMBER_LEAVE = 6
    const val FRIEND_REQUEST = 7
    const val FRIEND_ACCEPTED = 8
    const val USER_UPDATE = 9
    const val CONVERSATION_SETTINGS_UPDATED = 10
}

class EventService(private val dataStore: DataStore) {

    private val userSeqCounters = ConcurrentHashMap<String, AtomicLong>()
    private val convSeqCounters = ConcurrentHashMap<String, AtomicLong>()
    private val inboxes = ConcurrentHashMap<String, ConcurrentLinkedDeque<InboxEvent>>()

    fun nextUserSeq(userId: String): Long {
        val counter = userSeqCounters.computeIfAbsent(userId) { AtomicLong(0) }
        val seq = counter.incrementAndGet()
        // Repair: if state was loaded from a non-atomic snapshot where counter < max inbox seq,
        // jump ahead to avoid creating a duplicate
        val inbox = inboxes[userId]
        if (inbox != null) {
            val maxSeq = inbox.maxOfOrNull { it.userSeq } ?: 0L
            if (seq <= maxSeq) {
                counter.set(maxSeq)
                return counter.incrementAndGet()
            }
        }
        return seq
    }

    fun nextConversationSeq(conversationId: String): Long {
        return convSeqCounters.computeIfAbsent(conversationId) { AtomicLong(0) }.incrementAndGet()
    }

    fun getLatestUserSeq(userId: String): Long {
        return userSeqCounters[userId]?.get() ?: 0L
    }

    fun getInboxSize(userId: String): Int {
        return inboxes[userId]?.size ?: 0
    }

    fun appendEvent(userId: String, event: InboxEvent) {
        inboxes.computeIfAbsent(userId) { ConcurrentLinkedDeque() }.add(event)
        dataStore.onChanged?.invoke()
    }

    fun getEventsSince(userId: String, lastUserSeq: Long): List<InboxEvent> {
        val inbox = inboxes[userId] ?: return emptyList()
        return inbox.filter { it.userSeq > lastUserSeq }.sortedBy { it.userSeq }
    }

    fun createNewMessageEvent(msg: ServerMessage): JSONObject {
        return JSONObject().apply {
            put("eventType", EventTypes.NEW_MESSAGE)
            put("messageId", msg.messageId)
            put("conversationId", msg.conversationId)
            put("senderId", msg.senderId)
            dataStore.getUser(msg.senderId)?.let { sender ->
                put("senderNickname", sender.nickname)
                put("senderAvatarUrl", sender.avatarUrl)
                put("senderAvatarVersion", sender.avatarVersion)
            }
            if (msg.receiverId != null) put("receiverId", msg.receiverId)
            if (msg.receiverId != null) {
                dataStore.getUser(msg.receiverId)?.let { receiver ->
                    put("receiverNickname", receiver.nickname)
                    put("receiverAvatarUrl", receiver.avatarUrl)
                    put("receiverAvatarVersion", receiver.avatarVersion)
                }
            }
            if (msg.groupId != null) put("groupId", msg.groupId)
            put("messageType", msg.messageType)
            put("content", msg.content)
            put("conversationSeq", msg.conversationSeq)
            put("sendTime", msg.sendTime)
            put("createTime", msg.createTime)
            if (msg.extra != null) put("extra", msg.extra)
        }
    }

    fun createGroupCreatedEvent(group: ServerGroup): JSONObject {
        val membersArr = JSONArray()
        group.members.forEach { memberId ->
            val user = dataStore.getUser(memberId)
            membersArr.put(JSONObject().apply {
                put("userId", memberId)
                put("nickname", user?.nickname ?: memberId)
                put("avatar", (user?.avatar ?: ""))
                put("avatarUrl", user?.avatarUrl ?: "")
                put("avatarVersion", user?.avatarVersion ?: 0)
                put("role", if (memberId == group.ownerId) 0 else 1)
            })
        }
        return JSONObject().apply {
            put("eventType", EventTypes.GROUP_CREATED)
            put("groupId", group.groupId)
            put("groupName", group.groupName)
            put("ownerId", group.ownerId)
            put("memberCount", group.members.size)
            put("members", membersArr)
            put("createTime", group.createdAt)
        }
    }

    fun createGroupMemberJoinEvent(
        groupId: String,
        members: List<Pair<String, String>>,
        invitedMembers: List<Pair<String, String>> = members,
        inviterId: String? = null
    ): JSONObject {
        val group = dataStore.getGroup(groupId)
        val membersArr = JSONArray()
        members.forEach { (userId, nickname) ->
            val user = dataStore.getUser(userId)
            membersArr.put(JSONObject().apply {
                put("userId", userId)
                put("nickname", nickname)
                put("avatar", (user?.avatar ?: ""))
                put("avatarUrl", user?.avatarUrl ?: "")
                put("avatarVersion", user?.avatarVersion ?: 0)
                put("role", if (userId == group?.ownerId) 0 else 1)
            })
        }
        val invitedArr = JSONArray()
        invitedMembers.forEach { (userId, nickname) ->
            val user = dataStore.getUser(userId)
            invitedArr.put(JSONObject().apply {
                put("userId", userId)
                put("nickname", nickname)
                put("avatar", (user?.avatar ?: ""))
                put("avatarUrl", user?.avatarUrl ?: "")
                put("avatarVersion", user?.avatarVersion ?: 0)
                put("role", if (userId == group?.ownerId) 0 else 1)
            })
        }
        return JSONObject().apply {
            put("eventType", EventTypes.GROUP_MEMBER_JOIN)
            put("groupId", groupId)
            if (inviterId != null) {
                put("inviterId", inviterId)
                dataStore.getUser(inviterId)?.let { put("inviterNickname", it.nickname) }
            }
            if (group != null) {
                put("groupName", group.groupName)
                put("ownerId", group.ownerId)
                put("memberCount", group.members.size)
                put("createTime", group.createdAt)
            }
            put("members", membersArr)
            put("invitedMembers", invitedArr)
        }
    }

    fun createGroupMemberLeaveEvent(groupId: String, memberIds: List<String>): JSONObject {
        val membersArr = JSONArray()
        memberIds.forEach { userId ->
            membersArr.put(JSONObject().apply {
                put("userId", userId)
            })
        }
        return JSONObject().apply {
            put("eventType", EventTypes.GROUP_MEMBER_LEAVE)
            put("groupId", groupId)
            put("members", membersArr)
        }
    }

    fun buildSyncResultJson(events: List<InboxEvent>, hasMore: Boolean = false, nextUserSeq: Long = 0): String {
        val arr = JSONArray()
        events.forEach { event ->
            val obj = JSONObject(event.payload.toString())
            obj.put("userSeq", event.userSeq)
            arr.put(obj)
        }
        return JSONObject().apply {
            put("events", arr)
            put("hasMore", hasMore)
            put("nextUserSeq", nextUserSeq)
        }.toString()
    }

    fun createMessageRecallEvent(messageId: String, conversationId: String, senderId: String): JSONObject {
        return JSONObject().apply {
            put("eventType", EventTypes.MESSAGE_RECALL)
            put("recalledMessageId", messageId)
            put("conversationId", conversationId)
            put("senderId", senderId)
        }
    }

    fun createMessageReadEvent(conversationId: String, readUserId: String, lastReadSeq: Long): JSONObject {
        return JSONObject().apply {
            put("eventType", EventTypes.MESSAGE_READ)
            put("conversationId", conversationId)
            put("readConversationId", conversationId)
            put("readUserId", readUserId)
            put("lastReadSeq", lastReadSeq)
        }
    }

    fun createFriendRequestEvent(fromUserId: String, toUserId: String, fromNickname: String, message: String): JSONObject {
        return JSONObject().apply {
            put("eventType", EventTypes.FRIEND_REQUEST)
            put("fromUserId", fromUserId)
            put("toUserId", toUserId)
            put("fromNickname", fromNickname)
            put("requestMessage", message)
        }
    }

    fun createFriendAcceptedEvent(fromUserId: String, toUserId: String): JSONObject {
        val fromUser = dataStore.getUser(fromUserId)
        val toUser = dataStore.getUser(toUserId)
        return JSONObject().apply {
            put("eventType", EventTypes.FRIEND_ACCEPTED)
            put("fromUserId", fromUserId)
            put("toUserId", toUserId)
            put("fromNickname", fromUser?.nickname ?: fromUserId)
            put("toNickname", toUser?.nickname ?: toUserId)
            put("fromAvatarUrl", fromUser?.avatarUrl ?: "")
            put("fromAvatarVersion", fromUser?.avatarVersion ?: 0)
            put("toAvatarUrl", toUser?.avatarUrl ?: "")
            put("toAvatarVersion", toUser?.avatarVersion ?: 0)
        }
    }

    fun createConversationSettingsUpdatedEvent(conversationId: String, isPinned: Boolean, pinnedTime: Long, mute: Boolean): JSONObject {
        return JSONObject().apply {
            put("eventType", EventTypes.CONVERSATION_SETTINGS_UPDATED)
            put("conversationId", conversationId)
            put("isPinned", isPinned)
            put("pinnedTime", pinnedTime)
            put("mute", mute)
        }
    }

    fun createUserUpdateEvent(userId: String, nickname: String?, avatar: String?, avatarUrl: String?, avatarVersion: Int?, signature: String?, region: String?): JSONObject {
        return JSONObject().apply {
            put("eventType", EventTypes.USER_UPDATE)
            put("senderId", userId)
            if (nickname != null) put("nickname", nickname)
            if (avatar != null) put("avatar", (avatar ?: ""))
            if (avatarUrl != null) put("avatarUrl", avatarUrl)
            if (avatarVersion != null) put("avatarVersion", avatarVersion)
            if (signature != null) put("signature", signature)
            if (region != null) put("region", region)
        }
    }

    fun toJson(): JSONObject {
        // Repair counters before snapshot to ensure consistency
        inboxes.forEach { (userId, events) ->
            val maxSeq = events.maxOfOrNull { it.userSeq } ?: return@forEach
            userSeqCounters.computeIfAbsent(userId) { AtomicLong(maxSeq) }
                .updateAndGet { maxOf(it, maxSeq) }
        }

        return JSONObject().apply {
            put("userSeqCounters", JSONObject().apply {
                userSeqCounters.forEach { (userId, counter) -> put(userId, counter.get()) }
            })
            put("convSeqCounters", JSONObject().apply {
                convSeqCounters.forEach { (conversationId, counter) -> put(conversationId, counter.get()) }
            })
            put("inboxes", JSONObject().apply {
                inboxes.forEach { (userId, events) ->
                    // Deduplicate by userSeq to guard against any in-memory duplicates
                    val seen = mutableSetOf<Long>()
                    put(userId, JSONArray().apply {
                        events.sortedBy { it.userSeq }.forEach { event ->
                            if (seen.add(event.userSeq)) {
                                put(JSONObject().apply {
                                    put("userSeq", event.userSeq)
                                    put("eventType", event.eventType)
                                    put("payload", JSONObject(event.payload.toString()))
                                    put("createdAt", event.createdAt)
                                })
                            }
                        }
                    })
                }
            })
        }
    }

    fun loadFromJson(root: JSONObject) {
        userSeqCounters.clear()
        convSeqCounters.clear()
        inboxes.clear()

        root.optJSONObject("userSeqCounters")?.let { counters ->
            counters.keys().forEach { userId ->
                userSeqCounters[userId] = AtomicLong(counters.optLong(userId, 0))
            }
        }
        root.optJSONObject("convSeqCounters")?.let { counters ->
            counters.keys().forEach { conversationId ->
                convSeqCounters[conversationId] = AtomicLong(counters.optLong(conversationId, 0))
            }
        }
        root.optJSONObject("inboxes")?.let { allInboxes ->
            allInboxes.keys().forEach { userId ->
                val queue = ConcurrentLinkedDeque<InboxEvent>()
                val arr = allInboxes.optJSONArray(userId) ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val payload = obj.optJSONObject("payload") ?: JSONObject()
                    queue.add(
                        InboxEvent(
                            userSeq = obj.optLong("userSeq", 0),
                            eventType = obj.optInt("eventType", 0),
                            payload = payload,
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                inboxes[userId] = queue
            }
        }

        // Ensure counters are >= max inbox seq to prevent duplicates after non-atomic snapshot
        inboxes.forEach { (userId, events) ->
            val maxSeq = events.maxOfOrNull { it.userSeq } ?: 0L
            val counter = userSeqCounters[userId]
            if (counter == null) {
                userSeqCounters[userId] = AtomicLong(maxSeq)
            } else {
                counter.updateAndGet { current -> maxOf(current, maxSeq) }
            }
        }
    }

}

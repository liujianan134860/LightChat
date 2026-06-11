package com.lightchat.sync

import com.lightchat.LightChatApplication
import com.lightchat.event.AppEvents
import com.lightchat.im.ConnectionState
import com.lightchat.im.ImClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class SyncManager(
    private val imClient: ImClient,
    private val eventProcessor: EventProcessor
) {
    private val app = LightChatApplication.instance
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isSyncing = false
    @Volatile private var pendingSync = false
    @Volatile private var pendingSinceSeq = 0L
    private var authSyncJob: Job? = null

    private val onNewEventNotify: (Long) -> Unit = { latestUserSeq ->
        if (started) {
            val localSeq = app.syncStateDao.getLastUserSeq()
            if (imClient.state.value.connectionState == ConnectionState.AUTHENTICATED) {
                val syncFromSeq = if (latestUserSeq > localSeq) {
                    localSeq
                } else {
                    (latestUserSeq - 1).coerceAtLeast(0)
                }
                triggerSync(syncFromSeq)
            }
        }
    }

    private val onSyncResult: (String) -> Unit = { json ->
        if (started) {
            isSyncing = false
            parseAndProcess(json) {
                val root = try { org.json.JSONObject(json) } catch (_: Exception) { null }
                val hasMore = root?.optBoolean("hasMore", false) == true
                val nextSeq = root?.optLong("nextUserSeq", 0L) ?: 0L
                if (hasMore && nextSeq > 0L) {
                    triggerSync(nextSeq)
                } else if (pendingSync) {
                    pendingSync = false
                    val seq = pendingSinceSeq.takeIf { it > 0 } ?: app.syncStateDao.getLastUserSeq()
                    pendingSinceSeq = 0L
                    triggerSync(seq)
                }
            }
        }
    }

    private val onMessageAck: (String, Int, Long) -> Unit = { messageId, status, conversationSeq ->
        if (started) {
            scope.launch {
                val msg = app.messageDao.getById(messageId)
                if (msg != null) {
                    val newStatus = when (status) {
                        0 -> com.lightchat.model.MessageStatus.SENT
                        1 -> com.lightchat.model.MessageStatus.DELIVERED
                        2 -> com.lightchat.model.MessageStatus.READ
                        else -> com.lightchat.model.MessageStatus.FAILED
                    }
                    app.messageDao.updateStatusAndConversationSeq(messageId, newStatus, conversationSeq)
                    AppEvents.notifyMessageChanged(msg.conversationId)
                    AppEvents.notifyConversationChanged(msg.conversationId)
                }
            }
        }
    }

    private val onReadNotify: (String, String, Long) -> Unit = { conversationId, readUserId, lastReadSeq ->
        if (started && readUserId != app.userSession.currentUserId) {
            scope.launch {
                applyReadReceipt(conversationId, readUserId, lastReadSeq)
            }
        }
    }

    private var started = false

    fun start() {
        if (started) return
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
        started = true
        imClient.onNewEventNotify(onNewEventNotify)
        imClient.onSyncResult(onSyncResult)
        imClient.onMessageAck(onMessageAck)
        imClient.onReadNotify(onReadNotify)
        authSyncJob = scope.launch {
            imClient.state
                .map { it.connectionState }
                .distinctUntilChanged()
                .collect { connectionState ->
                if (started && connectionState == ConnectionState.AUTHENTICATED) {
                    requestSync()
                }
            }
        }
    }

    private fun applyReadReceipt(conversationId: String, readUserId: String, lastReadSeq: Long) {
        val currentUserId = app.userSession.currentUserId ?: return
        app.groupDao.upsertConversationMemberRead(conversationId, readUserId, lastReadSeq)
        val updated = app.messageDao.markLastSentMessageRead(conversationId, currentUserId, lastReadSeq)
        if (updated == 0) {
            app.messageDao.getLatestSentMessage(conversationId, currentUserId)?.let { latest ->
                app.messageDao.updateStatus(latest.messageId, com.lightchat.model.MessageStatus.READ)
            }
        }
        AppEvents.notifyMessageChanged(conversationId)
        AppEvents.notifyConversationChanged(conversationId)
    }

    fun triggerSync(lastUserSeq: Long) {
        if (isSyncing) {
            pendingSync = true
            if (pendingSinceSeq == 0L || lastUserSeq < pendingSinceSeq) {
                pendingSinceSeq = lastUserSeq
            }
            return
        }
        isSyncing = true
        if (!imClient.sync(lastUserSeq)) {
            isSyncing = false
        }
    }

    fun requestSync() {
        val seq = app.syncStateDao.getLastUserSeq()
        triggerSync(seq)
    }

    private fun parseAndProcess(json: String, onDone: () -> Unit = {}) {
        scope.launch {
            try {
                val events = parseSyncResult(json)
                eventProcessor.process(events)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                onDone()
            }
        }
    }

    private fun parseSyncResult(json: String): List<SyncEvent> {
        val events = mutableListOf<SyncEvent>()
        val root = JSONObject(json)
        val arr = root.optJSONArray("events") ?: return events
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            events.add(parseEvent(obj))
        }
        return events
    }

    private fun parseEvent(obj: JSONObject): SyncEvent {
        val membersArr = obj.optJSONArray("members")
        val members = membersArr?.let { arr ->
            (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                MemberInfo(
                    userId = m.getString("userId"),
                    nickname = m.optString("nickname", ""),
                    avatar = m.optString("avatar", ""),
                    avatarUrl = m.optString("avatarUrl", ""),
                    avatarVersion = m.optInt("avatarVersion", 0),
                    role = m.optInt("role", 1)
                )
            }
        }
        val invitedMembersArr = obj.optJSONArray("invitedMembers")
        val invitedMembers = invitedMembersArr?.let { arr ->
            (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                MemberInfo(
                    userId = m.getString("userId"),
                    nickname = m.optString("nickname", ""),
                    avatar = m.optString("avatar", ""),
                    avatarUrl = m.optString("avatarUrl", ""),
                    avatarVersion = m.optInt("avatarVersion", 0),
                    role = m.optInt("role", 1)
                )
            }
        }
        return SyncEvent(
            eventType = obj.getInt("eventType"),
            userSeq = obj.getLong("userSeq"),
            messageId = obj.optString("messageId", null),
            conversationId = obj.optString("conversationId", null),
            senderId = obj.optString("senderId", null),
            senderNickname = obj.optString("senderNickname", null),
            senderAvatarUrl = obj.optString("senderAvatarUrl", null),
            senderAvatarVersion = if (obj.has("senderAvatarVersion")) obj.getInt("senderAvatarVersion") else null,
            receiverId = obj.optString("receiverId", null),
            receiverNickname = obj.optString("receiverNickname", null),
            receiverAvatarUrl = obj.optString("receiverAvatarUrl", null),
            receiverAvatarVersion = if (obj.has("receiverAvatarVersion")) obj.getInt("receiverAvatarVersion") else null,
            groupId = obj.optString("groupId", null),
            messageType = if (obj.has("messageType")) obj.getInt("messageType") else null,
            content = obj.optString("content", null),
            conversationSeq = if (obj.has("conversationSeq")) obj.getLong("conversationSeq") else null,
            sendTime = if (obj.has("sendTime")) obj.getLong("sendTime") else null,
            createTime = if (obj.has("createTime")) obj.getLong("createTime") else null,
            extra = obj.optString("extra", null),
            groupName = obj.optString("groupName", null),
            ownerId = obj.optString("ownerId", null),
            memberCount = if (obj.has("memberCount")) obj.getInt("memberCount") else null,
            members = members,
            inviterId = obj.optString("inviterId", null),
            inviterNickname = obj.optString("inviterNickname", null),
            invitedMembers = invitedMembers,
            fromUserId = obj.optString("fromUserId", null),
            toUserId = obj.optString("toUserId", null),
            fromNickname = obj.optString("fromNickname", null),
            toNickname = obj.optString("toNickname", null),
            fromAvatarUrl = obj.optString("fromAvatarUrl", null),
            fromAvatarVersion = if (obj.has("fromAvatarVersion")) obj.getInt("fromAvatarVersion") else null,
            toAvatarUrl = obj.optString("toAvatarUrl", null),
            toAvatarVersion = if (obj.has("toAvatarVersion")) obj.getInt("toAvatarVersion") else null,
            requestMessage = obj.optString("requestMessage", null),
            recalledMessageId = obj.optString("recalledMessageId", null),
            readConversationId = obj.optString("readConversationId", null),
            readUserId = obj.optString("readUserId", null),
            lastReadSeq = if (obj.has("lastReadSeq")) obj.getLong("lastReadSeq") else null,
            nickname = obj.optString("nickname", null),
            avatar = obj.optString("avatar", null),
            avatarUrl = obj.optString("avatarUrl", null),
            avatarVersion = if (obj.has("avatarVersion")) obj.getInt("avatarVersion") else null,
            signature = obj.optString("signature", null),
            region = obj.optString("region", null),
            isPinned = if (obj.has("isPinned")) obj.getBoolean("isPinned") else null,
            pinnedTime = if (obj.has("pinnedTime")) obj.getLong("pinnedTime") else null,
            mute = if (obj.has("mute")) obj.getBoolean("mute") else null
        )
    }

    fun destroy() {
        authSyncJob?.cancel()
        authSyncJob = null
        isSyncing = false
        started = false
        scope.cancel()
    }
}

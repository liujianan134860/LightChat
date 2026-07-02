package com.lightchat.sync

import com.lightchat.data.local.DatabaseHelper
import com.lightchat.data.local.UserSession
import com.lightchat.data.local.dao.*
import com.lightchat.domain.notification.MessageNotifier
import com.lightchat.domain.session.AppPresence
import com.lightchat.event.AppEvents
import com.lightchat.model.*

class EventProcessor(
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val groupDao: GroupDao,
    private val userDao: UserDao,
    private val friendRequestDao: FriendRequestDao,
    private val syncStateDao: SyncStateDao,
    private val databaseHelper: DatabaseHelper,
    private val userSession: UserSession,
    private val appPresence: AppPresence,
    private val messageNotifier: MessageNotifier
) {

    fun process(events: List<SyncEvent>): Int {
        var processed = 0
        for (event in events) {
            try {
                when (event.eventType) {
                    EventType.NEW_MESSAGE -> handleNewMessage(event)
                    EventType.MESSAGE_RECALL -> handleMessageRecall(event)
                    EventType.MESSAGE_READ -> handleMessageRead(event)
                    EventType.GROUP_CREATED -> handleGroupCreated(event)
                    EventType.GROUP_MEMBER_JOIN -> handleGroupMemberJoin(event)
                    EventType.GROUP_MEMBER_LEAVE -> handleGroupMemberLeave(event)
                    EventType.FRIEND_REQUEST_EVENT -> handleFriendRequest(event)
                    EventType.FRIEND_ACCEPTED -> handleFriendAccepted(event)
                    EventType.USER_UPDATE -> handleUserUpdate(event)
                    EventType.CONVERSATION_SETTINGS_UPDATED -> handleConversationSettingsUpdated(event)
                }
                processed++
                syncStateDao.setLastUserSeq(event.userSeq)
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }
        }
        return processed
    }

    private fun handleNewMessage(e: SyncEvent) {
        val msg = Message(
            messageId = e.messageId ?: return,
            conversationId = e.conversationId ?: return,
            senderId = e.senderId ?: "",
            receiverId = e.receiverId,
            groupId = e.groupId,
            messageType = MessageType.fromInt(e.messageType ?: 0),
            content = e.content ?: "",
            status = MessageStatus.DELIVERED,
            conversationSeq = e.conversationSeq ?: 0,
            userSeq = e.userSeq,
            sendTime = e.sendTime ?: System.currentTimeMillis(),
            createTime = e.createTime ?: System.currentTimeMillis(),
            extra = e.extra
        )
        // Keep optimistic local inserts, but still backfill the authoritative server sequence.
        val existing = messageDao.getById(msg.messageId)
        if (existing != null) {
            val syncedStatus = if (msg.senderId == userSession.currentUserId) {
                MessageStatus.SENT
            } else {
                MessageStatus.DELIVERED
            }
            messageDao.updateStatusAndConversationSeq(msg.messageId, syncedStatus, msg.conversationSeq)
            return
        }

        messageDao.insert(msg)
        upsertMessageProfiles(e)

        val displayContent = when (msg.messageType) {
            MessageType.IMAGE -> "[图片]"
            MessageType.USER_CARD -> "[名片]${msg.content}"
            MessageType.GROUP_CARD -> "[群名片]${msg.content}"
            MessageType.MERGE_FORWARD -> "[聊天记录]"
            else -> msg.content
        }

        // Upsert conversation
        val conv = conversationDao.getById(msg.conversationId)
        val isActiveConversation = appPresence.isForeground && appPresence.currentConversationId == msg.conversationId
        val isMentioned = isCurrentUserMentioned(msg.extra)
        if (conv == null) {
            val convType = if (e.groupId != null) ConversationType.GROUP else ConversationType.SINGLE
            val title = if (e.groupId != null) {
                groupDao.getGroupById(e.groupId)?.groupName ?: "群聊"
            } else {
                resolveSinglePeerName(msg.conversationId, e)
            }
            val targetId = if (e.groupId != null) e.groupId else resolveSinglePeerId(msg.conversationId, e)
            val targetUser = if (e.groupId != null) null else userDao.getById(targetId)
            val targetAvatar = targetUser?.avatar.orEmpty()
            val targetAvatarUrl = targetUser?.avatarUrl ?: ""
            val targetAvatarVersion = targetUser?.avatarVersion ?: 0
            val newThumbnailPath = null
            conversationDao.insert(
                Conversation(
                    conversationId = msg.conversationId,
                    type = convType,
                    targetId = targetId,
                    title = title,
                    avatar = targetAvatar,
                    avatarUrl = targetAvatarUrl,
                    avatarVersion = targetAvatarVersion,
                    lastMessageId = msg.messageId,
                    lastMessageContent = displayContent,
                    lastMessageTime = msg.createTime,
                    lastMessageThumbnail = newThumbnailPath,
                    unreadCount = if (isActiveConversation || msg.senderId == userSession.currentUserId) 0 else 1,
                    atMe = !isActiveConversation && msg.senderId != userSession.currentUserId && isMentioned,
                    atMeCount = if (!isActiveConversation && msg.senderId != userSession.currentUserId && isMentioned) 1 else 0
                )
            )
        } else {
            if (conv.type == ConversationType.SINGLE) {
                val targetId = resolveSinglePeerId(msg.conversationId, e)
                val targetName = resolveSinglePeerName(msg.conversationId, e)
                val user = userDao.getById(targetId)
                val targetAvatar = user?.avatar.orEmpty()
                val targetAvatarUrl = user?.avatarUrl ?: ""
                val targetAvatarVersion = user?.avatarVersion ?: 0
                if (targetName.isNotBlank() && (conv.title != targetName || conv.avatar != targetAvatar || conv.avatarUrl != targetAvatarUrl || conv.targetId != targetId)) {
                    conversationDao.updateSingleDisplayInfo(msg.conversationId, targetId, targetName, targetAvatar, targetAvatarUrl, targetAvatarVersion)
                }
            }
            val thumbnailPath = null
            conversationDao.updateLastMessage(msg.conversationId, msg.messageId, displayContent, msg.createTime, thumbnailPath)
            if (msg.senderId != userSession.currentUserId && !isActiveConversation) {
                conversationDao.incrementUnread(msg.conversationId)
                if (isMentioned) conversationDao.incrementAtMe(msg.conversationId)
            } else if (isActiveConversation) {
                conversationDao.clearUnread(msg.conversationId)
            }
        }

        AppEvents.notifyMessageChanged(msg.conversationId)
        AppEvents.notifyConversationChanged(msg.conversationId)

        val notificationConversation = conversationDao.getById(msg.conversationId)
        if (!appPresence.isForeground && msg.senderId != userSession.currentUserId && !isActiveConversation && notificationConversation?.mute != true) {
            val senderName = userDao.getById(msg.senderId)?.nickname ?: msg.senderId
            val isGroupMessage = msg.groupId != null || e.groupId != null || conv?.type == ConversationType.GROUP
            val notificationTitle = if (isGroupMessage) {
                val groupId = msg.groupId ?: e.groupId ?: conv?.targetId
                groupId?.let { groupDao.getGroupById(it)?.groupName?.takeIf { name -> name.isNotBlank() } }
                    ?: conv?.title?.takeIf { it.isNotBlank() }
                    ?: conversationDao.getById(msg.conversationId)?.title?.takeIf { it.isNotBlank() }
                    ?: "群聊"
            } else {
                senderName
            }
            val notificationContent = if (isGroupMessage) "$senderName: $displayContent" else displayContent
            messageNotifier.showMessage(
                msg.conversationId,
                notificationTitle,
                notificationContent,
                if (isGroupMessage && isMentioned) msg.messageId else ""
            )
        }
    }

    private fun isCurrentUserMentioned(extra: String?): Boolean {
        val currentUserId = userSession.currentUserId ?: return false
        return try {
            val ids = org.json.JSONObject(extra ?: "{}").optJSONArray("atUserIds") ?: return false
            (0 until ids.length()).any { ids.optString(it) == currentUserId }
        } catch (_: Exception) {
            false
        }
    }

    private fun handleMessageRecall(e: SyncEvent) {
        val messageId = e.recalledMessageId ?: return
        val conversationId = e.conversationId ?: return
        val cv = android.content.ContentValues().apply {
            put("is_recalled", 1)
            put("content", "消息已撤回")
        }
        databaseHelper.writableDatabase.update(
            "message",
            cv,
            "owner_user_id = ? AND message_id = ?",
            arrayOf(databaseHelper.currentOwnerId(), messageId)
        )
        // Update conversation last message if it was the recalled one
        val conv = conversationDao.getById(conversationId)
        if (conv != null && conv.lastMessageId == messageId) {
            conversationDao.updateLastMessage(conv.conversationId, messageId, "消息已撤回", System.currentTimeMillis())
        }
        AppEvents.notifyMessageChanged(conversationId)
    }

    private fun handleMessageRead(e: SyncEvent) {
        val convId = e.readConversationId ?: return
        val userId = e.readUserId ?: return
        val lastReadSeq = e.lastReadSeq ?: Long.MAX_VALUE
        groupDao.upsertConversationMemberRead(convId, userId, lastReadSeq)
        if (userId == userSession.currentUserId) {
            conversationDao.clearUnread(convId)
            AppEvents.notifyConversationChanged(convId)
        } else {
            val currentUserId = userSession.currentUserId ?: return
            val updated = messageDao.markLastSentMessageRead(convId, currentUserId, lastReadSeq)
            if (updated == 0) {
                messageDao.getLatestSentMessage(convId, currentUserId)?.let { latest ->
                    messageDao.updateStatus(latest.messageId, MessageStatus.READ)
                }
            }
            AppEvents.notifyMessageChanged(convId)
            AppEvents.notifyConversationChanged(convId)
        }
    }

    private fun upsertMessageProfiles(e: SyncEvent) {
        e.senderId?.let { senderId ->
            upsertUserProfile(
                senderId,
                e.senderNickname?.takeIf { it.isNotBlank() } ?: senderId,
                e.senderAvatarUrl ?: "",
                e.senderAvatarVersion ?: 0
            )
        }
        e.receiverId?.let { receiverId ->
            upsertUserProfile(
                receiverId,
                e.receiverNickname?.takeIf { it.isNotBlank() } ?: receiverId,
                e.receiverAvatarUrl ?: "",
                e.receiverAvatarVersion ?: 0
            )
        }
    }

    private fun resolveSinglePeerId(conversationId: String, e: SyncEvent): String {
        val currentUserId = userSession.currentUserId
        val fromConversationId = Regex("^single_(.+)_(.+)$").find(conversationId)?.groupValues
            ?.drop(1)
            ?.firstOrNull { it != currentUserId }
        return fromConversationId
            ?: listOfNotNull(e.senderId, e.receiverId).firstOrNull { it != currentUserId }
            ?: e.senderId
            ?: e.receiverId
            ?: ""
    }

    private fun resolveSinglePeerName(conversationId: String, e: SyncEvent): String {
        val peerId = resolveSinglePeerId(conversationId, e)
        val eventName = when (peerId) {
            e.senderId -> e.senderNickname
            e.receiverId -> e.receiverNickname
            else -> null
        }
        return eventName?.takeIf { it.isNotBlank() }
            ?: userDao.getById(peerId)?.nickname?.takeIf { it.isNotBlank() && it != peerId }
            ?: peerId
    }

    private fun handleGroupCreated(e: SyncEvent) {
        val groupId = e.groupId ?: return
        val group = ImGroup(
            groupId = groupId,
            groupName = e.groupName ?: "新群聊",
            ownerId = e.ownerId ?: "",
            memberCount = e.memberCount ?: (e.members?.size ?: 0),
            createTime = e.createTime ?: System.currentTimeMillis()
        )
        groupDao.insertGroup(group)
        e.members?.forEach { member ->
            if (userDao.getById(member.userId) == null) {
                userDao.insert(User(member.userId, member.nickname.ifBlank { member.userId }, avatarUrl = member.avatarUrl, avatarVersion = member.avatarVersion))
            }
            groupDao.insertMember(
                GroupMember(
                    groupId = groupId,
                    userId = member.userId,
                    nickname = member.nickname,
                    avatar = member.avatar,
                    avatarUrl = member.avatarUrl,
                    avatarVersion = member.avatarVersion,
                    role = if (member.role == 0) MemberRole.OWNER else MemberRole.MEMBER,
                    joinTime = System.currentTimeMillis()
                )
            )
        }
        // Create group conversation
        val convId = "group_$groupId"
        val now = System.currentTimeMillis()
        if (conversationDao.getById(convId) == null) {
            conversationDao.insert(
                Conversation(
                    conversationId = convId,
                    type = ConversationType.GROUP,
                    targetId = groupId,
                    title = group.groupName,
                    lastMessageContent = "",
                    lastMessageTime = now
                )
            )
        }
        val ownerName = memberDisplayName(e.ownerId ?: group.ownerId)
        val invitedNames = e.members.orEmpty()
            .filter { it.userId != (e.ownerId ?: group.ownerId) }
            .map { it.nickname.ifBlank { it.userId } }
        val content = if (invitedNames.isNotEmpty()) {
            "${ownerName}邀请${invitedNames.joinToString("、")}加入了群聊"
        } else {
            "${ownerName}创建了群聊"
        }
        val eventTime = e.createTime ?: now
        insertSystemMessage(
            conversationId = convId,
            messageId = "sys_group_created_${groupId}_${e.userSeq}",
            content = content,
            createTime = eventTime
        )
        AppEvents.notifyConversationChanged(convId)
    }

    private fun handleGroupMemberJoin(e: SyncEvent) {
        val groupId = e.groupId ?: return
        val convId = ConversationId.group(groupId)
        val eventTime = e.createTime ?: System.currentTimeMillis()
        if (groupDao.getGroupById(groupId) == null) {
            val group = ImGroup(
                groupId = groupId,
                groupName = e.groupName ?: "群聊",
                ownerId = e.ownerId ?: "",
                memberCount = e.memberCount ?: (e.members?.size ?: 0),
                createTime = eventTime
            )
            groupDao.insertGroup(group)
        }
        e.members?.forEach { member ->
            if (userDao.getById(member.userId) == null) {
                userDao.insert(User(member.userId, member.nickname.ifBlank { member.userId }, avatarUrl = member.avatarUrl, avatarVersion = member.avatarVersion))
            }
            groupDao.insertMember(
                GroupMember(
                    groupId = groupId,
                    userId = member.userId,
                    nickname = member.nickname,
                    avatar = member.avatar,
                    avatarUrl = member.avatarUrl,
                    avatarVersion = member.avatarVersion,
                    role = if (member.role == 0) MemberRole.OWNER else MemberRole.MEMBER,
                    joinTime = System.currentTimeMillis()
                )
            )
        }
        val currentMembers = groupDao.getMembers(groupId)
        groupDao.updateMemberCount(groupId, currentMembers.size)
        if (conversationDao.getById(convId) == null) {
            val group = groupDao.getGroupById(groupId)
            conversationDao.insert(
                Conversation(
                    conversationId = convId,
                    type = ConversationType.GROUP,
                    targetId = groupId,
                    title = e.groupName ?: group?.groupName ?: "群聊",
                    lastMessageContent = "",
                    lastMessageTime = eventTime
                )
            )
        }
        val invited = e.invitedMembers?.takeIf { it.isNotEmpty() }
            ?: e.members.orEmpty().filter { member -> member.userId != e.inviterId }
        if (invited.isNotEmpty()) {
            val inviterName = e.inviterNickname
                ?.takeIf { it.isNotBlank() }
                ?: memberDisplayName(e.inviterId ?: e.ownerId.orEmpty())
            val invitedNames = invited.map { it.nickname.ifBlank { it.userId } }
            insertSystemMessage(
                conversationId = convId,
                messageId = "sys_group_join_${groupId}_${e.userSeq}",
                content = "${inviterName}邀请${invitedNames.joinToString("、")}加入了群聊",
                createTime = eventTime
            )
        }
        AppEvents.notifyConversationChanged(convId)
    }

    private fun handleGroupMemberLeave(e: SyncEvent) {
        val groupId = e.groupId ?: return
        e.members?.forEach { member ->
            groupDao.removeMember(groupId, member.userId)
        }
        val currentMembers = groupDao.getMembers(groupId)
        groupDao.updateMemberCount(groupId, currentMembers.size)
    }

    private fun handleFriendRequest(e: SyncEvent) {
        val fromUserId = e.fromUserId ?: return
        val toUserId = e.toUserId ?: return
        if (friendRequestDao.hasPendingRequest(fromUserId, toUserId)) return
        upsertUserProfile(
            fromUserId,
            e.fromNickname?.takeIf { it.isNotBlank() } ?: fromUserId,
            e.fromAvatarUrl ?: "",
            e.fromAvatarVersion ?: 0
        )
        val cv = android.content.ContentValues().apply {
            put("owner_user_id", databaseHelper.currentOwnerId())
            put("request_id", "fr_${fromUserId}_${toUserId}_${System.currentTimeMillis()}")
            put("from_user_id", fromUserId)
            put("to_user_id", toUserId)
            put("from_nickname", e.fromNickname ?: fromUserId)
            put("message", e.requestMessage ?: "我是$fromUserId")
            put("status", 0)
            put("create_time", e.createTime ?: System.currentTimeMillis())
        }
        databaseHelper.writableDatabase.insert("friend_request", null, cv)
        AppEvents.notifyFriendRequestChanged()
    }

    private fun handleFriendAccepted(e: SyncEvent) {
        val fromUserId = e.fromUserId ?: return
        val toUserId = e.toUserId ?: return
        userDao.addFriend(fromUserId, toUserId)
        userDao.addFriend(toUserId, fromUserId)
        val currentUserId = userSession.currentUserId ?: return

        val fromName = e.fromNickname ?: fromUserId
        val toName = e.toNickname ?: toUserId
        val fromAvatarUrl = e.fromAvatarUrl ?: ""
        val fromAvatarVersion = e.fromAvatarVersion ?: 0
        val toAvatarUrl = e.toAvatarUrl ?: ""
        val toAvatarVersion = e.toAvatarVersion ?: 0
        upsertUserProfile(fromUserId, fromName, fromAvatarUrl, fromAvatarVersion)
        upsertUserProfile(toUserId, toName, toAvatarUrl, toAvatarVersion)

        val targetId = if (currentUserId == fromUserId) toUserId else fromUserId
        val targetName = if (targetId == fromUserId) fromName else toName
        val targetAvatarUrl = if (targetId == fromUserId) fromAvatarUrl else toAvatarUrl
        val targetAvatarVersion = if (targetId == fromUserId) fromAvatarVersion else toAvatarVersion
        val targetUser = userDao.getById(targetId)
        val targetAvatar = targetUser?.avatar.orEmpty()
        val convId = ConversationId.single(fromUserId, toUserId)
        val existingConv = conversationDao.getById(convId)
        if (existingConv == null) {
            conversationDao.insert(
                Conversation(
                    conversationId = convId,
                    type = ConversationType.SINGLE,
                    targetId = targetId,
                    title = targetName,
                    avatar = targetAvatar,
                    avatarUrl = targetAvatarUrl,
                    avatarVersion = targetAvatarVersion,
                    lastMessageContent = "",
                    lastMessageTime = System.currentTimeMillis()
                )
            )
        } else {
            conversationDao.updateDisplayInfo(convId, targetName, targetAvatar, targetAvatarUrl, targetAvatarVersion)
        }
        // Mark friend request as accepted
        val db = databaseHelper.writableDatabase
        val cv = android.content.ContentValues().apply { put("status", 1) }
        db.update("friend_request", cv, "owner_user_id = ? AND from_user_id = ? AND to_user_id = ?",
            arrayOf(databaseHelper.currentOwnerId(), fromUserId, toUserId))
        AppEvents.notifyConversationChanged(convId)
        AppEvents.notifyFriendRequestChanged()
    }

    private fun upsertUserProfile(userId: String, nickname: String, avatarUrl: String, avatarVersion: Int) {
        userDao.upsertPreservingExisting(User(userId = userId, nickname = nickname, avatarUrl = avatarUrl, avatarVersion = avatarVersion))
    }

    private fun memberDisplayName(userId: String): String {
        if (userId.isBlank()) return "有人"
        return userDao.getById(userId)?.nickname?.takeIf { it.isNotBlank() && it != userId } ?: userId
    }

    private fun insertSystemMessage(
        conversationId: String,
        messageId: String,
        content: String,
        createTime: Long
    ) {
        if (messageDao.getById(messageId) != null) return
        val msg = Message(
            messageId = messageId,
            conversationId = conversationId,
            senderId = "system",
            messageType = MessageType.SYSTEM,
            content = content,
            status = MessageStatus.SENT,
            sendTime = createTime,
            createTime = createTime
        )
        messageDao.insert(msg)
        conversationDao.updateLastMessage(conversationId, messageId, content, createTime)
        AppEvents.notifyMessageChanged(conversationId)
    }

    private fun handleUserUpdate(e: SyncEvent) {
        val userId = e.senderId ?: return
        val existing = userDao.getById(userId)
        val updated = (existing ?: User(userId = userId, nickname = userId)).copy(
            nickname = e.nickname
                ?.takeIf { it.isNotBlank() && (it != userId || existing?.nickname.isNullOrBlank() || existing?.nickname == userId) }
                ?: existing?.nickname
                ?: userId,
            avatar = e.avatar ?: existing?.avatar ?: "",
            avatarUrl = e.avatarUrl ?: existing?.avatarUrl ?: "",
            avatarVersion = e.avatarVersion ?: existing?.avatarVersion ?: 0,
            signature = e.signature ?: existing?.signature ?: "",
            region = e.region ?: existing?.region ?: ""
        )
        userDao.upsertPreservingExisting(updated)
        groupDao.updateMemberAvatar(userId, updated.avatar, updated.avatarUrl, updated.avatarVersion)
        AppEvents.notifyUserChanged(userId)
    }

    private fun handleConversationSettingsUpdated(e: SyncEvent) {
        val convId = e.conversationId ?: return
        val isPinned = e.isPinned ?: return
        val pinnedTime = e.pinnedTime ?: 0L
        val mute = e.mute ?: false
        conversationDao.setPinned(convId, isPinned, pinnedTime)
        conversationDao.setMute(convId, mute)
        AppEvents.notifyConversationChanged(convId)
    }
}

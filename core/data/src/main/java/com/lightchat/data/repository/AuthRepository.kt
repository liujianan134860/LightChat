package com.lightchat.data.repository

import com.lightchat.data.local.TokenManager
import com.lightchat.data.local.UserSession
import com.lightchat.data.local.dao.ConversationDao
import com.lightchat.data.local.dao.GroupDao
import com.lightchat.data.local.dao.MessageDao
import com.lightchat.data.local.dao.SyncStateDao
import com.lightchat.data.local.dao.UserDao
import com.lightchat.data.remote.AuthApiClient
import com.lightchat.model.Conversation
import com.lightchat.model.ConversationId
import com.lightchat.model.ConversationType
import com.lightchat.model.GroupMember
import com.lightchat.model.ImGroup
import com.lightchat.model.MemberRole
import com.lightchat.model.Message
import com.lightchat.model.MessageStatus
import com.lightchat.model.MessageType
import com.lightchat.model.User
import com.lightchat.domain.repository.AuthRepositoryContract
import org.json.JSONObject

class AuthRepository(
    private val userDao: UserDao,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val groupDao: GroupDao,
    private val syncStateDao: SyncStateDao,
    private val tokenManager: TokenManager,
    private val userSession: UserSession,
    private val authApiClient: AuthApiClient = AuthApiClient()
) : AuthRepositoryContract {
    override fun login(username: String, password: String): Result<User> {
        if (username.isBlank() || password.isBlank()) {
            return Result.failure(Exception("账户和密码不能为空"))
        }

        return runCatching {
            val response = authApiClient.login(username.trim(), password)
            saveAuthenticatedUser(response.token, response.user)
            response.user
        }
    }

    override fun register(username: String, password: String, nickname: String): Result<User> {
        if (username.isBlank() || password.isBlank() || nickname.isBlank()) {
            return Result.failure(Exception("所有字段不能为空"))
        }

        return runCatching {
            val response = authApiClient.register(username.trim(), password, nickname.trim())
            saveAuthenticatedUser(response.token, response.user)
            response.user
        }
    }

    override fun logout() {
        tokenManager.clearToken()
        userSession.clear()
    }

    override fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn() && userSession.isLoggedIn()
    }

    override fun getCurrentUserId(): String? = userSession.currentUserId

    private fun saveAuthenticatedUser(token: String, user: User) {
        userSession.currentUserId = user.userId
        userSession.currentNickname = user.nickname
        tokenManager.saveToken(token)
        userDao.insert(user)
        if (needsBootstrap()) {
            runCatching { cacheBootstrap(token) }
        }
    }

    private fun needsBootstrap(): Boolean {
        val lastUserSeq = syncStateDao.getLastUserSeq()
        val visibleConversationCount = conversationDao.getVisibleCount()
        return lastUserSeq <= 0L || visibleConversationCount == 0
    }

    private fun cacheBootstrap(token: String) {
        val userId = userSession.currentUserId ?: return
        val root = authApiClient.bootstrap(token)
        val conversationSettings = mutableMapOf<String, BootstrapConversationSettings>()
        root.optJSONObject("conversationSettings")?.let { settingsObj ->
            settingsObj.keys().forEach { convId ->
                val s = settingsObj.optJSONObject(convId) ?: JSONObject()
                conversationSettings[convId] = BootstrapConversationSettings(
                    isPinned = s.optBoolean("isPinned", false),
                    pinnedTime = s.optLong("pinnedTime", 0),
                    mute = s.optBoolean("mute", false)
                )
            }
        }

        root.optJSONArray("users")?.let { users ->
            for (i in 0 until users.length()) {
                userDao.upsertPreservingExisting(users.getJSONObject(i).toClientUser())
            }
        }

        root.optJSONArray("friends")?.let { friends ->
            for (i in 0 until friends.length()) {
                userDao.addFriend(userId, friends.getString(i))
            }
        }

        root.optJSONArray("groups")?.let { groups ->
            for (i in 0 until groups.length()) {
                val obj = groups.getJSONObject(i)
                val group = ImGroup(
                    groupId = obj.getString("groupId"),
                    groupName = obj.optString("groupName", obj.getString("groupId")),
                    avatar = obj.optString("avatar", ""),
                    avatarUrl = obj.optString("avatarUrl", ""),
                    avatarVersion = obj.optInt("avatarVersion", 0),
                    ownerId = obj.optString("ownerId", ""),
                    memberCount = obj.optJSONArray("members")?.length() ?: 0,
                    createTime = obj.optLong("createdAt", System.currentTimeMillis())
                )
                groupDao.insertGroup(group)
                obj.optJSONArray("membersInfo")?.let { members ->
                    for (m in 0 until members.length()) {
                        val member = members.getJSONObject(m)
                        groupDao.insertMember(
                            GroupMember(
                                groupId = group.groupId,
                                userId = member.getString("userId"),
                                nickname = member.optString("nickname", member.getString("userId")),
                                avatar = member.optString("avatar", ""),
                                avatarUrl = member.optString("avatarUrl", ""),
                                avatarVersion = member.optInt("avatarVersion", 0),
                                role = if (member.optInt("role", 1) == 0) MemberRole.OWNER else MemberRole.MEMBER,
                                joinTime = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }

        root.optJSONArray("messages")?.let { messages ->
            for (i in 0 until messages.length()) {
                val msgObj = messages.getJSONObject(i)
                val msgId = msgObj.getString("messageId")
                val existing = messageDao.getById(msgId)
                if (existing?.isDeleted == true) continue
                val msg = msgObj.toClientMessage()
                messageDao.insert(msg)
            }
        }

        root.optJSONArray("conversations")?.let { conversations ->
            for (i in 0 until conversations.length()) {
                val obj = conversations.getJSONObject(i)
                val conversationId = obj.getString("conversationId")
                val type = obj.optString("type", "SINGLE")
                val groupId = obj.optString("groupId", "")
                val targetId = if (type == "GROUP") {
                    groupId
                } else {
                    val participants = obj.optJSONArray("participants")
                    (0 until (participants?.length() ?: 0))
                        .mapNotNull { participants?.getString(it) }
                        .firstOrNull { it != userId }
                        ?: conversationId.removePrefix("single_").split("_").firstOrNull { it != userId }.orEmpty()
                }
                val lastMessage = messageDao.getMessagesByConversation(conversationId, 1).lastOrNull()
                val title = if (type == "GROUP") {
                    groupDao.getGroupById(groupId)?.groupName ?: "群聊"
                } else {
                    userDao.getById(targetId)?.nickname ?: targetId
                }
                val convUser = userDao.getById(targetId)
                val convGroup = if (type == "GROUP") groupDao.getGroupById(groupId) else null
                val resolvedConversationId = conversationId.ifBlank {
                    if (type == "GROUP") ConversationId.group(groupId) else ConversationId.single(userId, targetId)
                }
                val settings = conversationSettings[resolvedConversationId]
                conversationDao.insert(
                    Conversation(
                        conversationId = resolvedConversationId,
                        type = if (type == "GROUP") ConversationType.GROUP else ConversationType.SINGLE,
                        targetId = targetId,
                        title = title,
                        avatar = if (type == "GROUP") convGroup?.avatar.orEmpty() else convUser?.avatar.orEmpty(),
                        avatarUrl = if (type == "GROUP") convGroup?.avatarUrl.orEmpty() else convUser?.avatarUrl.orEmpty(),
                        avatarVersion = if (type == "GROUP") convGroup?.avatarVersion ?: 0 else convUser?.avatarVersion ?: 0,
                        lastMessageId = lastMessage?.messageId,
                        lastMessageContent = lastMessage?.let { displayMessageContent(it) }.orEmpty(),
                        lastMessageTime = lastMessage?.createTime ?: obj.optLong("createdAt", System.currentTimeMillis()),
                        isPinned = settings?.isPinned ?: false,
                        pinnedTime = settings?.pinnedTime ?: 0,
                        mute = settings?.mute ?: false
                    )
                )
            }
        }

        // Apply per-user conversation settings (pin/mute) from server bootstrap
        conversationSettings.forEach { (convId, settings) ->
            conversationDao.setPinned(convId, settings.isPinned, settings.pinnedTime)
            conversationDao.setMute(convId, settings.mute)
        }

        val maxUserSeq = root.optLong("maxUserSeq", 0L)
        if (maxUserSeq > 0L) {
            syncStateDao.setLastUserSeq(maxUserSeq)
        }
    }

    private fun JSONObject.toClientUser(): User = User(
        userId = getString("userId"),
        nickname = optString("nickname", getString("userId")),
        avatar = optString("avatar", ""),
        avatarUrl = optString("avatarUrl", ""),
        avatarVersion = optInt("avatarVersion", 0),
        signature = optString("signature", ""),
        region = optString("region", "")
    )

    private fun JSONObject.toClientMessage(): Message = Message(
        messageId = getString("messageId"),
        conversationId = getString("conversationId"),
        senderId = getString("senderId"),
        receiverId = optString("receiverId", null),
        groupId = optString("groupId", null),
        messageType = MessageType.fromInt(optInt("messageType", 0)),
        content = optString("content", ""),
        status = MessageStatus.DELIVERED,
        clientSeq = optLong("clientSeq", 0),
        conversationSeq = optLong("conversationSeq", 0),
        userSeq = optLong("userSeq", 0),
        sendTime = optLong("sendTime", System.currentTimeMillis()),
        createTime = optLong("createTime", System.currentTimeMillis()),
        extra = optString("extra", null)
    )

    private fun displayMessageContent(message: Message): String = when (message.messageType) {
        MessageType.IMAGE -> "[图片]"
        MessageType.USER_CARD -> "[名片]${message.content}"
        MessageType.GROUP_CARD -> "[群名片]${message.content}"
        MessageType.MERGE_FORWARD -> "[聊天记录]"
        else -> message.content
    }

    private data class BootstrapConversationSettings(
        val isPinned: Boolean,
        val pinnedTime: Long,
        val mute: Boolean
    )
}

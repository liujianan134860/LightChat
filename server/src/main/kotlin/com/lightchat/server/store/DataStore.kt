package com.lightchat.server.store

import com.lightchat.server.model.*
import com.lightchat.server.security.JwtClaims
import com.lightchat.server.security.PasswordHasher
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class ServerConversationSettings(
    val isPinned: Boolean = false,
    val pinnedTime: Long = 0,
    val mute: Boolean = false
)

class DataStore {
    companion object {
        private const val LIGHTCHAT_ASSISTANT_ID = "lightchat_assistant"
        private const val LIGHTCHAT_ASSISTANT_NAME = "LightChat助手"
        private const val LIGHTCHAT_ASSISTANT_AVATAR = "lightchat://app-icon"
        private const val LIGHTCHAT_WELCOME_MESSAGE = "欢迎使用LightChat!"
    }

    private val users = ConcurrentHashMap<String, ServerUser>()
    private val messages = ConcurrentHashMap<String, ServerMessage>()
    private val conversations = ConcurrentHashMap<String, ServerConversation>()
    private val groups = ConcurrentHashMap<String, ServerGroup>()
    private val friendships = ConcurrentHashMap<String, MutableSet<String>>()
    private val credentials = ConcurrentHashMap<String, String>()
    private val authSessions = ConcurrentHashMap<String, JSONObject>()
    val friendRequests = CopyOnWriteArrayList<org.json.JSONObject>()
    private val conversationSettings = ConcurrentHashMap<String, ConcurrentHashMap<String, ServerConversationSettings>>()
    var onChanged: (() -> Unit)? = null

    fun getOrCreateUser(userId: String, nickname: String): ServerUser {
        return users.computeIfAbsent(userId) {
            ServerUser(userId = userId, nickname = nickname)
        }
    }

    fun registerUser(userId: String, password: String, nickname: String): Result<ServerUser> {
        if (users.containsKey(userId)) {
            return Result.failure(IllegalArgumentException("用户已存在"))
        }
        val user = ServerUser(userId = userId, nickname = nickname)
        users[userId] = user
        credentials[userId] = PasswordHasher.hash(password)
        createWelcomeConversation(userId)
        onChanged?.invoke()
        return Result.success(user)
    }

    private fun createWelcomeConversation(userId: String) {
        users.putIfAbsent(
            LIGHTCHAT_ASSISTANT_ID,
            ServerUser(
                userId = LIGHTCHAT_ASSISTANT_ID,
                nickname = LIGHTCHAT_ASSISTANT_NAME,
                avatar = LIGHTCHAT_ASSISTANT_AVATAR
            )
        )
        val conversationId = listOf(LIGHTCHAT_ASSISTANT_ID, userId)
            .sorted()
            .joinToString(separator = "_", prefix = "single_")
        conversations[conversationId] = ServerConversation(
            conversationId = conversationId,
            participants = ConcurrentHashMap.newKeySet<String>().apply {
                add(LIGHTCHAT_ASSISTANT_ID)
                add(userId)
            }
        )
        val messageId = UUID.randomUUID().toString()
        messages[messageId] = ServerMessage(
            messageId = messageId,
            conversationId = conversationId,
            senderId = LIGHTCHAT_ASSISTANT_ID,
            receiverId = userId,
            messageType = 0,
            content = LIGHTCHAT_WELCOME_MESSAGE,
            conversationSeq = 0
        )
    }

    fun loginUser(userId: String, password: String): Result<ServerUser> {
        val user = users[userId] ?: return Result.failure(NoSuchElementException("用户不存在"))
        val savedPassword = credentials[userId]
        if (!PasswordHasher.verify(password, savedPassword)) {
            return Result.failure(IllegalArgumentException("密码错误"))
        }
        if (PasswordHasher.needsUpgrade(savedPassword)) {
            credentials[userId] = PasswordHasher.hash(password)
            onChanged?.invoke()
        }
        return Result.success(user)
    }

    fun saveAuthSession(claims: JwtClaims, deviceName: String = "", clientIp: String = "") {
        authSessions[claims.tokenId] = JSONObject().apply {
            put("tokenId", claims.tokenId)
            put("userId", claims.userId)
            put("issuedAt", claims.issuedAt)
            put("expiresAt", claims.expiresAt)
            put("revokedAt", 0L)
            put("deviceName", deviceName)
            put("clientIp", clientIp)
            put("lastSeenAt", System.currentTimeMillis())
        }
        onChanged?.invoke()
    }

    fun isAuthSessionActive(tokenId: String, userId: String): Boolean {
        val session = authSessions[tokenId] ?: return false
        if (session.optString("userId") != userId) return false
        if (session.optLong("revokedAt", 0L) > 0L) return false
        val now = System.currentTimeMillis() / 1000
        return session.optLong("expiresAt", 0L) > now
    }

    fun touchAuthSession(tokenId: String) {
        val session = authSessions[tokenId] ?: return
        session.put("lastSeenAt", System.currentTimeMillis())
        onChanged?.invoke()
    }

    fun getUser(userId: String): ServerUser? = users[userId]
    fun userExists(userId: String): Boolean = users.containsKey(userId)
    fun getUserCount(): Int = users.size

    fun searchUsers(query: String, limit: Int = 30): List<ServerUser> {
        val keyword = query.trim()
        if (keyword.isBlank()) return users.values.sortedBy { it.userId }.take(limit)
        return users.values
            .filter {
                it.userId.contains(keyword, ignoreCase = true) ||
                    it.nickname.contains(keyword, ignoreCase = true)
            }
            .sortedBy { it.userId }
            .take(limit)
    }

    fun saveMessage(msg: ServerMessage) {
        messages[msg.messageId] = msg
        onChanged?.invoke()
    }

    fun getMessage(messageId: String): ServerMessage? = messages[messageId]

    fun updateMessageExtra(messageId: String, extra: String): Boolean {
        val existing = messages[messageId] ?: return false
        messages[messageId] = existing.copy(extra = extra)
        onChanged?.invoke()
        return true
    }

    fun getOrCreateConversation(convId: String, type: String): ServerConversation {
        return conversations.computeIfAbsent(convId) {
            ServerConversation(conversationId = convId, type = type)
        }
    }

    fun getConversation(convId: String): ServerConversation? = conversations[convId]

    fun addParticipant(convId: String, userId: String) {
        val conv = getOrCreateConversation(convId, "SINGLE")
        conv.participants.add(userId)
        onChanged?.invoke()
    }

    fun getParticipants(convId: String): Set<String> {
        return conversations[convId]?.participants ?: emptySet()
    }

    fun getConversationSettings(userId: String, conversationId: String): ServerConversationSettings {
        return conversationSettings[userId]?.get(conversationId) ?: ServerConversationSettings()
    }

    fun setConversationSettings(userId: String, conversationId: String, settings: ServerConversationSettings) {
        conversationSettings.computeIfAbsent(userId) { ConcurrentHashMap() }[conversationId] = settings
        onChanged?.invoke()
    }

    fun createGroup(groupId: String, name: String, ownerId: String, memberIds: List<String>): ServerGroup {
        val group = ServerGroup(
            groupId = groupId,
            groupName = name,
            ownerId = ownerId
        )
        group.members.add(ownerId)
        group.members.addAll(memberIds)
        groups[groupId] = group
        conversations["group_$groupId"] = ServerConversation(
            conversationId = "group_$groupId",
            type = "GROUP",
            participants = ConcurrentHashMap.newKeySet<String>().apply { addAll(group.members) },
            groupId = groupId
        )
        onChanged?.invoke()
        return group
    }

    fun getGroup(groupId: String): ServerGroup? = groups[groupId]
    fun getGroupMembers(groupId: String): Set<String> = groups[groupId]?.members ?: emptySet()
    fun getGroupCount(): Int = groups.size

    fun addGroupMembers(groupId: String, memberIds: List<String>): List<String> {
        val group = groups[groupId] ?: return emptyList()
        val existing = group.members.toSet()
        val added = memberIds
            .distinct()
            .filter { users.containsKey(it) && it !in existing }
        if (added.isEmpty()) return emptyList()

        group.members.addAll(added)
        val conv = conversations.computeIfAbsent("group_$groupId") {
            ServerConversation(
                conversationId = "group_$groupId",
                type = "GROUP",
                groupId = groupId
            )
        }
        conv.participants.addAll(group.members)
        onChanged?.invoke()
        return added
    }

    fun addFriendship(userA: String, userB: String) {
        friendships.getOrPut(userA) { ConcurrentHashMap.newKeySet() }.add(userB)
        friendships.getOrPut(userB) { ConcurrentHashMap.newKeySet() }.add(userA)
        onChanged?.invoke()
    }

    fun areFriends(userA: String, userB: String): Boolean {
        return friendships[userA]?.contains(userB) == true
    }

    fun getFriends(userId: String): Set<String> = friendships[userId] ?: emptySet()

    fun buildBootstrapJson(userId: String): JSONObject {
        val friendIds = getFriends(userId)
        val userGroups = groups.values.filter { group -> userId in group.members }
        val visibleConversationIds = conversations.values
            .filter { conv -> userId in conv.participants || conv.groupId?.let { gid -> userId in getGroupMembers(gid) } == true }
            .map { it.conversationId }
            .toSet()
        val visibleConversationUserIds = conversations.values
            .filter { it.conversationId in visibleConversationIds }
            .flatMap { it.participants }
        val visibleMessages = messages.values
            .filter { it.conversationId in visibleConversationIds }
            .sortedByDescending { it.createTime }

        return JSONObject().apply {
            put("users", JSONArray().apply {
                (friendIds + userId + userGroups.flatMap { it.members } + visibleConversationUserIds).distinct().forEach { id ->
                    users[id]?.let { put(it.toJson()) }
                }
            })
            put("friends", JSONArray().apply { friendIds.forEach { put(it) } })
            put("groups", JSONArray().apply {
                userGroups.forEach { group ->
                    put(group.toJson().apply {
                        put("membersInfo", JSONArray().apply {
                            group.members.forEach { memberId ->
                                val user = users[memberId]
                                put(JSONObject().apply {
                                    put("userId", memberId)
                                    put("nickname", user?.nickname ?: memberId)
                                    put("avatar", user?.avatar ?: "")
                                    put("avatarUrl", user?.avatarUrl ?: "")
                                    put("avatarVersion", user?.avatarVersion ?: 0)
                                    put("role", if (memberId == group.ownerId) 0 else 1)
                                })
                            }
                        })
                    })
                }
            })
            put("conversations", JSONArray().apply {
                conversations.values
                    .filter { it.conversationId in visibleConversationIds }
                    .forEach { put(it.toJson()) }
            })
            put("messages", JSONArray().apply {
                visibleMessages.forEach { put(it.toJson()) }
            })
            put("conversationSettings", JSONObject().apply {
                conversationSettings[userId]?.forEach { (convId, settings) ->
                    put(convId, JSONObject().apply {
                        put("isPinned", settings.isPinned)
                        put("pinnedTime", settings.pinnedTime)
                        put("mute", settings.mute)
                    })
                }
            })
        }
    }

    fun saveFriendRequest(fromUserId: String, toUserId: String, message: String, fromNickname: String): org.json.JSONObject {
        val request = org.json.JSONObject().apply {
            put("fromUserId", fromUserId)
            put("toUserId", toUserId)
            put("message", message)
            put("fromNickname", fromNickname)
            put("status", 0)
            put("createTime", System.currentTimeMillis())
        }
        friendRequests.add(request)
        onChanged?.invoke()
        return request
    }

    fun getPendingFriendRequest(fromUserId: String, toUserId: String): org.json.JSONObject? {
        return friendRequests.find {
            it.optString("fromUserId") == fromUserId &&
            it.optString("toUserId") == toUserId &&
            it.optInt("status", 0) == 0
        }
    }

    fun updateFriendRequestStatus(fromUserId: String, toUserId: String, status: Int): Boolean {
        val request = friendRequests.find {
            it.optString("fromUserId") == fromUserId &&
                it.optString("toUserId") == toUserId &&
                it.optInt("status", 0) == 0
        } ?: return false
        request.put("status", status)
        onChanged?.invoke()
        return true
    }

    fun updateUser(userId: String, nickname: String?, avatar: String?, avatarUrl: String?, avatarVersion: Int?, signature: String?, region: String?) {
        val existing = users[userId] ?: return
        users[userId] = existing.copy(
            nickname = nickname ?: existing.nickname,
            avatar = avatar ?: existing.avatar,
            avatarUrl = avatarUrl ?: existing.avatarUrl,
            avatarVersion = avatarVersion ?: existing.avatarVersion,
            signature = signature ?: existing.signature,
            region = region ?: existing.region
        )
        onChanged?.invoke()
    }

    @Synchronized
    fun saveToFile(file: File, eventService: EventService) {
        val root = toJson(eventService)
        file.parentFile?.mkdirs()
        val tempFile = File(file.parentFile ?: File("."), "${file.name}.tmp")
        tempFile.writeText(root.toString(), Charsets.UTF_8)
        if (file.exists()) file.delete()
        tempFile.renameTo(file)
    }

    @Synchronized
    fun loadFromFile(file: File, eventService: EventService): Boolean {
        if (!file.exists()) return false
        loadFromJson(JSONObject(file.readText(Charsets.UTF_8)), eventService)
        return true
    }

    @Synchronized
    fun toJson(eventService: EventService): JSONObject {
        return JSONObject().apply {
            put("users", JSONArray().apply { users.values.forEach { put(it.toJson()) } })
            put("credentials", JSONObject().apply { credentials.forEach { (userId, password) -> put(userId, password) } })
            put("authSessions", JSONArray().apply { authSessions.values.forEach { put(JSONObject(it.toString())) } })
            put("messages", JSONArray().apply { messages.values.forEach { put(it.toJson()) } })
            put("conversations", JSONArray().apply { conversations.values.forEach { put(it.toJson()) } })
            put("groups", JSONArray().apply { groups.values.forEach { put(it.toJson()) } })
            put("friendships", JSONObject().apply {
                friendships.forEach { (userId, friendIds) ->
                    put(userId, JSONArray().apply { friendIds.forEach { put(it) } })
                }
            })
            put("friendRequests", JSONArray().apply { friendRequests.forEach { put(JSONObject(it.toString())) } })
            put("conversationSettings", JSONObject().apply {
                conversationSettings.forEach { (userId, settings) ->
                    put(userId, JSONObject().apply {
                        settings.forEach { (convId, s) ->
                            put(convId, JSONObject().apply {
                                put("isPinned", s.isPinned)
                                put("pinnedTime", s.pinnedTime)
                                put("mute", s.mute)
                            })
                        }
                    })
                }
            })
            put("events", eventService.toJson())
        }
    }

    @Synchronized
    fun loadFromJson(root: JSONObject, eventService: EventService) {
        users.clear()
        messages.clear()
        conversations.clear()
        groups.clear()
        friendships.clear()
        credentials.clear()
        authSessions.clear()
        friendRequests.clear()

        root.optJSONArray("users")?.forEachObject { obj ->
            users[obj.getString("userId")] = obj.toServerUser()
        }
        root.optJSONObject("credentials")?.let { obj ->
            obj.keys().forEach { key -> credentials[key] = obj.optString(key) }
        }
        root.optJSONArray("authSessions")?.forEachObject { obj ->
            val tokenId = obj.optString("tokenId")
            if (tokenId.isNotBlank()) authSessions[tokenId] = JSONObject(obj.toString())
        }
        root.optJSONArray("messages")?.forEachObject { obj ->
            val msg = obj.toServerMessage()
            messages[msg.messageId] = msg
        }
        root.optJSONArray("conversations")?.forEachObject { obj ->
            val conv = obj.toServerConversation()
            conversations[conv.conversationId] = conv
        }
        root.optJSONArray("groups")?.forEachObject { obj ->
            val group = obj.toServerGroup()
            groups[group.groupId] = group
        }
        root.optJSONObject("friendships")?.let { obj ->
            obj.keys().forEach { userId ->
                friendships[userId] = ConcurrentHashMap.newKeySet<String>().apply {
                    obj.optJSONArray(userId)?.forEachString { add(it) }
                }
            }
        }
        root.optJSONArray("friendRequests")?.forEachObject { obj ->
            friendRequests.add(JSONObject(obj.toString()))
        }
        root.optJSONObject("conversationSettings")?.let { allSettings ->
            allSettings.keys().forEach { userId ->
                val userSettings = ConcurrentHashMap<String, ServerConversationSettings>()
                allSettings.optJSONObject(userId)?.let { settingsObj ->
                    settingsObj.keys().forEach { convId ->
                        val s = settingsObj.optJSONObject(convId) ?: JSONObject()
                        userSettings[convId] = ServerConversationSettings(
                            isPinned = s.optBoolean("isPinned", false),
                            pinnedTime = s.optLong("pinnedTime", 0),
                            mute = s.optBoolean("mute", false)
                        )
                    }
                }
                conversationSettings[userId] = userSettings
            }
        }
        eventService.loadFromJson(root.optJSONObject("events") ?: JSONObject())
    }

    fun loadSeedData() {
        val seedUsers = listOf(
            "u1002" to "张三", "u1003" to "李四", "u1004" to "王五",
            "u1005" to "赵六", "u1006" to "孙七", "u1007" to "周八",
            "u1008" to "吴九", "u1009" to "郑十"
        )
        seedUsers.forEach { (id, name) -> users[id] = ServerUser(userId = id, nickname = name) }

        // Bidirectional friendships between all seed users
        for (i in seedUsers.indices) {
            for (j in i + 1 until seedUsers.size) {
                addFriendship(seedUsers[i].first, seedUsers[j].first)
            }
        }

        // Groups
        createGroup("g2001", "Tech Chat", "u1002", listOf("u1002", "u1003", "u1004", "u1005"))
        createGroup("g2002", "Family", "u1006", listOf("u1006", "u1007", "u1002"))

        // Group conversations
        conversations["group_g2001"] = ServerConversation(
            conversationId = "group_g2001",
            type = "GROUP",
            participants = ConcurrentHashMap.newKeySet<String>().apply {
                addAll(listOf("u1002", "u1003", "u1004", "u1005"))
            },
            groupId = "g2001"
        )
        conversations["group_g2002"] = ServerConversation(
            conversationId = "group_g2002",
            type = "GROUP",
            participants = ConcurrentHashMap.newKeySet<String>().apply {
                addAll(listOf("u1006", "u1007", "u1002"))
            },
            groupId = "g2002"
        )

        println("Seed data loaded: ${users.size} users, ${groups.size} groups, ${conversations.size} conversations")
    }

    private fun ServerUser.toJson() = JSONObject().apply {
        put("userId", userId)
        put("nickname", nickname)
        put("avatar", avatar)
        put("avatarUrl", avatarUrl)
        put("avatarVersion", avatarVersion)
        put("signature", signature)
        put("region", region)
        put("createdAt", createdAt)
    }

    private fun JSONObject.toServerUser() = ServerUser(
        userId = getString("userId"),
        nickname = optString("nickname", getString("userId")),
        avatar = optString("avatar", ""),
        avatarUrl = optString("avatarUrl", ""),
        avatarVersion = optInt("avatarVersion", 0),
        signature = optString("signature", ""),
        region = optString("region", ""),
        createdAt = optLong("createdAt", System.currentTimeMillis())
    )

    private fun ServerMessage.toJson() = JSONObject().apply {
        put("messageId", messageId)
        put("conversationId", conversationId)
        put("senderId", senderId)
        if (receiverId != null) put("receiverId", receiverId)
        if (groupId != null) put("groupId", groupId)
        put("messageType", messageType)
        put("content", content)
        put("clientSeq", clientSeq)
        put("conversationSeq", conversationSeq)
        put("sendTime", sendTime)
        put("createTime", createTime)
        if (extra != null) put("extra", extra)
    }

    private fun JSONObject.toServerMessage() = ServerMessage(
        messageId = getString("messageId"),
        conversationId = getString("conversationId"),
        senderId = getString("senderId"),
        receiverId = optStringOrNull("receiverId"),
        groupId = optStringOrNull("groupId"),
        messageType = optInt("messageType"),
        content = optString("content", ""),
        clientSeq = optLong("clientSeq", 0),
        conversationSeq = optLong("conversationSeq", 0),
        sendTime = optLong("sendTime", System.currentTimeMillis()),
        createTime = optLong("createTime", System.currentTimeMillis()),
        extra = optStringOrNull("extra")
    )

    private fun ServerConversation.toJson() = JSONObject().apply {
        put("conversationId", conversationId)
        put("type", type)
        put("participants", JSONArray().apply { participants.forEach { put(it) } })
        if (groupId != null) put("groupId", groupId)
        put("createdAt", createdAt)
    }

    private fun JSONObject.toServerConversation() = ServerConversation(
        conversationId = getString("conversationId"),
        type = optString("type", "SINGLE"),
        participants = ConcurrentHashMap.newKeySet<String>().apply {
            optJSONArray("participants")?.forEachString { add(it) }
        },
        groupId = optStringOrNull("groupId"),
        createdAt = optLong("createdAt", System.currentTimeMillis())
    )

    private fun ServerGroup.toJson() = JSONObject().apply {
        put("groupId", groupId)
        put("groupName", groupName)
        put("ownerId", ownerId)
        put("members", JSONArray().apply { members.forEach { put(it) } })
        put("createdAt", createdAt)
    }

    private fun JSONObject.toServerGroup() = ServerGroup(
        groupId = getString("groupId"),
        groupName = optString("groupName", getString("groupId")),
        ownerId = optString("ownerId", ""),
        members = ConcurrentHashMap.newKeySet<String>().apply {
            optJSONArray("members")?.forEachString { add(it) }
        },
        createdAt = optLong("createdAt", System.currentTimeMillis())
    )

    private fun JSONObject.optStringOrNull(key: String): String? {
        return if (has(key) && !isNull(key)) optString(key) else null
    }

    private fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (i in 0 until length()) block(getJSONObject(i))
    }

    private fun JSONArray.forEachString(block: (String) -> Unit) {
        for (i in 0 until length()) block(getString(i))
    }

}

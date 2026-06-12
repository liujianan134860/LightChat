package com.lightchat.server.store

import org.json.JSONArray
import org.json.JSONObject
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement

class MySqlStatePersistence(
    private val jdbcUrl: String,
    private val user: String,
    private val password: String
) : StatePersistence {
    init {
        Class.forName("com.mysql.cj.jdbc.Driver")
        connection().use { conn ->
            createMirrorTables(conn)
        }
    }

    override fun load(): JSONObject? {
        connection().use { conn ->
            return loadStructuredState(conn)
        }
    }

    override fun save(root: JSONObject) {
        connection().use { conn ->
            conn.autoCommit = false
            try {
                refreshMirrorTables(conn, root)
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    override fun describe(): String = jdbcUrl


    private fun createMirrorTables(conn: Connection) {
        conn.createStatement().use { statement ->
            // Migration: add avatar_url/avatar_version columns if missing (2026-06-10)
            try {
                statement.executeUpdate("ALTER TABLE users ADD COLUMN avatar_url VARCHAR(1024) DEFAULT ''")
            } catch (_: Exception) {}
            try {
                statement.executeUpdate("ALTER TABLE users ADD COLUMN avatar_version INT DEFAULT 0")
            } catch (_: Exception) {}

            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS users (
                    user_id VARCHAR(64) PRIMARY KEY,
                    nickname VARCHAR(255) NOT NULL,
                    avatar MEDIUMTEXT NULL,
                    avatar_url VARCHAR(1024) DEFAULT '',
                    avatar_version INT DEFAULT 0,
                    signature VARCHAR(1024) NULL,
                    region VARCHAR(255) NULL,
                    created_at BIGINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS credentials (
                    user_id VARCHAR(64) PRIMARY KEY,
                    password VARCHAR(255) NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS auth_sessions (
                    token_id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    issued_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    revoked_at BIGINT NOT NULL DEFAULT 0,
                    device_name VARCHAR(128) DEFAULT '',
                    client_ip VARCHAR(64) DEFAULT '',
                    last_seen_at BIGINT NOT NULL DEFAULT 0,
                    INDEX idx_auth_sessions_user (user_id),
                    INDEX idx_auth_sessions_exp (expires_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS friendships (
                    user_id VARCHAR(64) NOT NULL,
                    friend_id VARCHAR(64) NOT NULL,
                    PRIMARY KEY (user_id, friend_id),
                    INDEX idx_friendships_friend (friend_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS conversations (
                    conversation_id VARCHAR(128) PRIMARY KEY,
                    type VARCHAR(32) NOT NULL,
                    group_id VARCHAR(64) NULL,
                    created_at BIGINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS conversation_participants (
                    conversation_id VARCHAR(128) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    PRIMARY KEY (conversation_id, user_id),
                    INDEX idx_conv_participant_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS conversation_members (
                    conversation_id VARCHAR(128) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    display_name_cache VARCHAR(255) NULL,
                    role INT NOT NULL DEFAULT 1,
                    last_read_seq BIGINT NOT NULL DEFAULT 0,
                    last_delivered_seq BIGINT NOT NULL DEFAULT 0,
                    unread_count INT NOT NULL DEFAULT 0,
                    mention_count INT NOT NULL DEFAULT 0,
                    is_pinned TINYINT NOT NULL DEFAULT 0,
                    pinned_time BIGINT NOT NULL DEFAULT 0,
                    mute_until BIGINT NOT NULL DEFAULT 0,
                    is_hidden TINYINT NOT NULL DEFAULT 0,
                    last_seen_time BIGINT NOT NULL DEFAULT 0,
                    joined_at BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (conversation_id, user_id),
                    INDEX idx_conversation_members_user (user_id),
                    INDEX idx_conversation_members_list (user_id, is_pinned, pinned_time, last_seen_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS messages (
                    message_id VARCHAR(128) PRIMARY KEY,
                    conversation_id VARCHAR(128) NOT NULL,
                    sender_id VARCHAR(64) NOT NULL,
                    receiver_id VARCHAR(64) NULL,
                    group_id VARCHAR(64) NULL,
                    message_type INT NOT NULL,
                    content LONGTEXT NULL,
                    extra LONGTEXT NULL,
                    client_seq BIGINT NOT NULL,
                    conversation_seq BIGINT NOT NULL,
                    send_time BIGINT NOT NULL,
                    create_time BIGINT NOT NULL,
                    INDEX idx_messages_conversation_seq (conversation_id, conversation_seq),
                    INDEX idx_messages_sender (sender_id),
                    INDEX idx_messages_receiver (receiver_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS message_receipts (
                    message_id VARCHAR(128) NOT NULL,
                    conversation_id VARCHAR(128) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    receipt_type INT NOT NULL,
                    conversation_seq BIGINT NOT NULL DEFAULT 0,
                    event_user_seq BIGINT NOT NULL DEFAULT 0,
                    receipt_time BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (message_id, user_id, receipt_type),
                    INDEX idx_message_receipts_conversation (conversation_id, conversation_seq),
                    INDEX idx_message_receipts_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS chat_groups (
                    group_id VARCHAR(64) PRIMARY KEY,
                    group_name VARCHAR(255) NOT NULL,
                    owner_id VARCHAR(64) NOT NULL,
                    created_at BIGINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS group_members (
                    group_id VARCHAR(64) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    role INT NOT NULL DEFAULT 1,
                    PRIMARY KEY (group_id, user_id),
                    INDEX idx_group_members_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS friend_requests (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    from_user_id VARCHAR(64) NOT NULL,
                    to_user_id VARCHAR(64) NOT NULL,
                    from_nickname VARCHAR(255) NULL,
                    request_message VARCHAR(1024) NULL,
                    status INT NOT NULL,
                    create_time BIGINT NOT NULL,
                    INDEX idx_friend_requests_to_user (to_user_id),
                    INDEX idx_friend_requests_from_user (from_user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS user_seq_counters (
                    user_id VARCHAR(64) PRIMARY KEY,
                    latest_user_seq BIGINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS conversation_seq_counters (
                    conversation_id VARCHAR(128) PRIMARY KEY,
                    latest_conversation_seq BIGINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS inbox_events (
                    owner_user_id VARCHAR(64) NOT NULL,
                    user_seq BIGINT NOT NULL,
                    event_type INT NOT NULL,
                    payload LONGTEXT NOT NULL,
                    created_at BIGINT NOT NULL,
                    PRIMARY KEY (owner_user_id, user_seq),
                    INDEX idx_inbox_events_type (event_type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """.trimIndent()
            )
        }
    }

    private fun loadStructuredState(conn: Connection): JSONObject? {
        if (tableCount(conn, "users") == 0L && tableCount(conn, "messages") == 0L) {
            return null
        }

        return JSONObject().apply {
            put("users", loadUsers(conn))
            put("credentials", loadCredentials(conn))
            put("authSessions", loadAuthSessions(conn))
            put("friendships", loadFriendships(conn))
            put("conversations", loadConversations(conn))
            put("messages", loadMessages(conn))
            put("groups", loadGroups(conn))
            put("friendRequests", loadFriendRequests(conn))
            put("events", loadEvents(conn))
        }
    }

    private fun tableCount(conn: Connection, tableName: String): Long {
        conn.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $tableName").use { rs ->
                return if (rs.next()) rs.getLong(1) else 0L
            }
        }
    }

    private fun loadUsers(conn: Connection): JSONArray {
        val arr = JSONArray()
        conn.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT user_id, nickname, avatar, avatar_url, avatar_version, signature, region, created_at
                FROM users
                ORDER BY user_id
                """.trimIndent()
            ).use { rs ->
                while (rs.next()) {
                    arr.put(JSONObject().apply {
                        put("userId", rs.getString("user_id"))
                        put("nickname", rs.getString("nickname"))
                        put("avatar", rs.getString("avatar") ?: "")
                        put("avatarUrl", rs.getString("avatar_url") ?: "")
                        put("avatarVersion", rs.getInt("avatar_version"))
                        put("signature", rs.getString("signature") ?: "")
                        put("region", rs.getString("region") ?: "")
                        put("createdAt", rs.getLong("created_at"))
                    })
                }
            }
        }
        return arr
    }

    private fun loadCredentials(conn: Connection): JSONObject {
        val obj = JSONObject()
        conn.createStatement().use { statement ->
            statement.executeQuery("SELECT user_id, password FROM credentials ORDER BY user_id").use { rs ->
                while (rs.next()) {
                    obj.put(rs.getString("user_id"), rs.getString("password"))
                }
            }
        }
        return obj
    }

    private fun loadAuthSessions(conn: Connection): JSONArray {
        val arr = JSONArray()
        conn.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT token_id, user_id, issued_at, expires_at, revoked_at, device_name, client_ip, last_seen_at
                FROM auth_sessions
                ORDER BY issued_at
                """.trimIndent()
            ).use { rs ->
                while (rs.next()) {
                    arr.put(JSONObject().apply {
                        put("tokenId", rs.getString("token_id"))
                        put("userId", rs.getString("user_id"))
                        put("issuedAt", rs.getLong("issued_at"))
                        put("expiresAt", rs.getLong("expires_at"))
                        put("revokedAt", rs.getLong("revoked_at"))
                        put("deviceName", rs.getString("device_name") ?: "")
                        put("clientIp", rs.getString("client_ip") ?: "")
                        put("lastSeenAt", rs.getLong("last_seen_at"))
                    })
                }
            }
        }
        return arr
    }

    private fun loadFriendships(conn: Connection): JSONObject {
        val obj = JSONObject()
        conn.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT user_id, friend_id
                FROM friendships
                ORDER BY user_id, friend_id
                """.trimIndent()
            ).use { rs ->
                while (rs.next()) {
                    val userId = rs.getString("user_id")
                    val friends = obj.optJSONArray(userId) ?: JSONArray().also { obj.put(userId, it) }
                    friends.put(rs.getString("friend_id"))
                }
            }
        }
        return obj
    }

    private fun loadConversations(conn: Connection): JSONArray {
        val participants = mutableMapOf<String, JSONArray>()
        conn.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT conversation_id, user_id
                FROM conversation_participants
                ORDER BY conversation_id, user_id
                """.trimIndent()
            ).use { rs ->
                while (rs.next()) {
                    participants.getOrPut(rs.getString("conversation_id")) { JSONArray() }
                        .put(rs.getString("user_id"))
                }
            }
        }

        val arr = JSONArray()
        conn.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT conversation_id, type, group_id, created_at
                FROM conversations
                ORDER BY conversation_id
                """.trimIndent()
            ).use { rs ->
                while (rs.next()) {
                    val conversationId = rs.getString("conversation_id")
                    arr.put(JSONObject().apply {
                        put("conversationId", conversationId)
                        put("type", rs.getString("type"))
                        rs.getString("group_id")?.let { put("groupId", it) }
                        put("participants", participants[conversationId] ?: JSONArray())
                        put("createdAt", rs.getLong("created_at"))
                    })
                }
            }
        }
        return arr
    }

    private fun loadMessages(conn: Connection): JSONArray {
        val arr = JSONArray()
        conn.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT message_id, conversation_id, sender_id, receiver_id, group_id,
                       message_type, content, extra, client_seq, conversation_seq, send_time, create_time
                FROM messages
                ORDER BY create_time, message_id
                """.trimIndent()
            ).use { rs ->
                while (rs.next()) {
                    arr.put(JSONObject().apply {
                        put("messageId", rs.getString("message_id"))
                        put("conversationId", rs.getString("conversation_id"))
                        put("senderId", rs.getString("sender_id"))
                        rs.getString("receiver_id")?.let { put("receiverId", it) }
                        rs.getString("group_id")?.let { put("groupId", it) }
                        put("messageType", rs.getInt("message_type"))
                        put("content", rs.getString("content") ?: "")
                        rs.getString("extra")?.let { put("extra", it) }
                        put("clientSeq", rs.getLong("client_seq"))
                        put("conversationSeq", rs.getLong("conversation_seq"))
                        put("sendTime", rs.getLong("send_time"))
                        put("createTime", rs.getLong("create_time"))
                    })
                }
            }
        }
        return arr
    }

    private fun loadGroups(conn: Connection): JSONArray {
        val members = mutableMapOf<String, JSONArray>()
        conn.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT group_id, user_id
                FROM group_members
                ORDER BY group_id, user_id
                """.trimIndent()
            ).use { rs ->
                while (rs.next()) {
                    members.getOrPut(rs.getString("group_id")) { JSONArray() }
                        .put(rs.getString("user_id"))
                }
            }
        }

        val arr = JSONArray()
        conn.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT group_id, group_name, owner_id, created_at
                FROM chat_groups
                ORDER BY group_id
                """.trimIndent()
            ).use { rs ->
                while (rs.next()) {
                    val groupId = rs.getString("group_id")
                    arr.put(JSONObject().apply {
                        put("groupId", groupId)
                        put("groupName", rs.getString("group_name"))
                        put("ownerId", rs.getString("owner_id"))
                        put("members", members[groupId] ?: JSONArray())
                        put("createdAt", rs.getLong("created_at"))
                    })
                }
            }
        }
        return arr
    }

    private fun loadFriendRequests(conn: Connection): JSONArray {
        val arr = JSONArray()
        conn.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT from_user_id, to_user_id, from_nickname, request_message, status, create_time
                FROM friend_requests
                ORDER BY create_time, id
                """.trimIndent()
            ).use { rs ->
                while (rs.next()) {
                    arr.put(JSONObject().apply {
                        put("fromUserId", rs.getString("from_user_id"))
                        put("toUserId", rs.getString("to_user_id"))
                        put("fromNickname", rs.getString("from_nickname") ?: "")
                        put("message", rs.getString("request_message") ?: "")
                        put("status", rs.getInt("status"))
                        put("createTime", rs.getLong("create_time"))
                    })
                }
            }
        }
        return arr
    }

    private fun loadEvents(conn: Connection): JSONObject {
        return JSONObject().apply {
            put("userSeqCounters", JSONObject().also { counters ->
                conn.createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT user_id, latest_user_seq FROM user_seq_counters ORDER BY user_id"
                    ).use { rs ->
                        while (rs.next()) counters.put(rs.getString("user_id"), rs.getLong("latest_user_seq"))
                    }
                }
            })
            put("convSeqCounters", JSONObject().also { counters ->
                conn.createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT conversation_id, latest_conversation_seq FROM conversation_seq_counters ORDER BY conversation_id"
                    ).use { rs ->
                        while (rs.next()) counters.put(rs.getString("conversation_id"), rs.getLong("latest_conversation_seq"))
                    }
                }
            })
            put("inboxes", JSONObject().also { inboxes ->
                conn.createStatement().use { statement ->
                    statement.executeQuery(
                        """
                        SELECT owner_user_id, user_seq, event_type, payload, created_at
                        FROM inbox_events
                        ORDER BY owner_user_id, user_seq
                        """.trimIndent()
                    ).use { rs ->
                        while (rs.next()) {
                            val ownerUserId = rs.getString("owner_user_id")
                            val arr = inboxes.optJSONArray(ownerUserId) ?: JSONArray().also { inboxes.put(ownerUserId, it) }
                            val payload = JSONObject(rs.getString("payload"))
                            arr.put(JSONObject().apply {
                                put("userSeq", rs.getLong("user_seq"))
                                put("eventType", rs.getInt("event_type"))
                                put("payload", payload)
                                put("createdAt", rs.getLong("created_at"))
                            })
                        }
                    }
                }
            })
        }
    }

    private fun refreshMirrorTables(conn: Connection, root: JSONObject) {
        conn.createStatement().use { statement ->
            statement.executeUpdate("DELETE FROM friend_requests")
        }

        root.optJSONArray("users")?.let { users ->
            conn.prepareStatement(
                "INSERT INTO users (user_id, nickname, avatar, avatar_url, avatar_version, signature, region, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE nickname=VALUES(nickname), avatar=VALUES(avatar), avatar_url=VALUES(avatar_url), avatar_version=VALUES(avatar_version), signature=VALUES(signature), region=VALUES(region), created_at=VALUES(created_at)"
            ).use { stmt ->
                for (i in 0 until users.length()) {
                    val obj = users.getJSONObject(i)
                    stmt.setString(1, obj.getString("userId"))
                    stmt.setString(2, obj.optString("nickname", obj.getString("userId")))
                    stmt.setNullableString(3, obj.optString("avatar", ""))
                    stmt.setNullableString(4, obj.optString("avatarUrl", ""))
                    stmt.setInt(5, obj.optInt("avatarVersion", 0))
                    stmt.setNullableString(6, obj.optString("signature", ""))
                    stmt.setNullableString(7, obj.optString("region", ""))
                    stmt.setLong(8, obj.optLong("createdAt", 0))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }

        root.optJSONObject("credentials")?.let { credentials ->
            conn.prepareStatement("INSERT INTO credentials (user_id, password) VALUES (?, ?) ON DUPLICATE KEY UPDATE password=VALUES(password)").use { stmt ->
                credentials.keys().forEach { userId ->
                    stmt.setString(1, userId)
                    stmt.setString(2, credentials.optString(userId, ""))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }

        root.optJSONArray("authSessions")?.let { sessions ->
            conn.prepareStatement(
                """
                INSERT INTO auth_sessions (
                    token_id, user_id, issued_at, expires_at, revoked_at, device_name, client_ip, last_seen_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    user_id=VALUES(user_id), issued_at=VALUES(issued_at), expires_at=VALUES(expires_at),
                    revoked_at=VALUES(revoked_at), device_name=VALUES(device_name), client_ip=VALUES(client_ip),
                    last_seen_at=VALUES(last_seen_at)
                """.trimIndent()
            ).use { stmt ->
                for (i in 0 until sessions.length()) {
                    val obj = sessions.getJSONObject(i)
                    stmt.setString(1, obj.getString("tokenId"))
                    stmt.setString(2, obj.getString("userId"))
                    stmt.setLong(3, obj.optLong("issuedAt", 0))
                    stmt.setLong(4, obj.optLong("expiresAt", 0))
                    stmt.setLong(5, obj.optLong("revokedAt", 0))
                    stmt.setString(6, obj.optString("deviceName", ""))
                    stmt.setString(7, obj.optString("clientIp", ""))
                    stmt.setLong(8, obj.optLong("lastSeenAt", 0))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }

        root.optJSONObject("friendships")?.let { friendships ->
            conn.prepareStatement("INSERT INTO friendships (user_id, friend_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)").use { stmt ->
                friendships.keys().forEach { userId ->
                    val arr = friendships.optJSONArray(userId)
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            stmt.setString(1, userId)
                            stmt.setString(2, arr.getString(i))
                            stmt.addBatch()
                        }
                    }
                }
                stmt.executeBatch()
            }
        }

        root.optJSONArray("conversations")?.let { conversations ->
            conn.prepareStatement(
                "INSERT INTO conversations (conversation_id, type, group_id, created_at) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE type=VALUES(type), group_id=VALUES(group_id), created_at=VALUES(created_at)"
            ).use { convStmt ->
                conn.prepareStatement(
                    "INSERT INTO conversation_participants (conversation_id, user_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE conversation_id=VALUES(conversation_id)"
                ).use { participantStmt ->
                    for (i in 0 until conversations.length()) {
                        val obj = conversations.getJSONObject(i)
                        val conversationId = obj.getString("conversationId")
                        convStmt.setString(1, conversationId)
                        convStmt.setString(2, obj.optString("type", "SINGLE"))
                        convStmt.setNullableString(3, obj.optStringOrNull("groupId"))
                        convStmt.setLong(4, obj.optLong("createdAt", 0))
                        convStmt.addBatch()

                        val participants = obj.optJSONArray("participants")
                        if (participants != null) {
                            for (j in 0 until participants.length()) {
                                participantStmt.setString(1, conversationId)
                                participantStmt.setString(2, participants.getString(j))
                                participantStmt.addBatch()
                            }
                        }
                    }
                    convStmt.executeBatch()
                    participantStmt.executeBatch()
                }
            }
        }

        root.optJSONArray("messages")?.let { messages ->
            conn.prepareStatement(
                """
                INSERT INTO messages (
                    message_id, conversation_id, sender_id, receiver_id, group_id,
                    message_type, content, extra, client_seq, conversation_seq, send_time, create_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    conversation_id=VALUES(conversation_id), sender_id=VALUES(sender_id),
                    receiver_id=VALUES(receiver_id), group_id=VALUES(group_id),
                    message_type=VALUES(message_type), content=VALUES(content), extra=VALUES(extra),
                    client_seq=VALUES(client_seq), conversation_seq=VALUES(conversation_seq),
                    send_time=VALUES(send_time), create_time=VALUES(create_time)
                """.trimIndent()
            ).use { stmt ->
                for (i in 0 until messages.length()) {
                    val obj = messages.getJSONObject(i)
                    stmt.setString(1, obj.getString("messageId"))
                    stmt.setString(2, obj.getString("conversationId"))
                    stmt.setString(3, obj.getString("senderId"))
                    stmt.setNullableString(4, obj.optStringOrNull("receiverId"))
                    stmt.setNullableString(5, obj.optStringOrNull("groupId"))
                    stmt.setInt(6, obj.optInt("messageType", 0))
                    stmt.setNullableString(7, obj.optString("content", ""))
                    stmt.setNullableString(8, obj.optStringOrNull("extra"))
                    stmt.setLong(9, obj.optLong("clientSeq", 0))
                    stmt.setLong(10, obj.optLong("conversationSeq", 0))
                    stmt.setLong(11, obj.optLong("sendTime", 0))
                    stmt.setLong(12, obj.optLong("createTime", 0))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }

        refreshConversationMemberAndReceiptTables(conn, root)

        root.optJSONArray("groups")?.let { groups ->
            conn.prepareStatement(
                "INSERT INTO chat_groups (group_id, group_name, owner_id, created_at) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE group_name=VALUES(group_name), owner_id=VALUES(owner_id), created_at=VALUES(created_at)"
            ).use { groupStmt ->
                conn.prepareStatement(
                    "INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE role=VALUES(role)"
                ).use { memberStmt ->
                    for (i in 0 until groups.length()) {
                        val obj = groups.getJSONObject(i)
                        val groupId = obj.getString("groupId")
                        val ownerId = obj.optString("ownerId", "")
                        groupStmt.setString(1, groupId)
                        groupStmt.setString(2, obj.optString("groupName", groupId))
                        groupStmt.setString(3, ownerId)
                        groupStmt.setLong(4, obj.optLong("createdAt", 0))
                        groupStmt.addBatch()

                        val members = obj.optJSONArray("members")
                        if (members != null) {
                            for (j in 0 until members.length()) {
                                val userId = members.getString(j)
                                memberStmt.setString(1, groupId)
                                memberStmt.setString(2, userId)
                                memberStmt.setInt(3, if (userId == ownerId) 0 else 1)
                                memberStmt.addBatch()
                            }
                        }
                    }
                    groupStmt.executeBatch()
                    memberStmt.executeBatch()
                }
            }
        }

        root.optJSONArray("friendRequests")?.let { requests ->
            conn.prepareStatement(
                """
                INSERT INTO friend_requests (
                    from_user_id, to_user_id, from_nickname, request_message, status, create_time
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                for (i in 0 until requests.length()) {
                    val obj = requests.getJSONObject(i)
                    stmt.setString(1, obj.optString("fromUserId", ""))
                    stmt.setString(2, obj.optString("toUserId", ""))
                    stmt.setNullableString(3, obj.optStringOrNull("fromNickname"))
                    stmt.setNullableString(4, obj.optString("message", ""))
                    stmt.setInt(5, obj.optInt("status", 0))
                    stmt.setLong(6, obj.optLong("createTime", 0))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }

        root.optJSONObject("events")?.let { events ->
            events.optJSONObject("userSeqCounters")?.let { counters ->
                conn.prepareStatement("INSERT INTO user_seq_counters (user_id, latest_user_seq) VALUES (?, ?) ON DUPLICATE KEY UPDATE latest_user_seq=VALUES(latest_user_seq)").use { stmt ->
                    counters.keys().forEach { userId ->
                        stmt.setString(1, userId)
                        stmt.setLong(2, counters.optLong(userId, 0))
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
            }
            events.optJSONObject("convSeqCounters")?.let { counters ->
                conn.prepareStatement(
                    "INSERT INTO conversation_seq_counters (conversation_id, latest_conversation_seq) VALUES (?, ?) ON DUPLICATE KEY UPDATE latest_conversation_seq=VALUES(latest_conversation_seq)"
                ).use { stmt ->
                    counters.keys().forEach { conversationId ->
                        stmt.setString(1, conversationId)
                        stmt.setLong(2, counters.optLong(conversationId, 0))
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
            }
            events.optJSONObject("inboxes")?.let { inboxes ->
                conn.prepareStatement(
                    """
                    INSERT INTO inbox_events (owner_user_id, user_seq, event_type, payload, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE event_type=VALUES(event_type), payload=VALUES(payload), created_at=VALUES(created_at)
                    """.trimIndent()
                ).use { stmt ->
                    inboxes.keys().forEach { ownerUserId ->
                        val arr = inboxes.optJSONArray(ownerUserId)
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                stmt.setString(1, ownerUserId)
                                stmt.setLong(2, obj.optLong("userSeq", 0))
                                stmt.setInt(3, obj.optInt("eventType", 0))
                                stmt.setString(4, (obj.optJSONObject("payload") ?: JSONObject()).toString())
                                stmt.setLong(5, obj.optLong("createdAt", 0))
                                stmt.addBatch()
                            }
                        }
                    }
                    stmt.executeBatch()
                }
            }
        }
    }

    private fun refreshConversationMemberAndReceiptTables(conn: Connection, root: JSONObject) {
        val users = mutableMapOf<String, JSONObject>()
        root.optJSONArray("users")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                users[obj.getString("userId")] = obj
            }
        }

        val groupOwners = mutableMapOf<String, String>()
        root.optJSONArray("groups")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                groupOwners[obj.getString("groupId")] = obj.optString("ownerId", "")
            }
        }

        val messagesByConversation = mutableMapOf<String, MutableList<JSONObject>>()
        val messageById = mutableMapOf<String, JSONObject>()
        root.optJSONArray("messages")?.let { arr ->
            for (i in 0 until arr.length()) {
                val msg = arr.getJSONObject(i)
                messagesByConversation.getOrPut(msg.getString("conversationId")) { mutableListOf() }.add(msg)
                messageById[msg.getString("messageId")] = msg
            }
        }

        data class ReadState(val lastReadSeq: Long, val eventUserSeq: Long, val receiptTime: Long)
        val readStates = mutableMapOf<Pair<String, String>, ReadState>()
        root.optJSONObject("events")?.optJSONObject("inboxes")?.let { inboxes ->
            inboxes.keys().forEach { ownerUserId ->
                val events = inboxes.optJSONArray(ownerUserId) ?: return@forEach
                for (i in 0 until events.length()) {
                    val event = events.getJSONObject(i)
                    val payload = event.optJSONObject("payload") ?: JSONObject()
                    if (event.optInt("eventType", payload.optInt("eventType", 0)) == 3) {
                        val conversationId = payload.optString("readConversationId", payload.optString("conversationId", ""))
                        val readUserId = payload.optString("readUserId", "")
                        if (conversationId.isNotBlank() && readUserId.isNotBlank()) {
                            val key = conversationId to readUserId
                            val candidate = ReadState(
                                lastReadSeq = payload.optLong("lastReadSeq", 0),
                                eventUserSeq = event.optLong("userSeq", payload.optLong("userSeq", 0)),
                                receiptTime = event.optLong("createdAt", 0)
                            )
                            val existing = readStates[key]
                            if (existing == null ||
                                candidate.lastReadSeq > existing.lastReadSeq ||
                                (candidate.lastReadSeq == existing.lastReadSeq && candidate.receiptTime > existing.receiptTime)
                            ) {
                                readStates[key] = candidate
                            }
                        }
                    }
                }
            }
        }

        root.optJSONArray("conversations")?.let { conversations ->
            conn.prepareStatement(
                """
                INSERT INTO conversation_members (
                    conversation_id, user_id, display_name_cache, role,
                    last_read_seq, last_delivered_seq, unread_count, mention_count,
                    is_pinned, pinned_time, mute_until, is_hidden, last_seen_time, joined_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    display_name_cache=VALUES(display_name_cache), role=VALUES(role),
                    last_read_seq=VALUES(last_read_seq), last_delivered_seq=VALUES(last_delivered_seq),
                    unread_count=VALUES(unread_count), mention_count=VALUES(mention_count),
                    is_pinned=VALUES(is_pinned), pinned_time=VALUES(pinned_time),
                    mute_until=VALUES(mute_until), is_hidden=VALUES(is_hidden),
                    last_seen_time=VALUES(last_seen_time), joined_at=VALUES(joined_at)
                """.trimIndent()
            ).use { memberStmt ->
                for (i in 0 until conversations.length()) {
                    val conv = conversations.getJSONObject(i)
                    val conversationId = conv.getString("conversationId")
                    val groupId = conv.optStringOrNull("groupId")
                    val ownerId = groupId?.let { groupOwners[it] }.orEmpty()
                    val joinedAt = conv.optLong("createdAt", 0)
                    val convMessages = messagesByConversation[conversationId].orEmpty()
                    val maxSeq = convMessages.maxOfOrNull { it.optLong("conversationSeq", 0) } ?: 0L
                    val participants = conv.optJSONArray("participants") ?: continue
                    for (j in 0 until participants.length()) {
                        val userId = participants.getString(j)
                        val user = users[userId]
                        val readState = readStates[conversationId to userId]
                        val lastReadSeq = readState?.lastReadSeq ?: 0L
                        val unreadCount = convMessages.count { msg ->
                            msg.optString("senderId") != userId &&
                                msg.optLong("conversationSeq", 0) > lastReadSeq
                        }
                        val mentionCount = convMessages.count { msg ->
                            msg.optString("senderId") != userId &&
                                msg.optLong("conversationSeq", 0) > lastReadSeq &&
                                msg.optString("content", "").contains("@${user?.optString("nickname", userId) ?: userId}")
                        }
                        memberStmt.setString(1, conversationId)
                        memberStmt.setString(2, userId)
                        memberStmt.setNullableString(3, user?.optString("nickname", userId) ?: userId)
                        memberStmt.setInt(4, if (userId == ownerId) 0 else 1)
                        memberStmt.setLong(5, lastReadSeq)
                        memberStmt.setLong(6, maxSeq)
                        memberStmt.setInt(7, unreadCount)
                        memberStmt.setInt(8, mentionCount)
                        memberStmt.setInt(9, 0)
                        memberStmt.setLong(10, 0)
                        memberStmt.setLong(11, 0)
                        memberStmt.setInt(12, 0)
                        memberStmt.setLong(13, readState?.receiptTime ?: 0)
                        memberStmt.setLong(14, joinedAt)
                        memberStmt.addBatch()
                    }
                }
                memberStmt.executeBatch()
            }
        }

        conn.prepareStatement(
            """
            INSERT INTO message_receipts (
                message_id, conversation_id, user_id, receipt_type,
                conversation_seq, event_user_seq, receipt_time
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                conversation_id=VALUES(conversation_id), conversation_seq=VALUES(conversation_seq),
                event_user_seq=VALUES(event_user_seq), receipt_time=VALUES(receipt_time)
            """.trimIndent()
        ).use { receiptStmt ->
            readStates.forEach { (key, state) ->
                val (conversationId, readUserId) = key
                messagesByConversation[conversationId].orEmpty()
                    .filter { msg ->
                        msg.optString("senderId") != readUserId &&
                            msg.optLong("conversationSeq", 0) <= state.lastReadSeq
                    }
                    .forEach { msg ->
                        receiptStmt.setString(1, msg.getString("messageId"))
                        receiptStmt.setString(2, conversationId)
                        receiptStmt.setString(3, readUserId)
                        receiptStmt.setInt(4, 2)
                        receiptStmt.setLong(5, msg.optLong("conversationSeq", 0))
                        receiptStmt.setLong(6, state.eventUserSeq)
                        receiptStmt.setLong(7, state.receiptTime)
                        receiptStmt.addBatch()
                    }
            }
            receiptStmt.executeBatch()
        }
    }

    private fun PreparedStatement.setNullableString(index: Int, value: String?) {
        if (value == null) setNull(index, java.sql.Types.LONGVARCHAR) else setString(index, value)
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        return if (has(key) && !isNull(key)) optString(key) else null
    }

    private fun connection(): Connection = DriverManager.getConnection(jdbcUrl, user, password)
}

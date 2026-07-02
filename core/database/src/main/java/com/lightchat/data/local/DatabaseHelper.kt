package com.lightchat.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(private val appContext: Context) : SQLiteOpenHelper(
    appContext, DATABASE_NAME, null, DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_USER)
        db.execSQL(CREATE_TABLE_FRIEND)
        db.execSQL(CREATE_TABLE_CONVERSATION)
        db.execSQL(CREATE_TABLE_MESSAGE)
        db.execSQL(CREATE_TABLE_IM_GROUP)
        db.execSQL(CREATE_TABLE_GROUP_MEMBER)
        db.execSQL(CREATE_TABLE_SYNC_STATE)
        db.execSQL(CREATE_TABLE_FRIEND_REQUEST)
        db.execSQL(CREATE_TABLE_CONVERSATION_MEMBER)
        db.execSQL(CREATE_TABLE_MESSAGE_RECEIPT)
        db.execSQL(CREATE_TABLE_AUTH_SESSION)

        db.execSQL(CREATE_INDEX_MESSAGE_CONV_SEQ)
        db.execSQL(CREATE_INDEX_MESSAGE_CONV_TIME)
        db.execSQL(CREATE_INDEX_CONVERSATION_ORDER)
        db.execSQL(CREATE_INDEX_CONVERSATION_MEMBER_ORDER)
        db.execSQL(CREATE_INDEX_MESSAGE_RECEIPT_MESSAGE)
        db.execSQL(CREATE_INDEX_AUTH_SESSION_USER)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(CREATE_TABLE_FRIEND_REQUEST)
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE user ADD COLUMN region TEXT DEFAULT ''")
        }
        if (oldVersion < 4) {
            migrateToMultiAccountCache(db)
        }
        if (oldVersion < 5) {
            db.execSQL(CREATE_TABLE_CONVERSATION_MEMBER)
            db.execSQL(CREATE_TABLE_MESSAGE_RECEIPT)
            db.execSQL(CREATE_INDEX_CONVERSATION_MEMBER_ORDER)
            db.execSQL(CREATE_INDEX_MESSAGE_RECEIPT_MESSAGE)
        }
        if (oldVersion < 6) {
            db.execSQL(CREATE_TABLE_AUTH_SESSION)
            db.execSQL(CREATE_INDEX_AUTH_SESSION_USER)
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE conversation ADD COLUMN last_thumbnail TEXT DEFAULT ''")
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE conversation ADD COLUMN at_me_count INTEGER DEFAULT 0")
        }
        if (oldVersion < 9) {
            migrateFriendRequestToMultiAccount(db)
        }
        if (oldVersion < 10) {
            db.execSQL("ALTER TABLE user ADD COLUMN avatar_url TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE user ADD COLUMN avatar_version INTEGER DEFAULT 0")
        }
        if (oldVersion < 11) {
            migrateToOwnerIsolation(db)
            db.execSQL("ALTER TABLE group_member ADD COLUMN avatar_url TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE group_member ADD COLUMN avatar_version INTEGER DEFAULT 0")
        }
        if (oldVersion < 12) {
            db.execSQL("ALTER TABLE conversation ADD COLUMN avatar_url TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE conversation ADD COLUMN avatar_version INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE im_group ADD COLUMN avatar_url TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE im_group ADD COLUMN avatar_version INTEGER DEFAULT 0")
        }
    }

    fun clearAccountData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            listOf(
                "message",
                "conversation",
                "friend",
                "friend_request",
                "group_member",
                "im_group",
                "sync_state",
                "user",
                "conversation_member",
                "message_receipt",
                "auth_session"
            ).forEach { table ->
                db.delete(table, null, null)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun currentOwnerId(): String {
        return appContext
            .getSharedPreferences("lightchat_session", Context.MODE_PRIVATE)
            .getString("current_user_id", null)
            ?: "__anonymous__"
    }

    private fun migrateToMultiAccountCache(db: SQLiteDatabase) {
        val owner = currentOwnerId()
        db.execSQL("ALTER TABLE conversation RENAME TO conversation_old")
        db.execSQL(CREATE_TABLE_CONVERSATION)
        db.execSQL(
            """
            INSERT OR REPLACE INTO conversation (
                owner_user_id, conversation_id, type, target_id, title, avatar, avatar_url, avatar_version,
                last_message_id, last_message_content, last_message_time, last_thumbnail, unread_count,
                at_me, is_pinned, pinned_time, is_hidden, is_deleted, mute, manual_unread
            )
            SELECT '$owner', conversation_id, type, target_id, title, avatar, '', 0,
                last_message_id, last_message_content, last_message_time, '', unread_count,
                at_me, is_pinned, pinned_time, is_hidden, is_deleted, mute, manual_unread
            FROM conversation_old
            """.trimIndent()
        )
        db.execSQL("DROP TABLE conversation_old")

        db.execSQL("ALTER TABLE message RENAME TO message_old")
        db.execSQL(CREATE_TABLE_MESSAGE)
        db.execSQL(
            """
            INSERT OR REPLACE INTO message (
                owner_user_id, message_id, conversation_id, sender_id, receiver_id, group_id,
                message_type, content, status, client_seq, conversation_seq, user_seq,
                send_time, create_time, quote_message_id, original_message_id, is_deleted,
                is_recalled, extra
            )
            SELECT '$owner', message_id, conversation_id, sender_id, receiver_id, group_id,
                message_type, content, status, client_seq, conversation_seq, user_seq,
                send_time, create_time, quote_message_id, original_message_id, is_deleted,
                is_recalled, extra
            FROM message_old
            """.trimIndent()
        )
        db.execSQL("DROP TABLE message_old")

        db.execSQL("ALTER TABLE im_group RENAME TO im_group_old")
        db.execSQL(CREATE_TABLE_IM_GROUP)
        db.execSQL(
            """
            INSERT OR REPLACE INTO im_group (
                owner_user_id, group_id, group_name, avatar, avatar_url, avatar_version,
                owner_id, member_count, create_time
            )
            SELECT '$owner', group_id, group_name, avatar, '', 0, owner_id, member_count, create_time
            FROM im_group_old
            """.trimIndent()
        )
        db.execSQL("DROP TABLE im_group_old")

        db.execSQL("ALTER TABLE group_member RENAME TO group_member_old")
        db.execSQL(CREATE_TABLE_GROUP_MEMBER)
        db.execSQL(
            """
            INSERT OR REPLACE INTO group_member (
                owner_user_id, group_id, user_id, nickname, avatar, avatar_url, avatar_version,
                role, alias_in_group, join_time
            )
            SELECT '$owner', group_id, user_id, nickname, avatar, '', 0, role, alias_in_group, join_time
            FROM group_member_old
            """.trimIndent()
        )
        db.execSQL("DROP TABLE group_member_old")

        db.execSQL("ALTER TABLE sync_state RENAME TO sync_state_old")
        db.execSQL(CREATE_TABLE_SYNC_STATE)
        db.execSQL(
            """
            INSERT OR REPLACE INTO sync_state (owner_user_id, key, value)
            SELECT '$owner', key, value FROM sync_state_old
            """.trimIndent()
        )
        db.execSQL("DROP TABLE sync_state_old")

        db.execSQL(CREATE_INDEX_MESSAGE_CONV_SEQ)
        db.execSQL(CREATE_INDEX_MESSAGE_CONV_TIME)
        db.execSQL(CREATE_INDEX_CONVERSATION_ORDER)
    }

    private fun migrateToOwnerIsolation(db: SQLiteDatabase) {
        val owner = currentOwnerId()

        db.execSQL("ALTER TABLE user RENAME TO user_old")
        db.execSQL(CREATE_TABLE_USER)
        db.execSQL(
            """
            INSERT OR REPLACE INTO user (owner_user_id, user_id, nickname, avatar, avatar_url, avatar_version, signature, region)
            SELECT '$owner', user_id, nickname, avatar, avatar_url, avatar_version, signature, region
            FROM user_old
            """.trimIndent()
        )
        db.execSQL("DROP TABLE user_old")

        db.execSQL("ALTER TABLE friend RENAME TO friend_old")
        db.execSQL(CREATE_TABLE_FRIEND)
        db.execSQL(
            """
            INSERT OR REPLACE INTO friend (owner_user_id, user_id, friend_id)
            SELECT '$owner', user_id, friend_id FROM friend_old
            """.trimIndent()
        )
        db.execSQL("DROP TABLE friend_old")
    }

    private fun migrateFriendRequestToMultiAccount(db: SQLiteDatabase) {
        val owner = currentOwnerId()
        db.execSQL("ALTER TABLE friend_request RENAME TO friend_request_old")
        db.execSQL(CREATE_TABLE_FRIEND_REQUEST)
        db.execSQL(
            """
            INSERT OR REPLACE INTO friend_request (
                owner_user_id, request_id, from_user_id, to_user_id,
                from_nickname, message, status, create_time
            )
            SELECT '$owner', request_id, from_user_id, to_user_id,
                from_nickname, message, status, create_time
            FROM friend_request_old
            WHERE to_user_id = '$owner'
            """.trimIndent()
        )
        db.execSQL("DROP TABLE friend_request_old")
    }

    companion object {
        const val DATABASE_NAME = "lightchat.db"
        const val DATABASE_VERSION = 12

        const val CREATE_TABLE_USER = """
            CREATE TABLE IF NOT EXISTS user (
                owner_user_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                nickname TEXT,
                avatar TEXT,
                avatar_url TEXT DEFAULT '',
                avatar_version INTEGER DEFAULT 0,
                signature TEXT,
                region TEXT,
                PRIMARY KEY(owner_user_id, user_id)
            )
        """

        const val CREATE_TABLE_FRIEND = """
            CREATE TABLE IF NOT EXISTS friend (
                owner_user_id TEXT NOT NULL,
                user_id TEXT,
                friend_id TEXT,
                PRIMARY KEY(owner_user_id, user_id, friend_id)
            )
        """

        const val CREATE_TABLE_CONVERSATION = """
            CREATE TABLE IF NOT EXISTS conversation (
                owner_user_id TEXT NOT NULL,
                conversation_id TEXT NOT NULL,
                type INTEGER,
                target_id TEXT,
                title TEXT,
                avatar TEXT,
                avatar_url TEXT DEFAULT '',
                avatar_version INTEGER DEFAULT 0,
                last_message_id TEXT,
                last_message_content TEXT,
                last_message_time INTEGER,
                last_thumbnail TEXT DEFAULT '',
                unread_count INTEGER DEFAULT 0,
                at_me INTEGER DEFAULT 0,
                at_me_count INTEGER DEFAULT 0,
                is_pinned INTEGER DEFAULT 0,
                pinned_time INTEGER DEFAULT 0,
                is_hidden INTEGER DEFAULT 0,
                is_deleted INTEGER DEFAULT 0,
                mute INTEGER DEFAULT 0,
                manual_unread INTEGER DEFAULT 0,
                PRIMARY KEY(owner_user_id, conversation_id)
            )
        """

        const val CREATE_TABLE_MESSAGE = """
            CREATE TABLE IF NOT EXISTS message (
                owner_user_id TEXT NOT NULL,
                message_id TEXT NOT NULL,
                conversation_id TEXT NOT NULL,
                sender_id TEXT NOT NULL,
                receiver_id TEXT,
                group_id TEXT,
                message_type INTEGER,
                content TEXT,
                status INTEGER,
                client_seq INTEGER,
                conversation_seq INTEGER,
                user_seq INTEGER,
                send_time INTEGER,
                create_time INTEGER,
                quote_message_id TEXT,
                original_message_id TEXT,
                is_deleted INTEGER DEFAULT 0,
                is_recalled INTEGER DEFAULT 0,
                extra TEXT,
                PRIMARY KEY(owner_user_id, message_id)
            )
        """

        const val CREATE_TABLE_IM_GROUP = """
            CREATE TABLE IF NOT EXISTS im_group (
                owner_user_id TEXT NOT NULL,
                group_id TEXT NOT NULL,
                group_name TEXT,
                avatar TEXT,
                avatar_url TEXT DEFAULT '',
                avatar_version INTEGER DEFAULT 0,
                owner_id TEXT,
                member_count INTEGER,
                create_time INTEGER,
                PRIMARY KEY(owner_user_id, group_id)
            )
        """

        const val CREATE_TABLE_GROUP_MEMBER = """
            CREATE TABLE IF NOT EXISTS group_member (
                owner_user_id TEXT NOT NULL,
                group_id TEXT,
                user_id TEXT,
                nickname TEXT,
                avatar TEXT,
                avatar_url TEXT DEFAULT '',
                avatar_version INTEGER DEFAULT 0,
                role INTEGER,
                alias_in_group TEXT,
                join_time INTEGER,
                PRIMARY KEY(owner_user_id, group_id, user_id)
            )
        """

        const val CREATE_TABLE_SYNC_STATE = """
            CREATE TABLE IF NOT EXISTS sync_state (
                owner_user_id TEXT NOT NULL,
                key TEXT NOT NULL,
                value INTEGER NOT NULL,
                PRIMARY KEY(owner_user_id, key)
            )
        """

        const val CREATE_INDEX_MESSAGE_CONV_SEQ = """
            CREATE INDEX IF NOT EXISTS idx_message_conversation_seq
            ON message(owner_user_id, conversation_id, conversation_seq DESC)
        """

        const val CREATE_INDEX_MESSAGE_CONV_TIME = """
            CREATE INDEX IF NOT EXISTS idx_message_conversation_time
            ON message(owner_user_id, conversation_id, create_time DESC)
        """

        const val CREATE_INDEX_CONVERSATION_ORDER = """
            CREATE INDEX IF NOT EXISTS idx_conversation_order
            ON conversation(owner_user_id, is_pinned DESC, pinned_time DESC, last_message_time DESC)
        """

        const val CREATE_TABLE_FRIEND_REQUEST = """
            CREATE TABLE IF NOT EXISTS friend_request (
                owner_user_id TEXT NOT NULL,
                request_id TEXT NOT NULL,
                from_user_id TEXT NOT NULL,
                to_user_id TEXT NOT NULL,
                from_nickname TEXT,
                message TEXT,
                status INTEGER DEFAULT 0,
                create_time INTEGER NOT NULL,
                PRIMARY KEY(owner_user_id, request_id)
            )
        """

        const val CREATE_TABLE_CONVERSATION_MEMBER = """
            CREATE TABLE IF NOT EXISTS conversation_member (
                owner_user_id TEXT NOT NULL,
                conversation_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                display_name_cache TEXT,
                role INTEGER DEFAULT 1,
                last_read_seq INTEGER DEFAULT 0,
                last_delivered_seq INTEGER DEFAULT 0,
                unread_count INTEGER DEFAULT 0,
                mention_count INTEGER DEFAULT 0,
                is_pinned INTEGER DEFAULT 0,
                pinned_time INTEGER DEFAULT 0,
                mute_until INTEGER DEFAULT 0,
                is_hidden INTEGER DEFAULT 0,
                last_seen_time INTEGER DEFAULT 0,
                joined_at INTEGER DEFAULT 0,
                PRIMARY KEY(owner_user_id, conversation_id, user_id)
            )
        """

        const val CREATE_TABLE_MESSAGE_RECEIPT = """
            CREATE TABLE IF NOT EXISTS message_receipt (
                owner_user_id TEXT NOT NULL,
                conversation_id TEXT NOT NULL,
                message_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                receipt_type INTEGER NOT NULL,
                conversation_seq INTEGER DEFAULT 0,
                receipt_time INTEGER DEFAULT 0,
                PRIMARY KEY(owner_user_id, message_id, user_id, receipt_type)
            )
        """

        const val CREATE_INDEX_CONVERSATION_MEMBER_ORDER = """
            CREATE INDEX IF NOT EXISTS idx_conversation_member_owner_order
            ON conversation_member(owner_user_id, is_pinned DESC, pinned_time DESC, last_seen_time DESC)
        """

        const val CREATE_INDEX_MESSAGE_RECEIPT_MESSAGE = """
            CREATE INDEX IF NOT EXISTS idx_message_receipt_message
            ON message_receipt(owner_user_id, conversation_id, message_id)
        """

        const val CREATE_TABLE_AUTH_SESSION = """
            CREATE TABLE IF NOT EXISTS auth_session (
                token_id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                token TEXT NOT NULL,
                issued_at INTEGER DEFAULT 0,
                expires_at INTEGER DEFAULT 0,
                login_time INTEGER DEFAULT 0,
                last_used_at INTEGER DEFAULT 0,
                is_active INTEGER DEFAULT 1
            )
        """

        const val CREATE_INDEX_AUTH_SESSION_USER = """
            CREATE INDEX IF NOT EXISTS idx_auth_session_user
            ON auth_session(user_id, is_active, expires_at)
        """
    }
}

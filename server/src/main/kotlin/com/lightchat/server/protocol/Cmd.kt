package com.lightchat.server.protocol

object Cmd {
    const val AUTH = 1
    const val AUTH_ACK = 2
    const val HEARTBEAT = 3
    const val HEARTBEAT_ACK = 4
    const val NEW_EVENT_NOTIFY = 5
    const val SYNC = 6
    const val SYNC_RESULT = 7
    const val RECALL_MESSAGE = 8
    const val CREATE_GROUP = 9
    const val SEND_MESSAGE = 10
    const val MESSAGE_ACK = 11
    const val SEND_FRIEND_REQUEST = 12
    const val ACCEPT_FRIEND_REQUEST = 13
    const val MARK_READ = 14
    const val UPDATE_PROFILE = 15
    const val ADD_GROUP_MEMBERS = 16
    const val REJECT_FRIEND_REQUEST = 17
    const val READ_NOTIFY = 18
    const val UPDATE_CONVERSATION_SETTINGS = 19
    const val ERROR = 99

    fun name(cmd: Int): String = when (cmd) {
        AUTH -> "AUTH"
        AUTH_ACK -> "AUTH_ACK"
        HEARTBEAT -> "HEARTBEAT"
        HEARTBEAT_ACK -> "HEARTBEAT_ACK"
        NEW_EVENT_NOTIFY -> "NEW_EVENT_NOTIFY"
        SYNC -> "SYNC"
        SYNC_RESULT -> "SYNC_RESULT"
        RECALL_MESSAGE -> "RECALL_MESSAGE"
        CREATE_GROUP -> "CREATE_GROUP"
        SEND_MESSAGE -> "SEND_MESSAGE"
        MESSAGE_ACK -> "MESSAGE_ACK"
        SEND_FRIEND_REQUEST -> "SEND_FRIEND_REQUEST"
        ACCEPT_FRIEND_REQUEST -> "ACCEPT_FRIEND_REQUEST"
        MARK_READ -> "MARK_READ"
        UPDATE_PROFILE -> "UPDATE_PROFILE"
        ADD_GROUP_MEMBERS -> "ADD_GROUP_MEMBERS"
        REJECT_FRIEND_REQUEST -> "REJECT_FRIEND_REQUEST"
        READ_NOTIFY -> "READ_NOTIFY"
        UPDATE_CONVERSATION_SETTINGS -> "UPDATE_CONVERSATION_SETTINGS"
        ERROR -> "ERROR"
        else -> "UNKNOWN($cmd)"
    }
}

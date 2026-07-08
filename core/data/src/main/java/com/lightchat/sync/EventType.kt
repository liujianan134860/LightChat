package com.lightchat.sync

object EventType {
    const val NEW_MESSAGE = 1
    const val MESSAGE_RECALL = 2
    const val MESSAGE_READ = 3
    const val GROUP_CREATED = 4
    const val GROUP_MEMBER_JOIN = 5
    const val GROUP_MEMBER_LEAVE = 6
    const val FRIEND_REQUEST_EVENT = 7
    const val FRIEND_ACCEPTED = 8
    const val USER_UPDATE = 9
    const val CONVERSATION_SETTINGS_UPDATED = 10
}

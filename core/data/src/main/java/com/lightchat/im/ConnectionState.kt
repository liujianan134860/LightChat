package com.lightchat.im

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTHENTICATED,
    RECONNECTING;

    val isConnected: Boolean get() = this == CONNECTED || this == AUTHENTICATED
}

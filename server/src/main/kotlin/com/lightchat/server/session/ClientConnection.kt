package com.lightchat.server.session

interface ClientConnection {
    val isOpen: Boolean
    val remoteAddress: String

    fun send(data: ByteArray)
    fun close(code: Int, reason: String)
}

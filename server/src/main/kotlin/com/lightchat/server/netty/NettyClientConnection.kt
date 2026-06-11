package com.lightchat.server.netty

import com.lightchat.server.session.ClientConnection
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame

class NettyClientConnection(
    private val channel: Channel
) : ClientConnection {
    override val isOpen: Boolean
        get() = channel.isOpen && channel.isActive

    override val remoteAddress: String
        get() = channel.remoteAddress()?.toString() ?: "unknown"

    override fun send(data: ByteArray) {
        if (!isOpen) return
        channel.writeAndFlush(BinaryWebSocketFrame(Unpooled.wrappedBuffer(data)))
    }

    override fun close(code: Int, reason: String) {
        if (!channel.isOpen) return
        channel.writeAndFlush(CloseWebSocketFrame(code, reason))
            .addListener(ChannelFutureListener.CLOSE)
    }
}

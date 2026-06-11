package com.lightchat.server.netty

import com.lightchat.server.handler.PacketDispatcher
import com.lightchat.server.protocol.ProtocolCodec
import com.lightchat.server.session.ClientConnection
import com.lightchat.server.session.ConnectionRegistry
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.HandshakeComplete
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.util.AttributeKey

class NettyLightChatWebSocketServer(
    private val port: Int,
    private val packetDispatcher: PacketDispatcher,
    private val connectionRegistry: ConnectionRegistry,
    private val codec: ProtocolCodec
) {
    private val bossGroup = NioEventLoopGroup(1)
    private val workerGroup = NioEventLoopGroup()
    private var serverChannel: Channel? = null

    fun start() {
        val bootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 1024)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline()
                        .addLast(HttpServerCodec())
                        .addLast(HttpObjectAggregator(64 * 1024))
                        .addLast(ChunkedWriteHandler())
                        .addLast(WebSocketServerProtocolHandler("/ws", null, true, 16 * 1024 * 1024))
                        .addLast(LightChatFrameHandler(packetDispatcher, connectionRegistry, codec))
                }
            })

        val future = bootstrap.bind(port).sync()
        serverChannel = future.channel()
        println("[START] LightChat Netty WebSocket Server listening on port $port")
    }

    fun stop() {
        try {
            serverChannel?.close()?.sync()
        } finally {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }

    private class LightChatFrameHandler(
        private val packetDispatcher: PacketDispatcher,
        private val connectionRegistry: ConnectionRegistry,
        private val codec: ProtocolCodec
    ) : SimpleChannelInboundHandler<Any>() {
        override fun channelRead0(ctx: ChannelHandlerContext, msg: Any) {
            when (msg) {
                is BinaryWebSocketFrame -> handleBinary(ctx, msg.content())
                is TextWebSocketFrame -> println("[TEXT] ${ctx.channel().remoteAddress()}: ${msg.text().take(100)}")
            }
        }

        override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
            if (evt is HandshakeComplete) {
                val connection = NettyClientConnection(ctx.channel())
                ctx.channel().attr(CONNECTION_KEY).set(connection)
                println("[OPEN] ${connection.remoteAddress} path=${evt.requestUri()}")
            } else {
                super.userEventTriggered(ctx, evt)
            }
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            val connection = connection(ctx)
            val userId = connectionRegistry.unregister(connection)
            println("[CLOSE] $userId (${connection.remoteAddress})")
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            println("[ERROR] ${ctx.channel().remoteAddress()}: ${cause.message}")
            ctx.close()
        }

        private fun handleBinary(ctx: ChannelHandlerContext, content: ByteBuf) {
            val bytes = ByteArray(content.readableBytes())
            content.getBytes(content.readerIndex(), bytes)
            val packet = codec.decode(bytes)
            if (packet != null) {
                packetDispatcher.dispatch(connection(ctx), packet)
            } else {
                println("[INVALID] Failed to decode packet from ${ctx.channel().remoteAddress()} (${bytes.size} bytes)")
            }
        }

        private fun connection(ctx: ChannelHandlerContext): ClientConnection {
            val existing = ctx.channel().attr(CONNECTION_KEY).get()
            if (existing != null) return existing
            val created = NettyClientConnection(ctx.channel())
            ctx.channel().attr(CONNECTION_KEY).set(created)
            return created
        }

        companion object {
            private val CONNECTION_KEY: AttributeKey<ClientConnection> = AttributeKey.valueOf("lightchat.connection")
        }
    }
}

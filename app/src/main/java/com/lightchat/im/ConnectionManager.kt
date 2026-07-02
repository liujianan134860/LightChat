package com.lightchat.im

import com.lightchat.core.network.NetworkClients
import com.lightchat.protocol.Packet
import com.lightchat.protocol.ProtocolCodec
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString

class ConnectionManager(
    private val serverUrl: String = DEFAULT_URL
) {
    private val codec = ProtocolCodec()
    @Volatile private var webSocket: WebSocket? = null
    private var state: ConnectionState = ConnectionState.DISCONNECTED
    private var seqCounter = 0L
    private var authToken: String? = null
    private var manualDisconnect = false
    private var networkAvailable = true
    private var appInForeground = true
    private var authTimeoutJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val heartbeatManager: HeartbeatManager
    private val reconnectManager: ReconnectManager

    var onPacketReceived: ((Packet) -> Unit)? = null
    var onStateChanged: ((ConnectionState) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null
    var onKicked: ((String) -> Unit)? = null

    private val client = NetworkClients.webSocket

    init {
        heartbeatManager = HeartbeatManager(
            onSendHeartbeat = { sendHeartbeat() },
            onHeartbeatTimeout = {
                log("心跳超时，断开连接")
                closeForReconnect("Heartbeat timeout")
            }
        )
        reconnectManager = ReconnectManager(
            onReconnect = {
                log("尝试重连...")
                val token = authToken
                if (token == null || manualDisconnect || !networkAvailable || !appInForeground) {
                    false
                } else {
                    connectInternal(token)
                    waitForAuthentication()
                }
            },
            onStateChange = { updateState(it) }
        )
    }

    fun connect(token: String) {
        authToken = token
        manualDisconnect = false
        if (!networkAvailable || !appInForeground) {
            log("当前不可连接，等待网络或前台恢复")
            return
        }
        connectInternal(token)
    }

    private fun connectInternal(token: String) {
        if (state == ConnectionState.CONNECTING ||
            state == ConnectionState.CONNECTED ||
            state == ConnectionState.AUTHENTICATED
        ) return

        updateState(ConnectionState.CONNECTING)
        log("正在连接 $serverUrl ...")

        val request = Request.Builder()
            .url(serverUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        val newWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!claimOrIgnore(webSocket)) return
                log("WebSocket 已连接")
                updateState(ConnectionState.CONNECTED)
                sendAuth(token)
                startAuthTimeout(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent(webSocket)) return
                // Ignore text messages; we use binary
                log("收到文本消息: $text")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (!isCurrent(webSocket)) return
                val packet = codec.decode(bytes.toByteArray())
                if (packet != null) {
                    log("收到包: cmd=${packet.cmd.toInt()} seq=${packet.seq} len=${packet.body.size}")
                    if (packet.cmd.toInt() == com.lightchat.protocol.Cmd.ERROR && state != ConnectionState.AUTHENTICATED) {
                        log("认证阶段收到错误: ${codec.getBodyAsString(packet)}")
                        stopAuthTimeout()
                        closeForReconnect("Authentication error")
                        return
                    }
                    handlePacket(packet)
                } else {
                    log("收到无效数据包 (${bytes.size} bytes)")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrent(webSocket)) return
                log("WebSocket 正在关闭: $code $reason")
                if (code == 4001) {
                    onKicked?.invoke(if (reason.isNotBlank()) reason else "账号已在其他设备登录")
                }
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrent(webSocket)) return
                log("WebSocket 已关闭")
                if (code == 4001) {
                    onKicked?.invoke(if (reason.isNotBlank()) reason else "账号已在其他设备登录")
                }
                this@ConnectionManager.webSocket = null
                updateState(ConnectionState.DISCONNECTED)
                stopAuthTimeout()
                heartbeatManager.stop()
                if (code != 4001) scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!claimOrIgnore(webSocket)) return
                log("WebSocket 连接失败: ${t.message}")
                this@ConnectionManager.webSocket = null
                updateState(ConnectionState.DISCONNECTED)
                stopAuthTimeout()
                heartbeatManager.stop()
                scheduleReconnect()
            }
        })
        webSocket = newWebSocket
    }

    private fun claimOrIgnore(socket: WebSocket): Boolean {
        val current = webSocket
        if (current != null && current !== socket) return false
        webSocket = socket
        return true
    }

    private fun isCurrent(socket: WebSocket): Boolean = webSocket === socket

    fun disconnect() {
        manualDisconnect = true
        stopAuthTimeout()
        heartbeatManager.stop()
        reconnectManager.stop()
        webSocket?.close(1000, "Client closing")
        webSocket = null
        updateState(ConnectionState.DISCONNECTED)
    }

    fun onNetworkAvailable() {
        networkAvailable = true
        log("网络已恢复")
        reconnectIfNeeded()
    }

    fun onNetworkLost() {
        networkAvailable = false
        log("网络已断开")
        stopAuthTimeout()
        heartbeatManager.stop()
        reconnectManager.stop()
        webSocket?.cancel()
        webSocket = null
        updateState(ConnectionState.DISCONNECTED)
    }

    fun onAppForeground() {
        appInForeground = true
        log("应用回到前台")
        reconnectIfNeeded()
    }

    fun onAppBackground() {
        appInForeground = false
        log("应用进入后台")
        reconnectManager.stop()
    }

    @Synchronized
    fun send(packet: Packet): Boolean {
        val data = codec.encode(packet)
        return webSocket?.send(ByteString.of(*data)) == true
    }

    @Synchronized
    fun sendRaw(data: ByteArray): Boolean {
        val ws = webSocket
        if (ws == null) {
            Log.e("LightChatIM", "sendRaw FAILED: webSocket is null, state=$state")
            return false
        }
        val sent = ws.send(ByteString.of(*data))
        if (!sent) {
            Log.e("LightChatIM", "sendRaw FAILED: ws.send() returned false, isOpen=${ws.queueSize() >= 0}, state=$state, dataSize=${data.size}")
        }
        return sent
    }

    private fun sendAuth(token: String) {
        val data = codec.encodeAuth(token, nextSeq())
        sendRaw(data)
        log("发送 AUTH 包")
    }

    private fun sendHeartbeat() {
        val data = codec.encodeHeartbeat(nextSeq())
        if (sendRaw(data)) {
            log("发送心跳包")
        }
    }

    private fun handlePacket(packet: Packet) {
        when (packet.cmd.toInt()) {
            com.lightchat.protocol.Cmd.AUTH_ACK -> {
                log("收到 AUTH_ACK，鉴权成功")
                stopAuthTimeout()
                updateState(ConnectionState.AUTHENTICATED)
                heartbeatManager.start()
                reconnectManager.reset()
            }
            com.lightchat.protocol.Cmd.HEARTBEAT_ACK -> {
                heartbeatManager.onAckReceived()
            }
            else -> {
                onPacketReceived?.invoke(packet)
            }
        }
    }

    private fun updateState(newState: ConnectionState) {
        if (state != newState) {
            state = newState
            onStateChanged?.invoke(newState)
        }
    }

    fun getState(): ConnectionState = state

    private fun closeForReconnect(reason: String) {
        stopAuthTimeout()
        heartbeatManager.stop()
        webSocket?.close(1001, reason)
        webSocket = null
        updateState(ConnectionState.DISCONNECTED)
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (!manualDisconnect && authToken != null && networkAvailable && appInForeground) {
            reconnectManager.start()
        }
    }

    private fun reconnectIfNeeded() {
        if (!manualDisconnect && authToken != null && state == ConnectionState.DISCONNECTED) {
            reconnectManager.start()
        }
    }

    private suspend fun waitForAuthentication(): Boolean {
        repeat(20) {
            delay(500)
            if (state == ConnectionState.AUTHENTICATED) return true
            if (state == ConnectionState.DISCONNECTED) return false
        }
        webSocket?.cancel()
        webSocket = null
        updateState(ConnectionState.DISCONNECTED)
        return false
    }

    private fun nextSeq(): Long = ++seqCounter

    private fun log(msg: String) {
        Log.d("LightChatIM", msg)
        onLog?.invoke(msg)
    }

    private fun startAuthTimeout(socket: WebSocket) {
        stopAuthTimeout()
        authTimeoutJob = scope.launch {
            delay(5_000)
            if (isCurrent(socket) && state == ConnectionState.CONNECTED) {
                log("AUTH_ACK 超时，断开后重连")
                withContext(Dispatchers.IO) {
                    socket.cancel()
                    if (isCurrent(socket)) {
                        webSocket = null
                        updateState(ConnectionState.DISCONNECTED)
                        scheduleReconnect()
                    }
                }
            }
        }
    }

    private fun stopAuthTimeout() {
        authTimeoutJob?.cancel()
        authTimeoutJob = null
    }

    companion object {
        const val DEFAULT_URL = "ws://10.129.97.70:8080/ws"
    }
}

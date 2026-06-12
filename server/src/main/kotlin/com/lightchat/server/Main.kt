package com.lightchat.server

import com.lightchat.server.handler.MessageDeliveryService
import com.lightchat.server.handler.PacketDispatcher
import com.lightchat.server.http.AuthHttpServer
import com.lightchat.server.netty.NettyLightChatWebSocketServer
import com.lightchat.server.protocol.ProtocolCodec
import com.lightchat.server.push.MockVendorPushGateway
import com.lightchat.server.security.JwtService
import com.lightchat.server.session.ConnectionRegistry
import com.lightchat.server.store.DataStore
import com.lightchat.server.store.EventService
import com.lightchat.server.store.MySqlStatePersistence
import com.lightchat.server.store.StatePersistence
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

fun main() {
    val port = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8080
    val httpPort = System.getenv("SERVER_HTTP_PORT")?.toIntOrNull() ?: 8081
    val persistence = createPersistence()
    println("=== LightChat Server v1.0 ===")
    println("Initializing components...")

    val dataStore = DataStore()
    val eventService = EventService(dataStore)
    val loadedState = runCatching { persistence.load() }.onFailure {
        println("[PERSIST] Failed to load from ${persistence.describe()}: ${it.message}")
    }.getOrNull()
    if (loadedState != null) {
        dataStore.loadFromJson(loadedState, eventService)
        println("DataStore loaded from ${persistence.describe()}")
    } else {
        persistence.save(dataStore.toJson(eventService))
        println("Empty state saved to ${persistence.describe()}")
    }
    val persistenceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lightchat-persistence").apply { isDaemon = true }
    }
    val saveQueued = AtomicBoolean(false)
    val saveVersion = AtomicInteger(0)
    fun scheduleSave() {
        val targetVersion = saveVersion.incrementAndGet()
        if (!saveQueued.compareAndSet(false, true)) return
        persistenceExecutor.submit {
            var savedVersion = targetVersion
            while (true) {
                try {
                    persistence.save(dataStore.toJson(eventService))
                } catch (e: Exception) {
                    println("[PERSIST] Failed to save data: ${e.message}")
                }
                if (saveVersion.get() == savedVersion) {
                    saveQueued.set(false)
                    if (saveVersion.get() == savedVersion || !saveQueued.compareAndSet(false, true)) {
                        break
                    }
                }
                savedVersion = saveVersion.get()
            }
        }
    }
    dataStore.onChanged = {
        scheduleSave()
    }

    println("DataStore: ${dataStore.getUserCount()} users, ${dataStore.getGroupCount()} groups")

    val connectionRegistry = ConnectionRegistry()
    val codec = ProtocolCodec()
    val jwtService = JwtService()
    val pushGateway = MockVendorPushGateway(dataStore)
    val deliveryService = MessageDeliveryService(dataStore, eventService, connectionRegistry, codec, pushGateway)
    val dispatcher = PacketDispatcher(connectionRegistry, dataStore, eventService, codec, deliveryService, jwtService, pushGateway)

    val server = NettyLightChatWebSocketServer(port, dispatcher, connectionRegistry, codec)
    val httpServer = AuthHttpServer(httpPort, dataStore, jwtService, pushGateway, eventService)
    httpServer.start()
    server.start()
    println("Server started on port $port")
    println("HTTP API started on port $httpPort")
    println("Waiting for connections... (Ctrl+C to stop)")

    Runtime.getRuntime().addShutdownHook(Thread {
        println("\nShutting down...")
        try {
            persistenceExecutor.shutdown()
            persistenceExecutor.awaitTermination(3, TimeUnit.SECONDS)
            persistence.save(dataStore.toJson(eventService))
            server.stop()
        } catch (_: Exception) {}
        try {
            httpServer.stop()
        } catch (_: Exception) {}
        println("Server stopped.")
    })
}

private fun createPersistence(): StatePersistence {
    val jdbcUrl = System.getenv("MYSQL_URL")
        ?: "jdbc:mysql://127.0.0.1:3307/lightchat?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true"
    val user = System.getenv("MYSQL_USER") ?: "root"
    val password = System.getenv("MYSQL_PASSWORD") ?: ""
    return MySqlStatePersistence(jdbcUrl, user, password)
}

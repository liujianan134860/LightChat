package com.lightchat.server.push

import com.lightchat.server.model.ServerMessage
import com.lightchat.server.store.DataStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MockVendorPushGateway(private val dataStore: DataStore) {
    private val pending = ConcurrentHashMap<String, CopyOnWriteArrayList<JSONObject>>()
    private val messagePushIndex = ConcurrentHashMap<String, MutableSet<String>>() // messageId -> pushId set
    private val pushById = ConcurrentHashMap<String, JSONObject>() // pushId -> push JSON
    private val adbExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mock-push-adb-dispatcher").apply { isDaemon = true }
    }
    private val adbPath = System.getenv("MOCK_PUSH_ADB_PATH")?.takeIf { it.isNotBlank() } ?: "adb"
    private val adbUserDevices = parseUserDeviceMap(System.getenv("MOCK_PUSH_ADB_DEVICES").orEmpty())
    private val adbAllDevices = parseDeviceList(System.getenv("MOCK_PUSH_ADB_ALL").orEmpty())

    fun enqueueMessage(recipientId: String, message: ServerMessage) {
        val senderName = dataStore.getUser(message.senderId)?.nickname ?: message.senderId
        val preview = when (message.messageType) {
            1 -> "[图片]"
            5 -> "[名片]${message.content}"
            6 -> "[群名片]${message.content}"
            7 -> "[聊天记录]"
            else -> message.content
        }
        val pushId = UUID.randomUUID().toString()
        val push = JSONObject().apply {
            put("pushId", pushId)
            put("pushType", "message")
            put("userId", recipientId)
            put("conversationId", message.conversationId)
            put("messageId", message.messageId)
            put("title", senderName)
            put("content", preview)
            put("createdAt", System.currentTimeMillis())
        }
        pending.computeIfAbsent(recipientId) { CopyOnWriteArrayList() }.add(push)
        pushById[pushId] = push
        messagePushIndex.computeIfAbsent(message.messageId) { ConcurrentHashMap.newKeySet() }.add(pushId)
        println("[MOCK-PUSH] queued recipient=$recipientId conversation=${message.conversationId}")
        dispatchByAdb(push)
    }

    fun recallMessage(messageId: String, senderName: String) {
        val pushIds = messagePushIndex[messageId] ?: return
        for (pushId in pushIds) {
            val push = pushById[pushId] ?: continue
            push.put("pushType", "message_recall")
            push.put("title", senderName)
            push.put("content", "撤回了一条消息")
            println("[MOCK-PUSH] recall updated pushId=$pushId messageId=$messageId")
            dispatchByAdb(push)
        }
    }

    fun enqueueFriendRequest(recipientId: String, senderName: String, message: String) {
        val content = message.ifBlank { "$senderName 请求添加你为好友" }
        val push = JSONObject().apply {
            put("pushId", UUID.randomUUID().toString())
            put("pushType", "friend_request")
            put("userId", recipientId)
            put("conversationId", "")
            put("title", "新的朋友")
            put("content", content)
            put("createdAt", System.currentTimeMillis())
        }
        pending.computeIfAbsent(recipientId) { CopyOnWriteArrayList() }.add(push)
        println("[MOCK-PUSH] queued friend request recipient=$recipientId sender=$senderName")
        dispatchByAdb(push)
    }

    fun drain(userId: String): JSONArray {
        val messages = pending.remove(userId).orEmpty()
        return JSONArray().apply {
            messages.forEach { put(JSONObject(it.toString())) }
        }
    }

    fun pendingCount(userId: String): Int = pending[userId]?.size ?: 0

    private fun dispatchByAdb(push: JSONObject) {
        val targets = resolveTargets(push.optString("userId"))
        if (targets.isEmpty()) return

        val pushCopy = JSONObject(push.toString())
        adbExecutor.execute {
            targets.forEach { serial ->
                sendAdbBroadcast(serial, pushCopy)
            }
        }
    }

    private fun resolveTargets(userId: String): List<String> {
        val mapped = adbUserDevices[userId].orEmpty()
        return when {
            mapped.isNotEmpty() -> mapped
            adbAllDevices.isNotEmpty() -> adbAllDevices
            else -> emptyList()
        }
    }

    private fun sendAdbBroadcast(serial: String, push: JSONObject) {
        val args = listOf(
            adbPath,
            "-s",
            serial,
            "shell",
            "am",
            "broadcast",
            "-a",
            ACTION_MOCK_VENDOR_PUSH,
            "-n",
            MOCK_PUSH_RECEIVER_COMPONENT,
            "--es",
            "userId",
            push.optString("userId", ""),
            "--es",
            "pushType",
            push.optString("pushType", "message"),
            "--es",
            "conversationId",
            push.optString("conversationId", ""),
            "--es",
            "title",
            push.optString("title", "LightChat"),
            "--es",
            "content",
            push.optString("content", "")
        )

        try {
            val process = ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().readText().trim()
            if (!finished) {
                process.destroyForcibly()
                println("[MOCK-PUSH] adb broadcast timeout serial=$serial")
                return
            }
            if (process.exitValue() == 0) {
                println("[MOCK-PUSH] adb broadcast sent serial=$serial type=${push.optString("pushType")}")
            } else {
                println("[MOCK-PUSH] adb broadcast failed serial=$serial code=${process.exitValue()} output=$output")
            }
        } catch (e: Exception) {
            println("[MOCK-PUSH] adb broadcast error serial=$serial message=${e.message}")
        }
    }

    private fun parseUserDeviceMap(raw: String): Map<String, List<String>> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(",", ";")
            .mapNotNull { entry ->
                val parts = entry.split("=", limit = 2)
                val userId = parts.getOrNull(0)?.trim().orEmpty()
                val serials = parts.getOrNull(1)?.let(::parseDeviceList).orEmpty()
                if (userId.isBlank() || serials.isEmpty()) null else userId to serials
            }
            .toMap()
    }

    private fun parseDeviceList(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split("|", ",", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private companion object {
        const val ACTION_MOCK_VENDOR_PUSH = "com.lightchat.action.MOCK_VENDOR_PUSH"
        const val MOCK_PUSH_RECEIVER_COMPONENT = "com.lightchat/.notification.MockVendorPushReceiver"
    }
}

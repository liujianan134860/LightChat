package com.lightchat.im

import com.lightchat.protocol.Cmd
import com.lightchat.protocol.Packet
import com.lightchat.protocol.ProtocolCodec
import com.lightchat.domain.session.ConnectionController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ImClientState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val logs: List<String> = emptyList()
)

class ImClient(
    private val connectionManager: ConnectionManager = ConnectionManager()
) : ConnectionController {

    private val codec = ProtocolCodec()
    private val _state = MutableStateFlow(ImClientState())
    val state: StateFlow<ImClientState> = _state.asStateFlow()

    private val logBuffer = mutableListOf<String>()
    private var seqCounter = 0L

    private val newEventNotifyListeners = mutableListOf<(Long) -> Unit>()
    private val messageAckListeners = mutableListOf<(String, Int, Long) -> Unit>()
    private val syncResultListeners = mutableListOf<(String) -> Unit>()
    private val recallAckListeners = mutableListOf<(String) -> Unit>()
    private val groupCreateAckListeners = mutableListOf<(String) -> Unit>()
    private val addGroupMembersAckListeners = mutableListOf<(String, Int) -> Unit>()
    private val errorListeners = mutableListOf<(Int, String) -> Unit>()
    private val friendRequestAckListeners = mutableListOf<() -> Unit>()
    private val friendAcceptAckListeners = mutableListOf<() -> Unit>()
    private val markReadAckListeners = mutableListOf<() -> Unit>()
    private val readNotifyListeners = mutableListOf<(String, String, Long) -> Unit>()
    private val updateProfileAckListeners = mutableListOf<() -> Unit>()
    private val updateConvSettingsAckListeners = mutableListOf<() -> Unit>()
    private val kickedListeners = mutableListOf<(String) -> Unit>()

    fun onNewEventNotify(listener: (Long) -> Unit) { if (!newEventNotifyListeners.contains(listener)) newEventNotifyListeners.add(listener) }
    fun onMessageAck(listener: (String, Int, Long) -> Unit) { if (!messageAckListeners.contains(listener)) messageAckListeners.add(listener) }
    fun onSyncResult(listener: (String) -> Unit) { if (!syncResultListeners.contains(listener)) syncResultListeners.add(listener) }
    fun onRecallAck(listener: (String) -> Unit) { if (!recallAckListeners.contains(listener)) recallAckListeners.add(listener) }
    fun onGroupCreateAck(listener: (String) -> Unit) { if (!groupCreateAckListeners.contains(listener)) groupCreateAckListeners.add(listener) }
    fun removeGroupCreateAckListener(listener: (String) -> Unit) { groupCreateAckListeners.remove(listener) }
    fun onAddGroupMembersAck(listener: (String, Int) -> Unit) { if (!addGroupMembersAckListeners.contains(listener)) addGroupMembersAckListeners.add(listener) }
    fun removeAddGroupMembersAckListener(listener: (String, Int) -> Unit) { addGroupMembersAckListeners.remove(listener) }
    fun onError(listener: (Int, String) -> Unit) { if (!errorListeners.contains(listener)) errorListeners.add(listener) }
    fun removeErrorListener(listener: (Int, String) -> Unit) { errorListeners.remove(listener) }
    fun onFriendRequestAck(listener: () -> Unit) { if (!friendRequestAckListeners.contains(listener)) friendRequestAckListeners.add(listener) }
    fun onFriendAcceptAck(listener: () -> Unit) { if (!friendAcceptAckListeners.contains(listener)) friendAcceptAckListeners.add(listener) }
    fun onMarkReadAck(listener: () -> Unit) { if (!markReadAckListeners.contains(listener)) markReadAckListeners.add(listener) }
    fun onReadNotify(listener: (String, String, Long) -> Unit) { if (!readNotifyListeners.contains(listener)) readNotifyListeners.add(listener) }
    fun onUpdateProfileAck(listener: () -> Unit) { if (!updateProfileAckListeners.contains(listener)) updateProfileAckListeners.add(listener) }
    fun onUpdateConversationSettingsAck(listener: () -> Unit) { if (!updateConvSettingsAckListeners.contains(listener)) updateConvSettingsAckListeners.add(listener) }
    fun onKicked(listener: (String) -> Unit) { if (!kickedListeners.contains(listener)) kickedListeners.add(listener) }

    init {
        connectionManager.apply {
            onPacketReceived = { packet -> handlePacket(packet) }
            onStateChanged = { state ->
                _state.value = _state.value.copy(connectionState = state)
            }
            onLog = { msg -> addLog(msg) }
            onKicked = { reason ->
                addLog("账号被其他设备登录: $reason")
                kickedListeners.forEach { it(reason) }
            }
        }
    }

    override fun connect(token: String) {
        val currentState = connectionManager.getState()
        if (currentState != ConnectionState.DISCONNECTED) {
            addLog("ImClient: 已处于 $currentState，跳过重复连接")
            return
        }
        addLog("ImClient: 开始连接...")
        connectionManager.connect(token)
    }

    override fun disconnect() {
        addLog("ImClient: 断开连接")
        connectionManager.disconnect()
    }

    override fun onNetworkAvailable() {
        connectionManager.onNetworkAvailable()
    }

    override fun onNetworkLost() {
        connectionManager.onNetworkLost()
    }

    override fun onAppForeground() {
        connectionManager.onAppForeground()
    }

    override fun onAppBackground() {
        connectionManager.onAppBackground()
    }

    fun sendMessage(
        conversationId: String,
        messageType: Int,
        content: String,
        clientSeq: Long,
        messageId: String,
        sendTime: Long,
        receiverId: String?,
        groupId: String?,
        extra: String? = null
    ): Boolean {
        val data = codec.encodeSendMessage(conversationId, messageType, content, clientSeq, messageId, sendTime, receiverId, groupId, extra, nextSeq())
        addLog("发送消息: conv=$conversationId messageId=$messageId")
        return connectionManager.sendRaw(data)
    }

    fun sync(lastUserSeq: Long): Boolean {
        val data = codec.encodeSync(lastUserSeq, nextSeq())
        addLog("请求同步: lastUserSeq=$lastUserSeq")
        return connectionManager.sendRaw(data)
    }

    fun recallMessage(messageId: String, conversationId: String): Boolean {
        val data = codec.encodeRecallMessage(messageId, conversationId, nextSeq())
        addLog("撤回消息: messageId=$messageId")
        return connectionManager.sendRaw(data)
    }

    fun createGroup(groupId: String, groupName: String, memberIds: List<String>): Boolean {
        val data = codec.encodeCreateGroup(groupId, groupName, memberIds, nextSeq())
        addLog("创建群组: groupId=$groupId")
        return connectionManager.sendRaw(data)
    }

    fun addGroupMembers(groupId: String, memberIds: List<String>): Boolean {
        if (memberIds.isEmpty()) return false
        val data = codec.encodeAddGroupMembers(groupId, memberIds, nextSeq())
        addLog("邀请群成员: groupId=$groupId members=${memberIds.joinToString(",")}")
        return connectionManager.sendRaw(data)
    }

    fun sendFriendRequest(toUserId: String, message: String): Boolean {
        val data = codec.encodeSendFriendRequest(toUserId, message, nextSeq())
        addLog("发送好友申请: toUserId=$toUserId")
        return connectionManager.sendRaw(data)
    }

    fun acceptFriendRequest(fromUserId: String): Boolean {
        val data = codec.encodeAcceptFriendRequest(fromUserId, nextSeq())
        addLog("同意好友申请: fromUserId=$fromUserId")
        return connectionManager.sendRaw(data)
    }

    fun rejectFriendRequest(fromUserId: String): Boolean {
        val data = codec.encodeRejectFriendRequest(fromUserId, nextSeq())
        addLog("拒绝好友申请: fromUserId=$fromUserId")
        return connectionManager.sendRaw(data)
    }

    fun markRead(conversationId: String, lastReadSeq: Long): Boolean {
        val data = codec.encodeMarkRead(conversationId, lastReadSeq, nextSeq())
        addLog("标记已读: conv=$conversationId seq=$lastReadSeq")
        return connectionManager.sendRaw(data)
    }

    fun updateProfile(nickname: String?, avatar: String?, avatarUrl: String?, avatarVersion: Int?, signature: String?, region: String?): Boolean {
        val data = codec.encodeUpdateProfile(nickname, avatar, avatarUrl, avatarVersion, signature, region, nextSeq())
        addLog("更新资料: nickname=$nickname avatarUrl=$avatarUrl")
        return connectionManager.sendRaw(data)
    }

    fun updateConversationSettings(conversationId: String, isPinned: Boolean, pinnedTime: Long, mute: Boolean): Boolean {
        val data = codec.encodeUpdateConversationSettings(conversationId, isPinned, pinnedTime, mute, nextSeq())
        addLog("更新会话设置: conv=$conversationId pinned=$isPinned mute=$mute")
        return connectionManager.sendRaw(data)
    }

    fun isAuthenticated(): Boolean = connectionManager.getState() == ConnectionState.AUTHENTICATED

    fun destroy() {
        connectionManager.disconnect()
    }

    private fun handlePacket(packet: Packet) {
        when (packet.cmd.toInt()) {
            Cmd.NEW_EVENT_NOTIFY -> {
                val json = codec.getBodyAsString(packet)
                try {
                    val obj = org.json.JSONObject(json)
                    val latestUserSeq = obj.optLong("latestUserSeq", 0)
                    addLog("收到新事件通知: latestUserSeq=$latestUserSeq")
                    newEventNotifyListeners.forEach { it(latestUserSeq) }
                } catch (e: Exception) {
                    addLog("解析 NEW_EVENT_NOTIFY 失败: ${e.message}")
                }
            }
            Cmd.MESSAGE_ACK -> {
                val json = codec.getBodyAsString(packet)
                try {
                    val obj = org.json.JSONObject(json)
                    val messageId = obj.optString("messageId", "")
                    val status = obj.optInt("status", 0)
                    val conversationSeq = obj.optLong("conversationSeq", 0)
                    addLog("收到消息 ACK: messageId=$messageId status=$status seq=$conversationSeq")
                    messageAckListeners.forEach { it(messageId, status, conversationSeq) }
                } catch (e: Exception) {
                    addLog("解析 MESSAGE_ACK 失败: ${e.message}")
                }
            }
            Cmd.SYNC_RESULT -> {
                val json = codec.getBodyAsString(packet)
                addLog("收到同步结果: $json")
                syncResultListeners.forEach { it(json) }
            }
            Cmd.RECALL_MESSAGE -> {
                val json = codec.getBodyAsString(packet)
                try {
                    val obj = org.json.JSONObject(json)
                    val messageId = obj.optString("messageId", "")
                    addLog("收到撤回 ACK: messageId=$messageId")
                    recallAckListeners.forEach { it(messageId) }
                } catch (e: Exception) {
                    addLog("解析撤回 ACK 失败: ${e.message}")
                }
            }
            Cmd.CREATE_GROUP -> {
                val json = codec.getBodyAsString(packet)
                try {
                    val obj = org.json.JSONObject(json)
                    val groupId = obj.optString("groupId", "")
                    addLog("收到创建群组 ACK: groupId=$groupId")
                    groupCreateAckListeners.forEach { it(groupId) }
                } catch (e: Exception) {
                    addLog("解析创建群组 ACK 失败: ${e.message}")
                }
            }
            Cmd.SEND_FRIEND_REQUEST -> {
                addLog("收到好友申请 ACK")
                friendRequestAckListeners.forEach { it() }
            }
            Cmd.ACCEPT_FRIEND_REQUEST -> {
                addLog("收到同意好友申请 ACK")
                friendAcceptAckListeners.forEach { it() }
            }
            Cmd.REJECT_FRIEND_REQUEST -> {
                addLog("收到拒绝好友申请 ACK")
            }
            Cmd.MARK_READ -> {
                addLog("收到已读回执 ACK")
                markReadAckListeners.forEach { it() }
            }
            Cmd.READ_NOTIFY -> {
                val json = codec.getBodyAsString(packet)
                try {
                    val obj = org.json.JSONObject(json)
                    val conversationId = obj.optString("conversationId", "")
                    val readUserId = obj.optString("readUserId", "")
                    val lastReadSeq = obj.optLong("lastReadSeq", 0)
                    addLog("收到即时已读通知: conv=$conversationId user=$readUserId seq=$lastReadSeq")
                    readNotifyListeners.forEach { it(conversationId, readUserId, lastReadSeq) }
                } catch (e: Exception) {
                    addLog("解析 READ_NOTIFY 失败: ${e.message}")
                }
            }
            Cmd.UPDATE_PROFILE -> {
                addLog("收到更新资料 ACK")
                updateProfileAckListeners.forEach { it() }
            }
            Cmd.UPDATE_CONVERSATION_SETTINGS -> {
                addLog("收到更新会话设置 ACK")
                updateConvSettingsAckListeners.forEach { it() }
            }
            Cmd.ADD_GROUP_MEMBERS -> {
                val json = codec.getBodyAsString(packet)
                try {
                    val obj = org.json.JSONObject(json)
                    val groupId = obj.optString("groupId", "")
                    val addedCount = obj.optInt("addedCount", 0)
                    addLog("收到邀请群成员 ACK: groupId=$groupId addedCount=$addedCount")
                    addGroupMembersAckListeners.forEach { it(groupId, addedCount) }
                } catch (e: Exception) {
                    addLog("解析邀请群成员 ACK 失败: ${e.message}")
                }
            }
            Cmd.ERROR -> {
                val json = codec.getBodyAsString(packet)
                addLog("收到错误: $json")
                try {
                    val obj = org.json.JSONObject(json)
                    errorListeners.forEach {
                        it(obj.optInt("code", -1), obj.optString("message", "请求失败"))
                    }
                } catch (_: Exception) {
                    errorListeners.forEach { it(-1, "请求失败") }
                }
            }
            else -> {
                addLog("未知包类型: cmd=${packet.cmd.toInt()}")
            }
        }
    }

    private fun nextSeq(): Long = ++seqCounter

    private fun addLog(msg: String) {
        logBuffer.add("[${System.currentTimeMillis() % 100000}] $msg")
        if (logBuffer.size > 200) {
            logBuffer.removeAt(0)
        }
        _state.value = _state.value.copy(logs = logBuffer.toList())
    }
}

package com.lightchat.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightchat.LightChatApplication
import com.lightchat.data.remote.AuthApiClient
import com.lightchat.event.AppEvents
import com.lightchat.model.Conversation
import com.lightchat.model.ConversationType
import com.lightchat.model.Message
import com.lightchat.model.MessageStatus
import com.lightchat.model.MessageType
import com.lightchat.model.User
import com.lightchat.ui.chat.calculateBitmapInSampleSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import com.lightchat.util.showToast
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ChatUiState(
    val conversationId: String = "",
    val title: String = "",
    val messages: List<Message> = emptyList(),
    val groupReadCounts: Map<String, GroupReadCount> = emptyMap(),
    val inputText: String = "",
    val hasMoreMessages: Boolean = false,
    val isLoadingMoreMessages: Boolean = false,
    val hasNewerMessages: Boolean = false,
    val isLoadingNewerMessages: Boolean = false,
    val isSending: Boolean = false,
    val focusInputRequest: Int = 0,
    val scrollToBottomRequest: Int = 0,
    val isPositionedMidConversation: Boolean = false,
    val totalMessageCount: Int = 0,
    val statusChangeVersion: Long = 0L
)

data class GroupReadCount(
    val readCount: Int,
    val totalCount: Int,
    val mentionedReadNames: List<String> = emptyList(),
    val mentionedUnreadNames: List<String> = emptyList(),
    val hasMentionTargets: Boolean = false
)

class ChatViewModel : ViewModel() {

    private val app = LightChatApplication.instance
    private val messageRepository = app.messageRepository
    private val conversationRepository = app.conversationRepository
    private val userSession = app.userSession
    private val authApiClient = AuthApiClient()
    private val ackTimeoutJobs = ConcurrentHashMap<String, Job>()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var clientSeqCounter = 0L
    private var lastLoadMoreAnchorSeq = Long.MIN_VALUE
    private var lastLoadNewerAnchorKey = ""
    private var lastLoadOlderCallMs = 0L
    private var lastLoadNewerCallMs = 0L
    private val lastReportedReadSeq = ConcurrentHashMap<String, Long>()

    init {
        app.imClient.onMessageAck { messageId, status, conversationSeq ->
            handleAck(messageId, status, conversationSeq)
        }
        app.imClient.onRecallAck { messageId ->
            applyLocalRecall(messageId)
        }
        app.imClient.onError { code, message ->
            handleError(code, message)
        }
        viewModelScope.launch {
            AppEvents.messageChanged.collect { conversationId ->
                if (conversationId == _uiState.value.conversationId) {
                    refreshMessages(clearInput = false, scrollToBottom = !_uiState.value.hasNewerMessages)
                    app.conversationDao.clearUnread(conversationId)
                    markCurrentConversationRead()
                    AppEvents.notifyConversationChanged(conversationId)
                }
            }
        }
        viewModelScope.launch {
            app.imClient.state.collect { state ->
                if (state.connectionState == com.lightchat.im.ConnectionState.AUTHENTICATED) {
                    markCurrentConversationRead()
                }
            }
        }
    }

    fun loadConversation(conversationId: String, targetMessageId: String? = null) {
        val conv = conversationRepository.getConversation(conversationId)
        val resolvedTitle = resolveConversationTitle(conv)
        app.currentOpenConversationId = conversationId
        viewModelScope.launch {
            val targetId = targetMessageId?.takeIf { it.isNotBlank() }
            val msgs = if (targetId != null) {
                withContext(Dispatchers.IO) {
                    messageRepository.getMessagesAround(conversationId, targetId)
                }
            } else {
                messageRepository.getMessages(conversationId)
            }
            val totalCount = messageRepository.getMessageCount(conversationId)
            lastLoadMoreAnchorSeq = Long.MIN_VALUE
            lastLoadNewerAnchorKey = ""
            val hasOlder = msgs.firstOrNull()?.let { messageRepository.hasMessagesBefore(conversationId, it) }
                ?: false
            _uiState.value = ChatUiState(
                conversationId = conversationId,
                title = resolvedTitle,
                messages = msgs,
                groupReadCounts = buildGroupReadCounts(conv, msgs),
                hasMoreMessages = if (targetId == null) msgs.size < totalCount else hasOlder,
                hasNewerMessages = false,
                isPositionedMidConversation = targetId != null,
                totalMessageCount = totalCount,
                scrollToBottomRequest = if (targetId == null) 1 else 0
            )

            // Clear @me flag on conversation entry
            app.conversationDao.setAtMe(conversationId, false)
            app.conversationDao.clearUnread(conversationId)
            AppEvents.notifyConversationChanged(conversationId)

            markCurrentConversationRead()
            refreshPeerProfileWhenSingle(conv)
        }
    }

    fun loadMoreMessages() {
        val state = _uiState.value
        val messages = state.messages
        if (messages.isEmpty() || state.isLoadingMoreMessages || !state.hasMoreMessages) return
        val now = System.currentTimeMillis()
        if (now - lastLoadOlderCallMs < 400) return

        val earliest = messages.first()
        val earliestSeq = earliest.conversationSeq.takeIf { it > 0L } ?: earliest.createTime
        if (earliestSeq == lastLoadMoreAnchorSeq) return
        lastLoadMoreAnchorSeq = earliestSeq
        lastLoadOlderCallMs = now
        _uiState.value = state.copy(isLoadingMoreMessages = true)
        viewModelScope.launch {
            val older = withContext(Dispatchers.IO) {
                messageRepository.loadOlderMessages(state.conversationId, earliest)
            }
            val merged = (older + messages).distinctBy { it.messageId }
            if (older.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    messages = merged,
                    groupReadCounts = buildGroupReadCounts(
                        conversationRepository.getConversation(state.conversationId),
                        merged
                    ),
                    hasMoreMessages = merged.firstOrNull()?.let {
                        messageRepository.hasMessagesBefore(state.conversationId, it)
                    } ?: false,
                    isLoadingMoreMessages = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    hasMoreMessages = false,
                    isLoadingMoreMessages = false
                )
            }
        }
    }

    fun loadNewerMessages() {
        val state = _uiState.value
        val messages = state.messages
        if (messages.isEmpty() || state.isLoadingNewerMessages || !state.hasNewerMessages) return
        val now = System.currentTimeMillis()
        if (now - lastLoadNewerCallMs < 400) return

        val latest = messages.last()
        val anchorKey = "${latest.conversationSeq}:${latest.createTime}:${latest.clientSeq}:${latest.messageId}"
        if (anchorKey == lastLoadNewerAnchorKey) return
        lastLoadNewerAnchorKey = anchorKey
        lastLoadNewerCallMs = now
        _uiState.value = state.copy(isLoadingNewerMessages = true)
        viewModelScope.launch {
            val newer = withContext(Dispatchers.IO) {
                messageRepository.loadNewerMessages(state.conversationId, latest)
            }
            val merged = (messages + newer).distinctBy { it.messageId }
            if (newer.isNotEmpty()) {
                val conv = conversationRepository.getConversation(state.conversationId)
                _uiState.value = _uiState.value.copy(
                    messages = merged,
                    groupReadCounts = buildGroupReadCounts(conv, merged),
                    hasNewerMessages = merged.lastOrNull()?.let {
                        messageRepository.hasMessagesAfter(state.conversationId, it)
                    } ?: false,
                    isLoadingNewerMessages = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    hasNewerMessages = false,
                    isLoadingNewerMessages = false
                )
            }
        }
    }

    fun jumpToLatest() {
        val state = _uiState.value
        if (state.conversationId.isBlank()) return
        viewModelScope.launch {
            val conv = conversationRepository.getConversation(state.conversationId)
            val msgs = messageRepository.getMessages(state.conversationId)
            val totalCount = messageRepository.getMessageCount(state.conversationId)
            lastLoadMoreAnchorSeq = Long.MIN_VALUE
            lastLoadNewerAnchorKey = ""
            _uiState.value = state.copy(
                messages = msgs,
                groupReadCounts = buildGroupReadCounts(conv, msgs),
                hasMoreMessages = msgs.size < totalCount,
                isLoadingMoreMessages = false,
                hasNewerMessages = false,
                isLoadingNewerMessages = false,
                isPositionedMidConversation = false,
                totalMessageCount = totalCount,
                scrollToBottomRequest = state.scrollToBottomRequest + 1
            )
        }
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage(atUserIds: List<String> = emptyList()) {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        val state = _uiState.value
        val userId = userSession.currentUserId ?: return
        val messageId = UUID.randomUUID().toString()
        val clientSeq = ++clientSeqCounter

        val extra = if (atUserIds.isNotEmpty()) {
            val ids = org.json.JSONArray()
            atUserIds.forEach { ids.put(it) }
            org.json.JSONObject().put("atUserIds", ids).toString()
        } else null

        val msg = Message(
            messageId = messageId,
            conversationId = state.conversationId,
            senderId = userId,
            messageType = MessageType.TEXT,
            content = text,
            status = MessageStatus.SENDING,
            clientSeq = clientSeq,
            sendTime = System.currentTimeMillis(),
            createTime = System.currentTimeMillis(),
            extra = extra
        )

        viewModelScope.launch {
            messageRepository.sendMessage(msg)

            try {
                conversationRepository.updateLastMessage(
                    state.conversationId, messageId, text, msg.createTime
                )
                refreshMessages(scrollToBottom = true)

                val conv = conversationRepository.getConversation(state.conversationId)
                val receiverId = resolveSingleReceiverId(conv, userId)
                val groupId = if (conv?.type == com.lightchat.model.ConversationType.GROUP) conv.targetId else null
                val sent = withContext(Dispatchers.IO) {
                    app.imClient.sendMessage(
                        state.conversationId,
                        MessageType.TEXT.value,
                        text,
                        clientSeq,
                        messageId,
                        msg.sendTime,
                        receiverId,
                        groupId,
                        msg.extra
                    )
                }
                if (!sent) {
                    messageRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages.map { m ->
                            if (m.messageId == messageId) m.copy(status = MessageStatus.FAILED) else m
                        },
                        statusChangeVersion = _uiState.value.statusChangeVersion + 1
                    )
                    return@launch
                }
            } catch (e: Exception) {
                messageRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
                _uiState.value = _uiState.value.let { state ->
                    state.copy(
                        messages = state.messages.map { m ->
                            if (m.messageId == messageId) m.copy(status = MessageStatus.FAILED) else m
                        },
                        statusChangeVersion = state.statusChangeVersion + 1
                    )
                }
                return@launch
            }

            val timeoutJob = viewModelScope.launch {
                delay(3_000)
                val current = messageRepository.getMessageById(messageId)
                if (current != null && current.status == MessageStatus.SENDING) {
                    messageRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages.map { m ->
                            if (m.messageId == messageId) m.copy(status = MessageStatus.FAILED) else m
                        },
                        statusChangeVersion = _uiState.value.statusChangeVersion + 1
                    )
                }
                ackTimeoutJobs.remove(messageId)
            }
            ackTimeoutJobs[messageId] = timeoutJob
        }
    }

    fun sendMultipleImages(
        uris: List<Uri>,
        doodlesPerImage: Map<Int, List<com.lightchat.ui.chat.DoodlePath>> = emptyMap(),
        conversationId: String? = null
    ) {
        app.applicationScope.launch {
            for (uri in uris) {
                sendImageMessageSuspend(uri, conversationId)
            }
        }
    }

    fun refreshVisibleMessages(scrollToBottom: Boolean = false) {
        viewModelScope.launch {
            refreshMessages(clearInput = false, scrollToBottom = scrollToBottom)
        }
    }

    fun getConversationImageMessages(): List<Message> {
        return _uiState.value.messages.filter {
            it.messageType == MessageType.IMAGE && !it.isRecalled
        }
    }

    fun sendImageMessage(uri: Uri, targetConversationId: String? = null) {
        app.applicationScope.launch {
            sendImageMessageSuspend(uri, targetConversationId)
        }
    }

    private suspend fun sendImageMessageSuspend(uri: Uri, targetConversationId: String? = null) {
        val currentState = _uiState.value
        val conversationId = targetConversationId?.takeIf { it.isNotBlank() }
            ?: currentState.conversationId.takeIf { it.isNotBlank() }
            ?: app.currentOpenConversationId.orEmpty()
        if (conversationId.isBlank()) return
        val state = currentState.copy(conversationId = conversationId)
        val userId = userSession.currentUserId ?: return
        val messageId = UUID.randomUUID().toString()
        val clientSeq = ++clientSeqCounter

        val imageFile = withContext(Dispatchers.IO) {
            compressImage(uri, messageId)
        } ?: return
        val localPath = imageFile.absolutePath
        val thumbnailPath = com.lightchat.ui.chat.generateThumbnail(localPath)
        val localExtra = org.json.JSONObject()
            .put("fileName", "image.jpg")
            .put("localPath", localPath)
            .apply { thumbnailPath?.let { put("thumbnailPath", it) } }
            .toString()
        val msg = Message(
            messageId = messageId,
            conversationId = state.conversationId,
            senderId = userId,
            messageType = MessageType.IMAGE,
            content = localPath,
            status = MessageStatus.SENDING,
            clientSeq = clientSeq,
            sendTime = System.currentTimeMillis(),
            createTime = System.currentTimeMillis(),
            extra = localExtra
        )

        messageRepository.sendMessage(msg)
        conversationRepository.updateLastMessage(
            state.conversationId, messageId, "[图片]", msg.createTime, thumbnailPath
        )
        refreshMessages(scrollToBottom = true)

        try {
            val token = userSession.currentUserId?.let { app.tokenManager.getToken() } ?: ""
            val imageBytes = withContext(Dispatchers.IO) { imageFile.readBytes() }
            val thumbnailBytes = thumbnailPath?.let { withContext(Dispatchers.IO) { File(it).readBytes() } }
            val uploadedImage = withContext(Dispatchers.IO) {
                runCatching { authApiClient.uploadImage(imageBytes, token, thumbnailBytes) }.getOrNull()
            }
            if (uploadedImage == null) {
                messageRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
                applyFailedStatusToUi(messageId)
                return
            }
            val sendExtra = org.json.JSONObject()
                .put("fileName", "image.jpg")
                .put("localPath", localPath)
                .apply {
                    thumbnailPath?.let { put("thumbnailPath", it) }
                    put("fileId", uploadedImage.fileId)
                    put("imageUrl", uploadedImage.imageUrl)
                    put("thumbnailUrl", uploadedImage.thumbnailUrl)
                    put("objectKey", uploadedImage.objectKey)
                    put("thumbnailObjectKey", uploadedImage.thumbnailObjectKey)
                    put("storageProvider", uploadedImage.storageProvider)
                }
                .toString()
            app.messageDao.updateExtra(messageId, sendExtra)

            val conv = conversationRepository.getConversation(state.conversationId)
            val receiverId = resolveSingleReceiverId(conv, userId)
            val groupId = if (conv?.type == com.lightchat.model.ConversationType.GROUP) conv.targetId else null
            val sent = withContext(Dispatchers.IO) {
                app.imClient.sendMessage(
                    state.conversationId,
                    MessageType.IMAGE.value,
                    "[图片]",
                    clientSeq,
                    messageId,
                    msg.sendTime,
                    receiverId,
                    groupId,
                    sendExtra
                )
            }
            if (!sent) {
                messageRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
                applyFailedStatusToUi(messageId)
                return
            }
        } catch (e: Exception) {
            Log.e("LightChat", "sendImage: exception in send flow", e)
            messageRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
            applyFailedStatusToUi(messageId)
            return
        }

        val timeoutJob = viewModelScope.launch {
            delay(3_000)
            try {
                val current = messageRepository.getMessageById(messageId)
                if (current != null && current.status == MessageStatus.SENDING) {
                    messageRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
                    applyFailedStatusToUi(messageId)
                }
            } catch (_: Exception) { } finally {
                ackTimeoutJobs.remove(messageId)
            }
        }
        ackTimeoutJobs[messageId] = timeoutJob
    }

    private fun compressImage(uri: Uri, messageId: String): File? {
        return try {
            val context = app.applicationContext
            val cacheDir = File(context.filesDir, "images")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.readBytes()
            inputStream.close()

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            val maxDim = 1280
            val sampleSize = calculateBitmapInSampleSize(opts.outWidth, opts.outHeight, maxDim)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }

            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null

            if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                val newWidth = (bitmap.width * ratio).toInt()
                val newHeight = (bitmap.height * ratio).toInt()
                bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            }

            val file = File(cacheDir, "${messageId}.jpg")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            if (bytes.size > opts.outWidth * opts.outHeight / 2) {
                bitmap.recycle()
            }

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun sendUserCard(targetUserId: String) {
        val state = _uiState.value
        val userId = userSession.currentUserId ?: return
        val user = app.userDao.getById(targetUserId) ?: return
        if (!app.imClient.isAuthenticated()) {
            app.showToast("网络未连接，发送失败")
            return
        }
        val messageId = UUID.randomUUID().toString()
        val clientSeq = ++clientSeqCounter

        viewModelScope.launch {
            val msg = Message(
                messageId = messageId,
                conversationId = state.conversationId,
                senderId = userId,
                messageType = MessageType.USER_CARD,
                content = user.nickname,
                status = MessageStatus.SENDING,
                clientSeq = clientSeq,
                sendTime = System.currentTimeMillis(),
                createTime = System.currentTimeMillis(),
                extra = org.json.JSONObject()
                    .put("userId", user.userId)
                    .put("nickname", user.nickname)
                    .put("avatar", user.avatar)
                    .put("avatarUrl", user.avatarUrl)
                    .put("avatarVersion", user.avatarVersion)
                    .toString()
            )

            messageRepository.sendMessage(msg)
            conversationRepository.updateLastMessage(
                state.conversationId, messageId, "[名片]${user.nickname}", msg.createTime
            )
            refreshMessages(scrollToBottom = true)

            val conv = conversationRepository.getConversation(state.conversationId)
            val receiverId = resolveSingleReceiverId(conv, userId)
            val groupId = if (conv?.type == com.lightchat.model.ConversationType.GROUP) conv.targetId else null
            val sent = app.imClient.sendMessage(
                state.conversationId,
                MessageType.USER_CARD.value,
                user.nickname,
                clientSeq,
                messageId,
                msg.sendTime,
                receiverId,
                groupId,
                msg.extra
            )
            if (!sent) {
                messageRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
                applyFailedStatusToUi(messageId)
                return@launch
            }

            val timeoutJob = viewModelScope.launch {
                delay(3_000)
                val current = messageRepository.getMessageById(messageId)
                if (current != null && current.status == MessageStatus.SENDING) {
                    messageRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
                    applyFailedStatusToUi(messageId)
                }
                ackTimeoutJobs.remove(messageId)
            }
            ackTimeoutJobs[messageId] = timeoutJob
        }
    }

    fun sendGroupCard(targetGroupId: String) {
        val state = _uiState.value
        val userId = userSession.currentUserId ?: return
        val group = app.groupDao.getGroupById(targetGroupId) ?: return
        if (!app.imClient.isAuthenticated()) {
            app.showToast("网络未连接，发送失败")
            return
        }
        val messageId = UUID.randomUUID().toString()
        val clientSeq = ++clientSeqCounter

        viewModelScope.launch {
            val msg = Message(
                messageId = messageId,
                conversationId = state.conversationId,
                senderId = userId,
                messageType = MessageType.GROUP_CARD,
                content = group.groupName,
                status = MessageStatus.SENDING,
                clientSeq = clientSeq,
                sendTime = System.currentTimeMillis(),
                createTime = System.currentTimeMillis(),
                extra = org.json.JSONObject()
                    .put("groupId", group.groupId)
                    .put("groupName", group.groupName)
                    .toString()
            )

            messageRepository.sendMessage(msg)
            conversationRepository.updateLastMessage(
                state.conversationId, messageId, "[群名片]${group.groupName}", msg.createTime
            )
            refreshMessages(scrollToBottom = true)

            val conv = conversationRepository.getConversation(state.conversationId)
            val receiverId = resolveSingleReceiverId(conv, userId)
            val groupId = if (conv?.type == com.lightchat.model.ConversationType.GROUP) conv.targetId else null
            val sent = app.imClient.sendMessage(
                state.conversationId,
                MessageType.GROUP_CARD.value,
                group.groupName,
                clientSeq,
                messageId,
                msg.sendTime,
                receiverId,
                groupId,
                msg.extra
            )
            if (!sent) {
                messageRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
                applyFailedStatusToUi(messageId)
                return@launch
            }

            val timeoutJob = viewModelScope.launch {
                delay(3_000)
                val current = messageRepository.getMessageById(messageId)
                if (current != null && current.status == MessageStatus.SENDING) {
                    messageRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
                    applyFailedStatusToUi(messageId)
                }
                ackTimeoutJobs.remove(messageId)
            }
            ackTimeoutJobs[messageId] = timeoutJob
        }
    }

    fun resendMessage(messageId: String) {
        val msg = messageRepository.getMessageById(messageId) ?: return
        if (msg.status != MessageStatus.FAILED) return

        viewModelScope.launch {
            messageRepository.updateMessageStatus(messageId, MessageStatus.SENDING)
            refreshMessages(scrollToBottom = true)

            var extraToSend = msg.extra
            if (msg.messageType == MessageType.IMAGE) {
                val extraJson = try { org.json.JSONObject(msg.extra) } catch (_: Exception) { org.json.JSONObject() }
                val hasImageUrl = extraJson.optString("imageUrl", "").isNotBlank()
                val hasThumbnailUrl = extraJson.optString("thumbnailUrl", "").isNotBlank()
                if (!hasImageUrl || !hasThumbnailUrl) {
                    val localPath = extraJson.optString("localPath", "").ifBlank { msg.content }
                    val imageFile = File(localPath)
                    if (imageFile.exists()) {
                        try {
                            val token = userSession.currentUserId?.let { app.tokenManager.getToken() } ?: ""
                            val imageBytes = withContext(Dispatchers.IO) { imageFile.readBytes() }
                            val thumbnailPath = extraJson.optString("thumbnailPath", "")
                            val thumbnailBytes = if (thumbnailPath.isNotBlank()) {
                                val thumbFile = File(thumbnailPath)
                                if (thumbFile.exists()) withContext(Dispatchers.IO) { thumbFile.readBytes() } else null
                            } else null
                            val uploaded = withContext(Dispatchers.IO) {
                                runCatching { authApiClient.uploadImage(imageBytes, token, thumbnailBytes) }.getOrNull()
                            }
                            if (uploaded != null) {
                                extraJson.put("fileId", uploaded.fileId)
                                extraJson.put("imageUrl", uploaded.imageUrl)
                                extraJson.put("thumbnailUrl", uploaded.thumbnailUrl)
                                extraJson.put("objectKey", uploaded.objectKey)
                                extraJson.put("thumbnailObjectKey", uploaded.thumbnailObjectKey)
                                extraJson.put("storageProvider", uploaded.storageProvider)
                                extraToSend = extraJson.toString()
                                app.messageDao.updateExtra(messageId, extraToSend)
                            }
                        } catch (_: Exception) { }
                    }
                }
            }

            val clientSeq = ++clientSeqCounter
            val conv = conversationRepository.getConversation(msg.conversationId)
            val receiverId = resolveSingleReceiverId(conv, userSession.currentUserId ?: "")
            val groupId = if (conv?.type == com.lightchat.model.ConversationType.GROUP) conv.targetId else null
            val sent = app.imClient.sendMessage(
                msg.conversationId,
                msg.messageType.value,
                resendContent(msg),
                clientSeq,
                messageId,
                msg.sendTime,
                receiverId,
                groupId,
                extraToSend
            )
            if (!sent) {
                messageRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
                applyFailedStatusToUi(messageId)
                return@launch
            }

            val timeoutJob = viewModelScope.launch {
                delay(3_000)
                val current = messageRepository.getMessageById(messageId)
                if (current != null && current.status == MessageStatus.SENDING) {
                    messageRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
                    applyFailedStatusToUi(messageId)
                }
                ackTimeoutJobs.remove(messageId)
            }
            ackTimeoutJobs[messageId] = timeoutJob
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            val message = app.messageDao.getById(messageId) ?: return@launch
            val conversationId = message.conversationId
            app.messageDao.delete(messageId)
            val latest = app.messageDao.getLatestMessages(conversationId, 1).firstOrNull()
            if (latest != null) {
                val displayContent = when (latest.messageType) {
                    com.lightchat.model.MessageType.IMAGE -> "[图片]"
                    com.lightchat.model.MessageType.USER_CARD -> "[名片]${latest.content}"
                    com.lightchat.model.MessageType.GROUP_CARD -> "[群名片]${latest.content}"
                    com.lightchat.model.MessageType.MERGE_FORWARD -> "[聊天记录]"
                    else -> latest.content
                }
                app.conversationRepository.updateLastMessage(conversationId, latest.messageId, displayContent, latest.createTime, null)
            } else {
                app.conversationRepository.updateLastMessage(conversationId, "", "", 0L)
            }
            refreshMessages()
        }
    }

    fun recallMessage(messageId: String) {
        val msg = messageRepository.getMessageById(messageId) ?: return
        if (msg.senderId != userSession.currentUserId) return
        val elapsed = System.currentTimeMillis() - msg.sendTime
        if (elapsed > 120_000) return // 2-minute window

        app.imClient.recallMessage(messageId, msg.conversationId)
    }

    private fun applyLocalRecall(messageId: String) {
        val msg = messageRepository.getMessageById(messageId) ?: return
        viewModelScope.launch {
            val cv = android.content.ContentValues().apply {
                put("is_recalled", 1)
                put("content", "消息已撤回")
                put("extra", buildRecalledExtra(msg))
            }
            app.databaseHelper.writableDatabase.update(
                "message",
                cv,
                "owner_user_id = ? AND message_id = ?",
                arrayOf(app.databaseHelper.currentOwnerId(), messageId)
            )
            val conv = conversationRepository.getConversation(msg.conversationId)
            if (conv?.lastMessageId == messageId) {
                conversationRepository.updateLastMessage(
                    msg.conversationId, messageId, "消息已撤回", System.currentTimeMillis()
                )
            }
            refreshMessages()
        }
    }

    fun reeditRecalledMessage(messageId: String) {
        val msg = messageRepository.getMessageById(messageId) ?: return
        if (msg.senderId != userSession.currentUserId || msg.messageType != MessageType.TEXT) return
        val recalledText = parseRecalledContent(msg.extra).ifBlank { return }
        _uiState.value = _uiState.value.copy(
            inputText = recalledText,
            focusInputRequest = _uiState.value.focusInputRequest + 1
        )
    }

    private fun handleAck(messageId: String, status: Int, conversationSeq: Long) {
        ackTimeoutJobs[messageId]?.cancel()
        ackTimeoutJobs.remove(messageId)

        viewModelScope.launch {
            val newStatus = when (status) {
                0 -> MessageStatus.SENT
                1 -> MessageStatus.DELIVERED
                2 -> MessageStatus.READ
                else -> MessageStatus.FAILED
            }
            app.messageDao.updateStatusAndConversationSeq(messageId, newStatus, conversationSeq)
            refreshMessages()
        }
    }

    private fun handleError(code: Int, message: String) {
        viewModelScope.launch {
            val currentConvId = _uiState.value.conversationId
            if (currentConvId.isBlank()) return@launch
            val pending = app.messageDao.getMessagesByStatus(currentConvId, MessageStatus.SENDING.value)
            for (msg in pending) {
                app.messageDao.updateStatusAndConversationSeq(msg.messageId, MessageStatus.FAILED, 0)
            }
            if (pending.isNotEmpty()) applyFailedStatusToUi(pending.map { it.messageId })
        }
    }

    private suspend fun refreshMessages(clearInput: Boolean = true, scrollToBottom: Boolean = false) {
        val state = _uiState.value
        if (state.hasNewerMessages && !scrollToBottom) {
            val refreshed = withContext(Dispatchers.IO) {
                state.messages.mapNotNull { messageRepository.getMessageById(it.messageId) }
            }
            _uiState.value = state.copy(
                messages = refreshed,
                groupReadCounts = buildGroupReadCounts(conversationRepository.getConversation(state.conversationId), refreshed),
                inputText = if (clearInput) "" else state.inputText
            )
            return
        }
        val msgs = messageRepository.getMessages(state.conversationId)
        val totalCount = messageRepository.getMessageCount(state.conversationId)
        _uiState.value = state.copy(
            messages = msgs,
            groupReadCounts = buildGroupReadCounts(conversationRepository.getConversation(state.conversationId), msgs),
            hasMoreMessages = msgs.size < totalCount,
            isLoadingMoreMessages = false,
            hasNewerMessages = false,
            isLoadingNewerMessages = false,
            isPositionedMidConversation = false,
            totalMessageCount = totalCount,
            inputText = if (clearInput) "" else state.inputText,
            scrollToBottomRequest = if (scrollToBottom) state.scrollToBottomRequest + 1 else state.scrollToBottomRequest
        )
    }

    private fun buildGroupReadCounts(conv: Conversation?, messages: List<Message>): Map<String, GroupReadCount> {
        if (conv?.type != ConversationType.GROUP || messages.isEmpty()) return emptyMap()
        val currentUserId = userSession.currentUserId ?: return emptyMap()
        val memberNames = app.groupDao.getMembers(conv.targetId)
            .associate { it.userId to it.nickname.ifBlank { it.userId } }
        val otherMemberIds = memberNames.keys
            .filter { it.isNotBlank() && it != currentUserId }
            .distinct()
        if (otherMemberIds.isEmpty()) return emptyMap()
        val readSeqs = app.groupDao.getConversationReadSeqs(conv.conversationId)
        return messages
            .filter { it.senderId == currentUserId && it.conversationSeq > 0L && !it.isRecalled }
            .associate { message ->
                val readCount = otherMemberIds.count { memberId ->
                    (readSeqs[memberId] ?: 0L) >= message.conversationSeq
                }
                val mentionedIds = mentionedUserIds(message.extra)
                    .filter { it != currentUserId && it in otherMemberIds }
                    .distinct()
                val mentionedReadNames = mentionedIds
                    .filter { memberId -> (readSeqs[memberId] ?: 0L) >= message.conversationSeq }
                    .map { memberId -> memberNames[memberId].orEmpty().ifBlank { memberId } }
                val mentionedUnreadNames = mentionedIds
                    .filter { memberId -> (readSeqs[memberId] ?: 0L) < message.conversationSeq }
                    .map { memberId -> memberNames[memberId].orEmpty().ifBlank { memberId } }
                message.messageId to GroupReadCount(
                    readCount = readCount,
                    totalCount = otherMemberIds.size,
                    mentionedReadNames = mentionedReadNames,
                    mentionedUnreadNames = mentionedUnreadNames,
                    hasMentionTargets = mentionedIds.isNotEmpty()
                )
            }
    }

    private fun mentionedUserIds(extra: String?): List<String> {
        return try {
            val ids = org.json.JSONObject(extra ?: "{}").optJSONArray("atUserIds") ?: return emptyList()
            (0 until ids.length()).mapNotNull { ids.optString(it).takeIf { id -> id.isNotBlank() } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildRecalledExtra(msg: Message): String {
        val original = msg.extra
        val obj = try {
            org.json.JSONObject(original ?: "{}")
        } catch (_: Exception) {
            org.json.JSONObject()
        }
        if (msg.messageType == MessageType.TEXT) {
            obj.put("recalledContent", msg.content)
        }
        return obj.toString()
    }

    private fun resendContent(msg: Message): String {
        return when (msg.messageType) {
            MessageType.IMAGE -> "[图片]"
            else -> msg.content
        }
    }

    private fun parseRecalledContent(extra: String?): String {
        return try {
            org.json.JSONObject(extra ?: "{}").optString("recalledContent", "")
        } catch (_: Exception) {
            ""
        }
    }

    private fun markCurrentConversationRead() {
        if (!app.isAppForeground) return
        val state = _uiState.value
        if (state.conversationId.isBlank() || state.messages.isEmpty() || !app.imClient.isAuthenticated()) return
        val currentUserId = userSession.currentUserId ?: return
        val maxConvSeq = state.messages
            .filter { it.senderId != currentUserId }
            .maxOfOrNull { it.conversationSeq } ?: return
        if (maxConvSeq <= 0L) return
        val localReadSeq = app.groupDao.getConversationReadSeq(state.conversationId, currentUserId)
        val lastSeq = maxOf(lastReportedReadSeq[state.conversationId] ?: 0L, localReadSeq)
        if (maxConvSeq <= lastSeq) return
        val hasUnreadIncoming = state.messages.any {
            it.senderId != currentUserId &&
                it.conversationSeq > lastSeq &&
                it.status != MessageStatus.READ
        }
        if (!hasUnreadIncoming) {
            lastReportedReadSeq[state.conversationId] = maxConvSeq
            app.groupDao.upsertConversationMemberRead(state.conversationId, currentUserId, maxConvSeq)
            return
        }
        val sent = app.imClient.markRead(state.conversationId, maxConvSeq)
        if (!sent) return
        lastReportedReadSeq[state.conversationId] = maxConvSeq
        app.groupDao.upsertConversationMemberRead(state.conversationId, currentUserId, maxConvSeq)
        state.messages
            .filter { it.senderId != currentUserId && it.conversationSeq <= maxConvSeq }
            .map { it.senderId }
            .distinct()
            .forEach { senderId ->
                app.messageDao.markConversationMessagesRead(state.conversationId, senderId, maxConvSeq)
            }
    }

    private fun applyFailedStatusToUi(messageId: String) {
        val currentState = _uiState.value
        val found = currentState.messages.any { it.messageId == messageId }
        Log.d("LightChat", "applyFailedStatusToUi: msgId=${messageId.take(8)} foundInState=$found msgCount=${currentState.messages.size} version=${currentState.statusChangeVersion}")
        _uiState.value = currentState.copy(
            messages = currentState.messages.map { m ->
                if (m.messageId == messageId) m.copy(status = MessageStatus.FAILED) else m
            },
            statusChangeVersion = currentState.statusChangeVersion + 1
        )
    }

    private fun applyFailedStatusToUi(messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        val idSet = messageIds.toSet()
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            messages = currentState.messages.map { m ->
                if (m.messageId in idSet) m.copy(status = MessageStatus.FAILED) else m
            },
            statusChangeVersion = currentState.statusChangeVersion + 1
        )
    }

    private fun resolveSingleReceiverId(conv: Conversation?, currentUserId: String): String? {
        if (conv?.type != com.lightchat.model.ConversationType.SINGLE) return null
        if (conv.targetId.isNotBlank() && conv.targetId != currentUserId) return conv.targetId
        val parts = Regex("^single_(.+)_(.+)$").find(conv.conversationId)?.groupValues ?: return null
        return listOf(parts[1], parts[2]).firstOrNull { it != currentUserId }
    }

    private fun resolveConversationTitle(conv: Conversation?): String {
        if (conv == null) return ""
        if (conv.type != com.lightchat.model.ConversationType.SINGLE) return conv.title
        val currentUserId = userSession.currentUserId
        val targetId = if (conv.targetId.isNotBlank() && conv.targetId != currentUserId) {
            conv.targetId
        } else {
            Regex("^single_(.+)_(.+)$").find(conv.conversationId)?.groupValues
                ?.drop(1)
                ?.firstOrNull { it != currentUserId }
                ?: conv.targetId
        }
        val user = app.userDao.getById(targetId)
        val title = user?.nickname?.takeIf { it.isNotBlank() && it != targetId }
            ?: conv.title.takeIf { it.isNotBlank() && it != conv.targetId }
            ?: targetId
        if (targetId.isNotBlank() && (conv.targetId != targetId || conv.title != title || conv.avatar != user?.avatar.orEmpty())) {
            app.conversationDao.updateSingleDisplayInfo(conv.conversationId, targetId, title, user?.avatar.orEmpty(), user?.avatarUrl ?: "", user?.avatarVersion ?: 0)
        }
        return title
    }

    private suspend fun refreshPeerProfileWhenSingle(conv: Conversation?) {
        if (conv?.type != ConversationType.SINGLE) return
        val targetId = resolveSingleReceiverId(conv, userSession.currentUserId ?: "") ?: return
        val token = app.tokenManager.getToken() ?: return
        val freshUser = runCatching {
            withContext(Dispatchers.IO) {
                authApiClient.getUserProfile(targetId, token)
            }
        }.getOrNull() ?: return

        upsertFetchedUser(freshUser)
        if (freshUser.avatarUrl.isNotBlank()) {
            com.lightchat.ui.components.AvatarCacheLoader.loadAvatar(
                context = app.applicationContext,
                userId = freshUser.userId,
                avatarUrl = freshUser.avatarUrl,
                avatarVersion = freshUser.avatarVersion,
                avatarFallback = "",
                allowNetwork = true
            )
        }
        val title = freshUser.nickname
            .takeIf { it.isNotBlank() && it != freshUser.userId }
            ?: conv.title.takeIf { it.isNotBlank() && it != conv.targetId }
            ?: freshUser.userId
        app.conversationDao.updateSingleDisplayInfo(conv.conversationId, freshUser.userId, title, freshUser.avatar, freshUser.avatarUrl, freshUser.avatarVersion)
        _uiState.value = _uiState.value.copy(title = title)
        AppEvents.notifyConversationChanged(conv.conversationId)
        AppEvents.notifyUserChanged(freshUser.userId)
    }

    private fun upsertFetchedUser(user: User) {
        app.userDao.upsertPreservingExisting(user)
    }

    override fun onCleared() {
        super.onCleared()
        if (app.currentOpenConversationId == _uiState.value.conversationId) {
            app.currentOpenConversationId = null
        }
        ackTimeoutJobs.values.forEach { it.cancel() }
        ackTimeoutJobs.clear()
    }
}

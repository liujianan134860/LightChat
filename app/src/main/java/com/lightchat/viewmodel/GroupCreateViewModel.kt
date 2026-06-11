package com.lightchat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightchat.LightChatApplication
import com.lightchat.model.Conversation
import com.lightchat.model.ConversationId
import com.lightchat.model.ConversationType
import com.lightchat.model.ImGroup
import com.lightchat.model.MemberRole
import com.lightchat.model.GroupMember
import com.lightchat.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class GroupCreateUiState(
    val friends: List<User> = emptyList(),
    val selectedMemberIds: Set<String> = emptySet(),
    val groupName: String = "",
    val isCreating: Boolean = false,
    val isCreated: Boolean = false,
    val createdGroupId: String = "",
    val createdGroupName: String = "",
    val errorMessage: String = ""
)

class GroupCreateViewModel : ViewModel() {

    private val userRepository = LightChatApplication.instance.userRepository
    private val groupDao = LightChatApplication.instance.groupDao
    private val conversationRepository = LightChatApplication.instance.conversationRepository
    private val userSession = LightChatApplication.instance.userSession
    private val app = LightChatApplication.instance
    private var pendingGroup: PendingGroup? = null

    private val groupCreateAckListener: (String) -> Unit = { groupId ->
        val pending = pendingGroup
        if (pending != null && pending.groupId == groupId) {
            persistLocalGroup(pending)
            app.syncManager.requestSync()
            pendingGroup = null
            _uiState.value = _uiState.value.copy(
                isCreating = false,
                isCreated = true,
                createdGroupId = pending.groupId,
                createdGroupName = pending.groupName
            )
        }
    }

    private val errorListener: (Int, String) -> Unit = { _, message ->
        if (_uiState.value.isCreating) {
            pendingGroup = null
            _uiState.value = _uiState.value.copy(
                isCreating = false,
                errorMessage = message
            )
        }
    }

    private val _uiState = MutableStateFlow(GroupCreateUiState())
    val uiState: StateFlow<GroupCreateUiState> = _uiState.asStateFlow()

    init {
        app.imClient.onGroupCreateAck(groupCreateAckListener)
        app.imClient.onError(errorListener)
        loadFriends()
    }

    override fun onCleared() {
        app.imClient.removeGroupCreateAckListener(groupCreateAckListener)
        app.imClient.removeErrorListener(errorListener)
        super.onCleared()
    }

    fun loadFriends() {
        val friends = userRepository.getFriends()
        _uiState.value = GroupCreateUiState(friends = friends)
    }

    fun toggleMember(userId: String) {
        val current = _uiState.value.selectedMemberIds
        _uiState.value = _uiState.value.copy(
            selectedMemberIds = if (userId in current) current - userId else current + userId
        )
    }

    fun setSelectedMembers(userIds: Set<String>) {
        _uiState.value = _uiState.value.copy(selectedMemberIds = userIds)
    }

    fun onGroupNameChange(name: String) {
        _uiState.value = _uiState.value.copy(groupName = name)
    }

    fun createGroup() {
        val state = _uiState.value
        if (state.selectedMemberIds.size < 2) return

        val currentUserId = userSession.currentUserId ?: return
        val currentNickname = userSession.currentNickname ?: "我"

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, errorMessage = "")

            val groupId = "g${UUID.randomUUID().toString().take(8)}"
            val groupName = state.groupName.ifBlank {
                val selectedNames = state.friends
                    .filter { it.userId in state.selectedMemberIds }
                    .take(3)
                    .joinToString(", ") { it.nickname }
                if (selectedNames.isNotEmpty()) selectedNames else "新建群聊"
            }
            val allMemberIds = state.selectedMemberIds + currentUserId
            val currentUser = app.userDao.getById(currentUserId)
            val pending = PendingGroup(
                groupId = groupId,
                groupName = groupName,
                ownerId = currentUserId,
                ownerNickname = currentNickname,
                ownerAvatarUrl = currentUser?.avatarUrl ?: "",
                ownerAvatarVersion = currentUser?.avatarVersion ?: 0,
                memberIds = allMemberIds,
                friends = state.friends.filter { it.userId in state.selectedMemberIds }
            )
            pendingGroup = pending

            // Send to server first
            val sentToServer = app.imClient.createGroup(groupId, groupName, allMemberIds.toList())

            if (!sentToServer) {
                pendingGroup = null
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    errorMessage = "连接服务端后才能发起群聊"
                )
            }
        }
    }

    fun consumeCreatedState() {
        _uiState.value = _uiState.value.copy(isCreated = false)
    }

    private fun persistLocalGroup(pending: PendingGroup) {
        groupDao.insertGroup(
            ImGroup(
                groupId = pending.groupId,
                groupName = pending.groupName,
                ownerId = pending.ownerId,
                memberCount = pending.memberIds.size
            )
        )
        groupDao.insertMember(
            GroupMember(
                groupId = pending.groupId,
                userId = pending.ownerId,
                nickname = pending.ownerNickname,
                avatarUrl = pending.ownerAvatarUrl,
                avatarVersion = pending.ownerAvatarVersion,
                role = MemberRole.OWNER
            )
        )
        groupDao.insertMembers(
            pending.friends.map { friend ->
                GroupMember(
                    groupId = pending.groupId,
                    userId = friend.userId,
                    nickname = friend.nickname,
                    avatar = friend.avatar,
                    avatarUrl = friend.avatarUrl,
                    avatarVersion = friend.avatarVersion
                )
            }
        )
        conversationRepository.saveConversation(
            Conversation(
                conversationId = ConversationId.group(pending.groupId),
                type = ConversationType.GROUP,
                targetId = pending.groupId,
                title = pending.groupName,
                lastMessageContent = "群聊已创建",
                lastMessageTime = System.currentTimeMillis()
            )
        )
    }

    private data class PendingGroup(
        val groupId: String,
        val groupName: String,
        val ownerId: String,
        val ownerNickname: String,
        val ownerAvatarUrl: String = "",
        val ownerAvatarVersion: Int = 0,
        val memberIds: Set<String>,
        val friends: List<User>
    )
}

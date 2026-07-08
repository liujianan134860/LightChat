package com.lightchat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightchat.data.local.dao.GroupDao
import com.lightchat.domain.repository.UserRepositoryContract
import com.lightchat.event.AppEvents
import com.lightchat.model.ImGroup
import com.lightchat.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class ContactUiState(
    val friends: List<User> = emptyList(),
    val searchFriends: List<User> = emptyList(),
    val groups: List<ImGroup> = emptyList(),
    val selectedFriendIds: Set<String> = emptySet(),
    val query: String = "",
    val isSelectionMode: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false
) {
    val filteredFriends: List<User>
        get() = if (query.isBlank()) friends else searchFriends

    val filteredGroups: List<ImGroup>
        get() = if (query.isBlank()) groups else groups.filter {
            it.groupName.contains(query, ignoreCase = true) ||
                it.groupId.contains(query, ignoreCase = true)
        }
}

@HiltViewModel
class ContactViewModel @Inject constructor(
    private val userRepository: UserRepositoryContract,
    private val groupDao: GroupDao
) : ViewModel() {
    private val pageSize = 50

    private val _uiState = MutableStateFlow(ContactUiState())
    val uiState: StateFlow<ContactUiState> = _uiState.asStateFlow()

    init {
        loadFriends()
        viewModelScope.launch {
            AppEvents.friendRequestChanged.collect {
                loadFriends()
            }
        }
    }

    fun loadFriends() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val friends = userRepository.getFriendsPage(pageSize, 0)
            val total = userRepository.getFriendCount()
            _uiState.value = ContactUiState(
                friends = friends,
                searchFriends = if (_uiState.value.query.isBlank()) emptyList() else userRepository.searchFriends(_uiState.value.query),
                groups = groupDao.getCurrentOwnerGroups(),
                query = _uiState.value.query,
                hasMore = friends.size < total
            )
        }
    }

    fun loadMoreFriends() {
        val state = _uiState.value
        if (state.query.isNotBlank()) return
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true)
            val nextPage = userRepository.getFriendsPage(pageSize, state.friends.size)
            val merged = (state.friends + nextPage).distinctBy { it.userId }
            val total = userRepository.getFriendCount()
            _uiState.value = state.copy(
                friends = merged,
                isLoadingMore = false,
                hasMore = merged.size < total
            )
        }
    }

    fun enterSelectionMode() {
        _uiState.value = _uiState.value.copy(isSelectionMode = true, selectedFriendIds = emptySet())
    }

    fun exitSelectionMode() {
        _uiState.value = _uiState.value.copy(isSelectionMode = false, selectedFriendIds = emptySet())
    }

    fun toggleFriendSelection(userId: String) {
        val current = _uiState.value.selectedFriendIds
        _uiState.value = _uiState.value.copy(
            selectedFriendIds = if (userId in current) current - userId else current + userId
        )
    }

    fun onQueryChange(query: String) {
        val normalized = query.trim()
        viewModelScope.launch {
            val searchResult = if (normalized.isBlank()) emptyList() else userRepository.searchFriends(normalized)
            _uiState.value = _uiState.value.copy(
                query = query,
                searchFriends = searchResult
            )
        }
    }
}

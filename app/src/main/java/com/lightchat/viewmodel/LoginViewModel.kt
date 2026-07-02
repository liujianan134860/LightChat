package com.lightchat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightchat.data.local.TokenManager
import com.lightchat.data.repository.AuthRepository
import com.lightchat.im.ImClient
import com.lightchat.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val isRegisterMode: Boolean = false,
    val registerNickname: String = "",
    val isCheckingAuth: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val imClient: ImClient,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun onNicknameChange(value: String) {
        _uiState.value = _uiState.value.copy(registerNickname = value, errorMessage = null)
    }

    fun toggleRegisterMode() {
        _uiState.value = _uiState.value.copy(
            isRegisterMode = !_uiState.value.isRegisterMode,
            errorMessage = null
        )
    }

    fun login() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            delay(300) // brief visual feedback
            val result = withContext(Dispatchers.IO) {
                authRepository.login(state.username, state.password)
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true, isCheckingAuth = false)
                    connectImClient()
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message, isCheckingAuth = false)
                }
            )
        }
    }

    fun register() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            delay(300)
            val result = withContext(Dispatchers.IO) {
                authRepository.register(state.username, state.password, state.registerNickname)
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true, isCheckingAuth = false)
                    connectImClient()
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message, isCheckingAuth = false)
                }
            )
        }
    }

    private fun connectImClient() {
        val token = tokenManager.getToken()
        if (token != null) {
            imClient.connect(token)
            syncManager.start()
        }
    }

    fun checkLoginStatus() {
        val loggedIn = authRepository.isLoggedIn()
        _uiState.value = _uiState.value.copy(isLoggedIn = loggedIn, isCheckingAuth = false)
        if (loggedIn) {
            connectImClient()
        }
    }
}

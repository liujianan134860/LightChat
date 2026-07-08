package com.lightchat.domain.usecase

import com.lightchat.domain.repository.AuthRepositoryContract
import com.lightchat.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthUseCaseTest {
    private val repository = FakeAuthRepository()

    @Test
    fun loginRejectsBlankInputWithoutCallingRepository() {
        val result = LoginUseCase(repository)(" ", "password")

        assertTrue(result.isFailure)
        assertEquals(0, repository.loginCalls)
    }

    @Test
    fun loginTrimsUsernameAndDelegatesOnce() {
        val result = LoginUseCase(repository)(" alice ", "password")

        assertTrue(result.isSuccess)
        assertEquals("alice", repository.lastUsername)
        assertEquals(1, repository.loginCalls)
    }

    @Test
    fun registerRejectsMissingNickname() {
        val result = RegisterUseCase(repository)("alice", "password", " ")

        assertTrue(result.isFailure)
        assertEquals(0, repository.registerCalls)
    }

    private class FakeAuthRepository : AuthRepositoryContract {
        var loginCalls = 0
        var registerCalls = 0
        var lastUsername: String? = null

        override fun login(username: String, password: String): Result<User> {
            loginCalls++
            lastUsername = username
            return Result.success(User(username, username))
        }

        override fun register(username: String, password: String, nickname: String): Result<User> {
            registerCalls++
            return Result.success(User(username, nickname))
        }

        override fun logout() = Unit
        override fun isLoggedIn() = false
        override fun getCurrentUserId(): String? = null
    }
}

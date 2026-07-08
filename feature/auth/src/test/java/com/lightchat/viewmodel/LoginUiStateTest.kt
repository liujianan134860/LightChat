package com.lightchat.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginUiStateTest {
    @Test
    fun initialStateIsLoggedOutAndIdle() {
        val state = LoginUiState()

        assertFalse(state.isLoggedIn)
        assertFalse(state.isLoading)
        assertTrue(state.errorMessage == null)
    }

    @Test
    fun registrationModeRetainsIndependentNickname() {
        val state = LoginUiState(
            username = "alice",
            isRegisterMode = true,
            registerNickname = "Alice"
        )

        assertTrue(state.isRegisterMode)
        assertTrue(state.registerNickname == "Alice")
    }
}

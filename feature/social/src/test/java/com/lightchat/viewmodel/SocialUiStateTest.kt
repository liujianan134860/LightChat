package com.lightchat.viewmodel

import com.lightchat.model.User
import org.junit.Assert.assertEquals
import org.junit.Test

class SocialUiStateTest {
    @Test
    fun contactQueryUsesSearchResults() {
        val alice = User("alice", "Alice")
        val state = ContactUiState(
            friends = listOf(User("bob", "Bob")),
            searchFriends = listOf(alice),
            query = "ali"
        )
        assertEquals(listOf(alice), state.filteredFriends)
    }
}

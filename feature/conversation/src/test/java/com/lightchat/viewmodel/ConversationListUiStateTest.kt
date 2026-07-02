package com.lightchat.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationListUiStateTest {
    @Test
    fun emptyStateHasNoPaginationWork() {
        val state = ConversationListUiState()
        assertTrue(state.conversations.isEmpty())
        assertFalse(state.hasMore)
        assertFalse(state.isLoadingMore)
    }
}

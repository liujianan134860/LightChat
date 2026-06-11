package com.lightchat.ui.chat

import com.lightchat.model.Message
import com.lightchat.viewmodel.ChatUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatScrollControllerTest {

    @Test
    fun `content indexes include timestamp rows`() {
        val state = ChatUiState(
            messages = listOf(
                message("m1", 0),
                message("m2", 60_000),
                message("m3", 6 * 60_000)
            )
        )

        assertEquals(1, messageContentIndex(state, "m1"))
        assertEquals(2, messageContentIndex(state, "m2"))
        assertEquals(4, messageContentIndex(state, "m3"))
        assertEquals(4, lastConversationContentIndex(state))
    }

    @Test
    fun `loading older indicator shifts message indexes by one`() {
        val state = ChatUiState(
            messages = listOf(message("m1", 0), message("m2", 60_000)),
            isLoadingMoreMessages = true
        )

        assertEquals(2, messageContentIndex(state, "m1"))
        assertEquals(3, messageContentIndex(state, "m2"))
        assertEquals("m1", visibleMessageIdAtContentIndex(state, 2))
    }

    @Test
    fun `timestamp row does not resolve to a message id`() {
        val state = ChatUiState(
            messages = listOf(message("m1", 0), message("m2", 6 * 60_000))
        )

        assertNull(visibleMessageIdAtContentIndex(state, 2))
        assertEquals("m2", visibleMessageIdAtContentIndex(state, 3))
    }

    private fun message(id: String, createTime: Long) = Message(
        messageId = id,
        conversationId = "c1",
        senderId = "u1",
        content = id,
        createTime = createTime
    )
}

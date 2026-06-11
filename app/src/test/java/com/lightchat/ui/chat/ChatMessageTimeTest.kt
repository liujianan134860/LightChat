package com.lightchat.ui.chat

import com.lightchat.model.Message
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageTimeTest {

    @Test
    fun `first message always shows timestamp`() {
        assertTrue(shouldShowTimestamp(null, message(0)))
    }

    @Test
    fun `timestamp is hidden within five minute gap and shown at boundary`() {
        assertFalse(shouldShowTimestamp(message(0), message(5 * 60 * 1000L - 1)))
        assertTrue(shouldShowTimestamp(message(0), message(5 * 60 * 1000L)))
    }

    private fun message(createTime: Long) = Message(
        messageId = "m$createTime",
        conversationId = "c1",
        senderId = "u1",
        content = "hello",
        createTime = createTime
    )
}

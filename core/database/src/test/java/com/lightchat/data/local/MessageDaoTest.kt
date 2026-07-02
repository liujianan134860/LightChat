package com.lightchat.data.local

import androidx.test.core.app.ApplicationProvider
import com.lightchat.data.local.dao.MessageDao
import com.lightchat.model.Message
import com.lightchat.model.MessageStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageDaoTest {
    private lateinit var database: DatabaseHelper
    private lateinit var dao: MessageDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase("lightchat.db")
        UserSession(context).currentUserId = "owner-a"
        database = DatabaseHelper(context)
        dao = MessageDao(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun messagesAreIsolatedBySignedInOwner() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dao.insert(
            Message(
                messageId = "message-1",
                conversationId = "conversation-1",
                senderId = "owner-a",
                content = "hello",
                status = MessageStatus.SENT
            )
        )

        assertEquals("hello", dao.getById("message-1")?.content)

        UserSession(context).currentUserId = "owner-b"
        assertNull(dao.getById("message-1"))
    }
}

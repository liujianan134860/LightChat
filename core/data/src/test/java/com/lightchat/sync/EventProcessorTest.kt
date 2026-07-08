package com.lightchat.sync

import androidx.test.core.app.ApplicationProvider
import com.lightchat.data.local.DatabaseHelper
import com.lightchat.data.local.UserSession
import com.lightchat.data.local.dao.ConversationDao
import com.lightchat.data.local.dao.FriendRequestDao
import com.lightchat.data.local.dao.GroupDao
import com.lightchat.data.local.dao.MessageDao
import com.lightchat.data.local.dao.SyncStateDao
import com.lightchat.data.local.dao.UserDao
import com.lightchat.domain.notification.MessageNotifier
import com.lightchat.domain.session.AppPresence
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventProcessorTest {
    private lateinit var database: DatabaseHelper
    private lateinit var messages: MessageDao
    private lateinit var syncState: SyncStateDao
    private lateinit var processor: EventProcessor

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase("lightchat.db")
        val session = UserSession(context).apply { currentUserId = "me" }
        database = DatabaseHelper(context)
        messages = MessageDao(database)
        syncState = SyncStateDao(database)
        processor = EventProcessor(
            messages,
            ConversationDao(database),
            GroupDao(database),
            UserDao(database),
            FriendRequestDao(database),
            syncState,
            database,
            session,
            object : AppPresence {
                override val isForeground = true
                override val currentConversationId = "single_me_alice"
            },
            object : MessageNotifier {
                override fun showMessage(
                    conversationId: String,
                    title: String,
                    content: String,
                    targetMessageId: String
                ) = Unit
            }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun duplicateMessageEventIsIdempotentAndAdvancesCursor() {
        val event = SyncEvent(
            eventType = EventType.NEW_MESSAGE,
            userSeq = 7,
            messageId = "message-1",
            conversationId = "single_me_alice",
            senderId = "alice",
            receiverId = "me",
            content = "hello",
            conversationSeq = 3
        )

        processor.process(listOf(event))
        processor.process(listOf(event.copy(userSeq = 8)))

        assertEquals(1, messages.getMessageCount("single_me_alice"))
        assertEquals(8, syncState.getLastUserSeq())
    }
}

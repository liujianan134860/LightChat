package com.lightchat.server.store

import com.lightchat.server.model.ServerMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreTest {
    @Test
    fun registerUser_createsUserAndWelcomeConversation() {
        val store = DataStore()

        val result = store.registerUser("u1", "pwd", "Alice")

        assertTrue(result.isSuccess)
        assertEquals("Alice", store.getUser("u1")?.nickname)
        assertTrue(store.loginUser("u1", "pwd").isSuccess)
        assertFalse(store.loginUser("u1", "bad").isSuccess)
        assertNotNull(store.getConversation("single_lightchat_assistant_u1"))
    }

    @Test
    fun createGroupAndAddMembers_updatesGroupMembersAndParticipants() {
        val store = DataStore()
        store.registerUser("owner", "pwd", "Owner")
        store.registerUser("u1", "pwd", "User1")
        store.registerUser("u2", "pwd", "User2")

        store.createGroup("g1", "Group", "owner", listOf("u1"))
        val added = store.addGroupMembers("g1", listOf("u1", "u2", "missing"))

        assertEquals(listOf("u2"), added)
        assertEquals(setOf("owner", "u1", "u2"), store.getGroupMembers("g1"))
        assertEquals(setOf("owner", "u1", "u2"), store.getParticipants("group_g1"))
    }

    @Test
    fun saveMessage_persistsMessageById() {
        val store = DataStore()
        val message = ServerMessage(
            messageId = "m1",
            conversationId = "single_a_b",
            senderId = "a",
            receiverId = "b",
            messageType = 0,
            content = "hello"
        )

        store.saveMessage(message)

        assertEquals(message, store.getMessage("m1"))
    }
}


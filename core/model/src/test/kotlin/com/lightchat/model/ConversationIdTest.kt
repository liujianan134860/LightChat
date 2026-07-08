package com.lightchat.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationIdTest {
    @Test
    fun single_returnsStableIdRegardlessOfUserOrder() {
        assertEquals(
            "single_alice_bob",
            ConversationId.single("alice", "bob")
        )
        assertEquals(
            "single_alice_bob",
            ConversationId.single("bob", "alice")
        )
    }

    @Test
    fun group_prefixesGroupId() {
        assertEquals("group_g1001", ConversationId.group("g1001"))
    }
}

package com.lightchat.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class CmdTest {
    @Test
    fun name_returnsKnownNamesAndUnknownFallback() {
        assertEquals("AUTH", Cmd.name(Cmd.AUTH))
        assertEquals("SEND_MESSAGE", Cmd.name(Cmd.SEND_MESSAGE))
        assertEquals("UPDATE_CONVERSATION_SETTINGS", Cmd.name(Cmd.UPDATE_CONVERSATION_SETTINGS))
        assertEquals("UNKNOWN(-1)", Cmd.name(-1))
    }
}


package com.lightchat.server.store

import com.lightchat.server.model.InboxEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventServiceTest {

    @Test
    fun `events since returns only newer events sorted by user seq`() {
        val dataStore = DataStore()
        val eventService = EventService(dataStore)

        eventService.appendEvent("u1", event(3))
        eventService.appendEvent("u1", event(1))
        eventService.appendEvent("u1", event(2))

        val events = eventService.getEventsSince("u1", 1)

        assertEquals(listOf(2L, 3L), events.map { it.userSeq })
    }

    @Test
    fun `load repairs user seq counter from inbox max seq`() {
        val dataStore = DataStore()
        val eventService = EventService(dataStore)
        val snapshot = JSONObject(
            """
            {
              "userSeqCounters": {"u1": 1},
              "convSeqCounters": {},
              "inboxes": {
                "u1": [
                  {"userSeq": 5, "eventType": 1, "payload": {"messageId": "m5"}, "createdAt": 100}
                ]
              }
            }
            """.trimIndent()
        )

        eventService.loadFromJson(snapshot)

        assertEquals(6L, eventService.nextUserSeq("u1"))
    }

    @Test
    fun `sync result includes user seq in each payload`() {
        val eventService = EventService(DataStore())

        val json = JSONObject(eventService.buildSyncResultJson(listOf(event(8)), hasMore = true, nextUserSeq = 8))

        assertTrue(json.getBoolean("hasMore"))
        assertEquals(8L, json.getLong("nextUserSeq"))
        assertEquals(8L, json.getJSONArray("events").getJSONObject(0).getLong("userSeq"))
    }

    private fun event(userSeq: Long) = InboxEvent(
        userSeq = userSeq,
        eventType = EventTypes.NEW_MESSAGE,
        payload = JSONObject().put("messageId", "m$userSeq"),
        createdAt = 100 + userSeq
    )
}

package com.lightchat.im

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectBackoffTest {
    @Test
    fun delayGrowsExponentiallyAndIsCapped() {
        assertEquals(1_000, ReconnectBackoff.capForAttempt(0))
        assertEquals(2_000, ReconnectBackoff.capForAttempt(1))
        assertEquals(16_000, ReconnectBackoff.capForAttempt(4))
        assertEquals(30_000, ReconnectBackoff.capForAttempt(5))
        assertEquals(30_000, ReconnectBackoff.capForAttempt(20))
    }
}

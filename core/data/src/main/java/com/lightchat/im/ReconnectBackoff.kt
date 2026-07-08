package com.lightchat.im

import kotlin.math.min

object ReconnectBackoff {
    fun capForAttempt(
        attempt: Int,
        baseDelayMs: Long = 1_000,
        maxDelayMs: Long = 30_000
    ): Long {
        require(attempt >= 0)
        require(baseDelayMs > 0)
        require(maxDelayMs >= baseDelayMs)
        val multiplier = 1L shl min(attempt, 30)
        return (baseDelayMs * multiplier)
            .coerceAtLeast(baseDelayMs)
            .coerceAtMost(maxDelayMs)
    }
}

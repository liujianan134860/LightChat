package com.lightchat.im

import kotlinx.coroutines.*

class ReconnectManager(
    private val onReconnect: suspend () -> Boolean,
    private val onStateChange: (ConnectionState) -> Unit
) {
    private var retryCount = 0
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val maxDelay = 30_000L

    fun start() {
        if (job?.isActive == true) return
        retryCount = 0
        job = scope.launch {
            while (isActive) {
                val delayMs = calculateDelay(retryCount)
                delay(delayMs)

                retryCount++
                onStateChange(ConnectionState.RECONNECTING)

                val success = onReconnect()
                if (success) {
                    retryCount = 0
                    break
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        retryCount = 0
    }

    fun reset() {
        retryCount = 0
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    private fun calculateDelay(retry: Int): Long {
        // 1s, 2s, 4s, 8s, 16s, 30s, 30s, ...
        return ReconnectBackoff.capForAttempt(retry, maxDelayMs = maxDelay)
    }
}

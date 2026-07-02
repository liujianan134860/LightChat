package com.lightchat.im

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

class HeartbeatManager(
    private val intervalMs: Long = 20_000,
    private val maxMissCount: Int = 3,
    private val onSendHeartbeat: () -> Unit,
    private val onHeartbeatTimeout: () -> Unit
) {
    private var job: Job? = null
    private var ackCount = AtomicInteger(0)
    private var seqCounter = AtomicInteger(0)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        stop()
        ackCount.set(0)
        job = scope.launch {
            while (isActive) {
                delay(intervalMs)
                seqCounter.incrementAndGet()
                onSendHeartbeat()

                // Wait a short time for ACK to arrive
                delay(3000)

                if (ackCount.incrementAndGet() >= maxMissCount) {
                    onHeartbeatTimeout()
                    break
                }
            }
        }
    }

    fun onAckReceived() {
        ackCount.set(0)
    }

    fun stop() {
        job?.cancel()
        job = null
        ackCount.set(0)
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    fun getCurrentSeq(): Long = seqCounter.get().toLong()
}

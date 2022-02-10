@file:OptIn(DelicateCoroutinesApi::class)

import kotlinx.coroutines.*

class QueueReporter(bc: BedrockClient, bw: BenchmarkWriter, delay: Long) {
    private val job: Job = GlobalScope.launch {
        val start = System.currentTimeMillis()
        var i = 1
        while (isActive) {
            bw.collectQueueState(bc.getQueuedAmount())
            val nextQueryTime = start + i * delay
            val nextQueryDelay = nextQueryTime - System.currentTimeMillis()
            i++
            delay(nextQueryDelay)
        }
    }

    fun close() {
        job.cancel()
    }
}
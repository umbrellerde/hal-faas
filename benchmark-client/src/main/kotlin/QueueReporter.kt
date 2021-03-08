import kotlinx.coroutines.*

class QueueReporter(bc: BedrockClient, bw: BenchmarkWriter, delay: Long) {
    private val job: Job = GlobalScope.launch {
        val start = System.currentTimeMillis()
        var i = 1
        while (isActive) {
            bw.collectQueueState(bc.getQueuedAmount())
            val nextQueryTime = start + i*delay
            val delay = nextQueryTime - System.currentTimeMillis()
            i++
            delay(delay)
        }
    }

    fun close(){
        job.cancel()
    }
}
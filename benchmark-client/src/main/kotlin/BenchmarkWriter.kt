import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class BenchmarkWriter(private val runName: String, private val folder: String = "results") {
    private val logger = KotlinLogging.logger {}

    data class BenchmarkedInvocation(
        val inv: Invocation,
        val start: Long,
        var end: Long = -1,
        var result: InvocationResult? = null
    )

    private val allInvocations = mutableListOf<BenchmarkedInvocation>()

    fun collectStart(inv: Invocation, start: Long = System.currentTimeMillis()) {
        val bi = BenchmarkedInvocation(inv, start)
        // Just a couple of coroutines waiting to add their item to the list.
        GlobalScope.launch {
            synchronized(allInvocations) {
                allInvocations.add(bi)
            }
        }
    }

    fun collectEnd(callback: String, result: InvocationResult, end: Long = System.currentTimeMillis()) {
        val inv = allInvocations.find { it.inv.params.callbackUrl.endsWith(callback) }
        if (inv == null) {
            logger.error { "Could not get Invocation for callback $callback (result=$result)" }
            val filtered = allInvocations.filter { it.inv.params.callbackUrl.endsWith(callback) }
            if (filtered.isNotEmpty()) {
                logger.error { "BUUT the list filtered is not empty???" }
            }
            return
        }
        inv.result = result
        inv.end = end
    }

    private val queuedCount = mutableListOf<Pair<Long, Int>>()
    fun collectQueueState(amount: Int) {
        GlobalScope.launch {
            synchronized(queuedCount) {
                queuedCount.add(Pair(System.currentTimeMillis(), amount))
            }
        }

    }

    /**
     * writes results to $folder/$date_$name.csv
     * returns file name
     */
    fun writeToFile() : String {
        val tz = TimeZone.getTimeZone("Europe/Berlin")
        val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        df.timeZone = tz
        val isoTime = df.format(Date())

        // Write Invocation Results
        val pathName = "$folder/${isoTime}_inv_${runName}.csv"
        val targetFile = File(pathName)
        targetFile.parentFile.mkdirs()
        val writer = targetFile.bufferedWriter()
        writer.write("runtime;staticParam;volatileParam;callbackUrl;start;end;start_computation;end_computation;" +
                "result\n")
        allInvocations.forEach {
            if (it.result == null) {
                logger.error { "Computation $it has no Result!" }
                it.result = InvocationResult("", "", -1, -1)
            }
            writer.write(
                "\"${it.inv.runtime}\";\"${it.inv.configuration}\";\"${it.inv.params.payload}\";" +
                        "\"${it.inv.params.callbackUrl}\";\"${it.start}\";\"${it.end}\";\"${it.result!!.start_computation}\";" +
                        "\"${it.result!!.end_computation}\";\"${it.result!!.result}\"\n"
            )
        }
        writer.close()

        val pathQueueName = "$folder/${isoTime}_queue_${runName}.csv"
        val targetQueueFile = File(pathQueueName)
        targetQueueFile.parentFile.mkdirs()
        val writerQueue = targetQueueFile.bufferedWriter()
        writerQueue.write("time;queuedNum\n")
        queuedCount.forEach {
            writerQueue.write("\"${it.first}\";\"${it.second}\"\n")
        }
        writerQueue.close()

        logger.info { "Success writing log file" }
        return pathName
    }

}
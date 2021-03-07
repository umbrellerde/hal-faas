import mu.KotlinLogging
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat

import java.text.DateFormat
import java.util.*


class BenchmarkWriter(runName: String) {
    private val logger = KotlinLogging.logger {}

    data class BenchmarkedInvocation(
        val inv: Invocation,
        val start: Long = System.currentTimeMillis(),
        var end: Long = -1,
        var result: String = ""
    )

    private val allInvocations = mutableListOf<BenchmarkedInvocation>()

    fun collectStart(inv: Invocation) {
        allInvocations.add(BenchmarkedInvocation(inv))
    }

    fun collectEnd(callback: String, result: String) {
        val inv = allInvocations.find { it.inv.params.callbackUrl.endsWith(callback) }
        if (inv == null) {
            logger.error { "Could not get Invocation for callback $callback (result=$result)" }
            return
        }
        inv.result = result
        inv.end = System.currentTimeMillis()
    }

    fun writeToFile() {
        val tz = TimeZone.getTimeZone("Europe/Berlin")
        val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        df.timeZone = tz
        val isoTime = df.format(Date())

        val targetFile = File("results/${isoTime}_$runName.csv")
        targetFile.parentFile.mkdirs()
        val writer = targetFile.bufferedWriter()
        writer.write("runtime,workload,payload,callbackUrl,start,end,result\n")
    }

}
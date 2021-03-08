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
        var result: String = ""
    )

    private val allInvocations = mutableListOf<BenchmarkedInvocation>()

    fun collectStart(inv: Invocation, start: Long = System.currentTimeMillis()) {
        allInvocations.add(BenchmarkedInvocation(inv, start))
    }

    fun collectEnd(callback: String, result: String, end: Long = System.currentTimeMillis()) {
        val inv = allInvocations.find { it.inv.params.callbackUrl.endsWith(callback) }
        if (inv == null) {
            logger.error { "Could not get Invocation for callback $callback (result=$result)" }
            return
        }
        inv.result = result
        inv.end = end
    }

    fun writeToFile() : String {
        val tz = TimeZone.getTimeZone("Europe/Berlin")
        val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        df.timeZone = tz
        val isoTime = df.format(Date())
        val pathName = "$folder/${isoTime}_${runName}.csv"
        val targetFile = File(pathName)
        targetFile.parentFile.mkdirs()
        val writer = targetFile.bufferedWriter()
        writer.write("runtime,staticParam,volatileParam,callbackUrl,start,end,result\n")
        allInvocations.forEach {
            writer.write(
                "\"${it.inv.runtime}\",\"${it.inv.workload}\",\"${it.inv.params.payload}\"," +
                        "\"${it.inv.params.callbackUrl}\",\"${it.start}\",\"${it.end}\",\"${it.result}\"\n"
            )
        }
        writer.close()
        return pathName
    }

}
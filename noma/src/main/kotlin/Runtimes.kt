import com.beust.klaxon.Klaxon
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.time.ExperimentalTime

/**
 * Manages all running Runtimes, their cached InputConfigurations,
 * kills them when they are too old,
 */
@ExperimentalTime
class Runtimes(private val noMa: NodeManager) {
    private val logger = KotlinLogging.logger {}

    data class RunningRuntime(
        val pid: String,
        val runtimeName: String,
        val configuration: List<String>,
        val accelerator: String,
        val acceleratorAmount: Int,
        var lastUsed: Long = System.currentTimeMillis(),
        var busy: Boolean = false,
    )

    private val running = mutableListOf<RunningRuntime>()

    fun startRuntime(accelerator: String, implInv: ImplementationAndInvocation) {
        val pid = Processes.startProcess(implInv.runtime, accelerator, implInv.amount.toString())
        running.add(
            RunningRuntime(
                pid, implInv.runtime.name, mutableListOf(implInv.inv.configuration), accelerator,
                implInv.amount
            )
        )
        // TODO do initial invocation
    }

    private fun invoke(inv: Invocation, runtime: RunningRuntime) {
        runtime.busy = true
        val startComputation = System.currentTimeMillis()
        runtime.lastUsed = startComputation

        // maybe download the inputconfiguration, but update "configuration" parameter to the file path either way
        val split = inv.configuration.split("|")
        val invConfFile = S3Helper.getInputConfiguration(split[0], split[1])
        inv.configuration = invConfFile.absolutePath

        // maybe download the inputdata, then update the path
        if (inv.params.payload_type == PayloadTypes.REFERENCE) {
            // we need to download it! --> its in the format of a S3File
            val s3object = Klaxon().parse<S3File>(inv.params.payload)!!
            val file = S3Helper.getInputData(s3object)
            inv.params.payload = file.absolutePath
        }

        // actual invocation
        val response = Processes.invoke(runtime.pid, inv)

        // parse response, maybe upload result
        val responseParsed = Klaxon().parse<InvocationResult>(response)!!
        responseParsed.start_computation = startComputation
        responseParsed.end_computation = System.currentTimeMillis()
        logger.info { "Process called with $inv, returned $responseParsed" }

        if (responseParsed.result_type == "reference") {
            logger.info { "Uploading file ${responseParsed.result} to s3://" }
            val files = S3Helper.uploadFiles(responseParsed.result, inv.params.resultBucket)
            responseParsed.result = files
        }

        ResultsHandler.returnResult(inv, responseParsed)
        runtime.busy = false
    }

    private fun findInvocationsToRun() {

    }

    /**
     * Stop all runtimes that haven't been used for at least $olderThanMs
     * milliseconds
     */
    private fun stopOldRuntimes(olderThanMs: Long = 10_000) {
        val now = System.currentTimeMillis()
        val toStop = synchronized(running) {
            val tooOld = running.filter { it.lastUsed <= now - olderThanMs && !it.busy }
            tooOld.forEach { running.remove(it) }
            tooOld
        }
        toStop.forEach {
            logger.debug { "Stopping Runtime $it because it hasn't been used in $olderThanMs ms (now=${System.currentTimeMillis()})" }
            Processes.stopProcess(it.pid)
            GlobalScope.launch { noMa.registerFreedResources(it.accelerator, it.acceleratorAmount) }
        }
    }
}
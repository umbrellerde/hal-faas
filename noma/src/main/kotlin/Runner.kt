import com.beust.klaxon.Klaxon
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.time.ExperimentalTime

@ExperimentalTime
class Runner(accelerator: String, private val implInv: ImplementationAndInvocation, noMa: NodeManager) {
    private val logger = KotlinLogging.logger {}
    private val pid = Processes.startProcess(implInv.runtime, accelerator, implInv.amount.toString())

    init {
        GlobalScope.launch {
            S3Helper.getRuntime(implInv.runtime.location)
            val consumeHelper = BedrockClient() //ConsumeHelper()
            // do the first invocation
            logger.debug { "$pid: Invoking first invocation ${implInv.inv}" }
            invoke(implInv.inv)
            val allWorkloads = mutableListOf(implInv.inv.configuration)
            while (true) {
                logger.debug { "$pid: Waiting for next invocation..." }
                var successfulRun = false
                for (workload in allWorkloads) {
                    val nextInv = consumeHelper.consumeInvocation(
                        implInv.runtime.name, implInv.inv.configuration,
                        // Wait 5s if this is only running 1 workload, otherwise wait shorter to ask for other
                        //workloads
                        timeout_s = if (allWorkloads.size == 1) 8 else 2
                    )
                    if (nextInv.status == 200) {
                        logger.info { "$pid: Calling $nextInv" }
                        invoke(nextInv.inv)
                        successfulRun = true
                        // restart
                        break // out of for workload in workloads
                    }
                }

                if (!successfulRun) {
                    // try to find a new workload that has this runtime.
                    logger.info { "$pid: Did not find any invocation, trying 20s for any config..." }
                    val nextInv = consumeHelper.consumeInvocation(implInv.runtime.name, "*", 12)
                    if (nextInv.status == 200) {
                        logger.debug { "$pid: Calling $nextInv" }
                        allWorkloads.add(0, nextInv.inv.configuration)
                        invoke(nextInv.inv)
                    } else {
                        // There was no new invocation in timeout_s or other mistake
                        logger.info { "$pid: Shutting down because there is no invocation waiting for this runtime" }
                        Processes.stopProcess(pid)
                        GlobalScope.launch {
                            noMa.registerFreedResources(accelerator, implInv.amount)
                        }
                        break
                    }
                }
            }
        }
    }

    suspend fun invoke(inputInv: Invocation) {
        val startComputation = System.currentTimeMillis()
        val inv = inputInv.copy()

        // maybe download the inputconfiguration, but update "configuration" parameter to the file path either way
        // bucket|file
        val split = inv.configuration.split("|")
        val invConfFile = S3Helper.getInputConfiguration(split[0], split[1])
        inv.configuration = invConfFile.absolutePath

        // maybe download the inputdata, then update the path
        if (inv.params.payload_type == PayloadTypes.REFERENCE) {
            // we need to download it! --> its in the format of a S3File
            val s3object = inv.params.payload_reference
            val file = S3Helper.getInputData(s3object)
            inv.params.payload = file.absolutePath
        }

        // actual invocation
        val response = Processes.invoke(pid, inv)

        // Do all results handling stuff in a new coroutine so that this routine can get a new invocation to handle.
        GlobalScope.launch {
            // parse response, maybe upload result
            lateinit var responseParsed: InvocationResult
            try {
                responseParsed = Klaxon().parse<InvocationResult>(response)!!
            } catch (e: Exception) {
                logger.warn { "Klaxon can't parse: $response" }
                e.printStackTrace()
            }
            responseParsed.start_computation = startComputation
            responseParsed.end_computation = System.currentTimeMillis()
            responseParsed.amount = implInv.amount
            logger.info { "Process called with $inv, returned $responseParsed" }
            if (responseParsed.result_type == "reference") {
                logger.info { "Uploading file ${responseParsed.result} to s3://" }
                val files = S3Helper.uploadFiles(responseParsed.result, inv.params.resultBucket)
                responseParsed.result = ArrayList(files)
            }
            try {
                ResultsHandler.returnResult(inv, responseParsed)
            } catch (e: Exception) {
                logger.warn { "Could not reach results handler" }
            }

        }
    }
}
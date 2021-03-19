import com.beust.klaxon.Klaxon
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.time.ExperimentalTime

@ExperimentalTime
class Runner(accelerator: String, implInv: ImplementationAndInvocation, noMa: NodeManager) {
    private val logger = KotlinLogging.logger {}
    val pid = Processes.startProcess(implInv.runtime, accelerator, implInv.amount.toString())

    init {
        GlobalScope.launch {
            var client = BedrockClient()
            // do the first invocation
            logger.debug { "$pid: Invoking first invocation ${implInv.inv}" }
            invoke(implInv.inv)
            val allWorkloads = mutableListOf(implInv.inv.configuration)
            while (true) {
                logger.debug { "$pid: Waiting for next invocation..." }
                var successfulRun = false
                for (workload in allWorkloads) {
                    val nextInv = client.consumeInvocation(
                        implInv.runtime.name, implInv.inv.configuration,
                        // Wait 20s if this is only running 1 workload, otherwise wait shorter to ask for other
                        //workloads
                        if (allWorkloads.size == 1) 5 else 1
                    )
                    if (nextInv.status == 200) {
                        logger.debug { "$pid: Calling $nextInv" }
                        invoke(nextInv.inv)
                        successfulRun = true
                        // restart
                        break // out of for workload in workloads
                    }
                }

                if (!successfulRun) {
                    // try to find a new workload that has this runtime.
                    val nextInv = client.consumeInvocation(implInv.runtime.name, "*", 30)
                    if (nextInv.status == 200) {
                        logger.debug { "$pid: Calling $nextInv" }
                        allWorkloads.add(nextInv.inv.configuration)
                        invoke(nextInv.inv)
                    } else {
                        // There was no new invocation in timeout_s or other mistake
                        logger.info { "$pid: Shutting down because there is no invocation waiting for this runtime" }
                        Processes.stopProcess(pid)
                        noMa.registerFreedResources(accelerator, implInv.amount)
                        break
                    }
                }
            }
        }
    }

    fun invoke(inv: Invocation) {
        val startComputation = System.currentTimeMillis()

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
        val response = Processes.invoke(pid, inv)

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
    }
}
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
                    // TODO only consume workloads where this runtime has enough memory??? Or rename workloads
                    // according to their storage
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
    data class RuntimeInstanceResponse(
        val request: String,
        val accelerator: String,
        val amount: Int,
        val pid: String,
        val result_type: String,
        var result: String,
        val metadata: String
        )
    fun invoke(inv: Invocation) {
        val startComputation = System.currentTimeMillis()
        val invToUse = if (inv.params.payload_type == PayloadTypes.REFERENCE) {
            val path = S3Helper.getPathFomData(pid, inv.params.payload)
            inv.copy(params = inv.params.copy(payload = path))
        } else {
            inv
        }
        val configurationPath = S3Helper.getPathFromConfigurationInput(invToUse.configuration)
        val response = Processes.invoke(pid, invToUse.copy(configuration = configurationPath))
        val responseParsed = Klaxon().parse<RuntimeInstanceResponse>(response)
        logger.info { "Process called with $invToUse, returned $responseParsed" }
        if (responseParsed!!.result_type == "reference") {
            logger.info { "Uploading file ${responseParsed.result} to s3://" }
            val path = S3Helper.uploadResult(responseParsed.result)
            responseParsed.result = path
        }
        ResultsHandler.returnResult(inv, response, startComputation, end_computation = System.currentTimeMillis())
    }
}
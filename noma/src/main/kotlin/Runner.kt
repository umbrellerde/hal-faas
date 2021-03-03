import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.time.ExperimentalTime

@ExperimentalTime
class Runner(accelerator: String, acceleratorAmount: Int, implInv: ImplementationAndInvocation, noMa: NodeManager) {
    private val logger = KotlinLogging.logger {}
    val pid = Processes.startProcess(implInv.runtime, accelerator, acceleratorAmount.toString())

    init {
        GlobalScope.launch {
            var client = BedrockClient()
            // do the first invocation
            logger.debug { "$pid: Invoking first invocation ${implInv.inv}" }
            invoke(implInv.inv)
            val allWorkloads = mutableListOf(implInv.inv.workload)
            while (true) {
                logger.debug { "$pid: Waiting for next invocation..." }
                var successfulRun = false
                for (workload in allWorkloads) {
                    val nextInv = client.consumeInvocation(
                        implInv.runtime.name, implInv.inv.workload,
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
                        allWorkloads.add(nextInv.inv.workload)
                        invoke(nextInv.inv)
                    } else {
                        // There was no new invocation in timeout_s or other mistake
                        logger.info { "$pid: Shutting down because there is no invocation waiting for this runtime" }
                        Processes.stopProcess(pid)
                        noMa.registerFreedResources(accelerator, acceleratorAmount)
                        break
                    }
                }
            }
        }
    }

    fun invoke(inv: Invocation) {
        logger.info { "Process called with $inv, returned ${Processes.invoke(pid, inv)}" }
    }
}
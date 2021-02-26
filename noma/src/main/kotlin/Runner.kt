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
            invoke(implInv.inv.params)
            while (true) {
                logger.debug { "$pid: Waiting for next invocation..." }
                val nextInv = client.consumeInvocation(implInv.runtime.name, implInv.inv.workload, 60)
                if (nextInv.status == 200) {
                    logger.debug { "$pid: Calling $nextInv" }
                    invoke(nextInv.inv.params)
                } else {
                    // There was no new invocation in timeout_s or other mistake
                    logger.info { "$pid: Shutting down, nextInv was: $nextInv" }
                    Processes.stopProcess(pid)
                    noMa.registerFreedResources(accelerator, acceleratorAmount)
                    break
                }
            }
        }
    }

    fun invoke(invParams: InvocationParams) {
        Processes.invoke(pid, invParams.payload)
    }
}
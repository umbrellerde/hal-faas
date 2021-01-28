import hal_faas.serializables.Invocation
import hal_faas.serializables.InvocationResponse
import mu.KotlinLogging

/**
 * a "dumb" workloadInstance that must be managed from somewhere else
 */
class WorkloadInstance(workloadName: String, val accelerator: String, val accceleratorAmount: String, initialInvocation: Invocation) {
    private val logger = KotlinLogging.logger {}

    val pid = Processes.startProcess(workloadName, accelerator, accceleratorAmount)
    var lastUsed = System.currentTimeMillis()
    var busy = false

    private fun handleInvocation(inv: Invocation): InvocationResponse {
        busy = true
        logger.info { "WorkloadInstance $pid has received ${inv.toString().replace("\n", " ")}" }
        val res = Processes.invoke(pid, inv.parameters)
        lastUsed = System.currentTimeMillis()
        return InvocationResponse(inv.uniqueId, res)
        busy = false
    }
}
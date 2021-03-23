import kotlinx.coroutines.*
import mu.KotlinLogging
import kotlin.time.ExperimentalTime
import kotlin.time.minutes
import kotlin.time.seconds

@ExperimentalTime
class NodeManager {
    private val bc = BedrockClient()
    private val logger = KotlinLogging.logger {}

    // TODO maybe read this from a config file with acceleratorName, Type, Amount...
    private val acceleratorTypes = mapOf(
        "gpu-1" to "gpu",
        "gpu-2" to "gpu"
    )
    private val acceleratorCurrentlyFree = mutableMapOf(
        "gpu-1" to 500,
        "gpu-2" to 500
    )
    private var job: Job

    init {
//        acceleratorTypes.forEach { accelerator -> registerFreedResources(accelerator.value,
//            acceleratorCurrentlyFree[accelerator.key]!!) }
        job = GlobalScope.launch {
            while (isActive) {
                acceleratorCurrentlyFree.forEach { accelerator ->
                    if (accelerator.value > 0) {
                        logger.debug { "Trying to start new Resources for accelerator $accelerator, free=${acceleratorCurrentlyFree[accelerator.key]}" }
                        startNewResources(accelerator.key, accelerator.value)
                    }
                }
                val waitForNewObjects = 15.seconds
                logger.debug { "Waiting for $waitForNewObjects to start new resources on this node" }
                if (isActive) {
                    delay(waitForNewObjects)
                }
            }
        }
    }

    suspend fun registerFreedResources(accelerator: String, amount: Int) {
        val totalFree = synchronized(acceleratorCurrentlyFree) {
            changeAcceleratorCurrentlyFree(accelerator, amount)
            acceleratorCurrentlyFree[accelerator]!!
        }
        startNewResources(accelerator, totalFree)
    }

    private fun changeAcceleratorCurrentlyFree(key: String, value: Int) {
        val currentFree = acceleratorCurrentlyFree[key]!!
        acceleratorCurrentlyFree[key] = currentFree + value
    }

    private suspend fun startNewResources(accelerator: String, amount: Int) {
        logger.debug { "Trying to start new runtime_implementation on $accelerator, amount=$amount" }
        val acceleratorType = acceleratorTypes[accelerator]
        val nextRInv = bc.getNextRuntimeAndInvocationToStart(acceleratorType!!, amount)
        val changedAmount = synchronized(acceleratorCurrentlyFree) {
            if (nextRInv.success) {
                logger.info {
                    "Starting new runtime_implementation for $accelerator (amount=$amount), " +
                            "next_op=${nextRInv}"
                }
                changeAcceleratorCurrentlyFree(accelerator, nextRInv.amount)
                Runner(accelerator, nextRInv, this)
                nextRInv.amount
            } else {
                -1
            }
        }
        // We started something but we didn't use every freed resource
        val restFreeAmount = amount - changedAmount
        if (changedAmount != -1 && restFreeAmount > 0) {
            startNewResources(accelerator, restFreeAmount)
        }
    }

    suspend fun close() {
        job.cancelAndJoin()
    }
}
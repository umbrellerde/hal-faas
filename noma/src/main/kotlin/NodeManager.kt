import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.time.ExperimentalTime
import kotlin.time.minutes
import kotlin.time.seconds

@ExperimentalTime
class NodeManager {
    private val bc = BedrockClient()
    private val logger = KotlinLogging.logger {}
    // TODO read this from a config file with acceleratorName, Type, Amount...
    private val acceleratorTypes = mapOf(
            "gpu-1" to "gpu",
            "gpu-2" to "gpu"
    )
    private val acceleratorCurrentlyFree = mutableMapOf(
        "gpu-1" to 500,
        "gpu-2" to 500
    )
    init {
        acceleratorTypes.forEach { accelerator -> registerFreedResources(accelerator.value,
            acceleratorCurrentlyFree[accelerator.key]!!) }
        GlobalScope.launch {
            acceleratorCurrentlyFree.forEach { accelerator ->
                logger.debug { "Trying to start new Resources for accelerator $accelerator" }
                if (accelerator.value > 0) {
                    startNewResources(accelerator.key, accelerator.value)
                }
            }
            val waitForNewObjects = 30.seconds
            logger.debug { "Waiting for $waitForNewObjects to start new resources on this node" }
            delay(waitForNewObjects)
        }
    }

    fun registerFreedResources(accelerator: String, amount: Int) {
        synchronized(acceleratorCurrentlyFree) {
            changeAcceleratorCurrentlyFree(accelerator, amount)
        }
        startNewResources(accelerator, amount)
    }

    private fun changeAcceleratorCurrentlyFree(key: String, value: Int) {
        val currentFree = acceleratorCurrentlyFree[key]!!
        acceleratorCurrentlyFree[key] = currentFree + value
    }

    private fun startNewResources(accelerator: String, amount: Int) {
        logger.info { "Trying to start new runtime_implementation on $accelerator, amount=$amount" }
        val acceleratorType = acceleratorTypes[accelerator]
        synchronized(acceleratorCurrentlyFree) {
            val nextRInv = bc.getNextRuntimeAndInvocationToStart(acceleratorType!!, amount)
            if (nextRInv.success) {
                logger.info { "Starting new runtime_implementation for $accelerator (amount=$amount), " +
                        "next_op=${nextRInv}" }
                changeAcceleratorCurrentlyFree(accelerator, -amount)
                Runner(accelerator, amount, nextRInv, this)
            }
        }
    }
}
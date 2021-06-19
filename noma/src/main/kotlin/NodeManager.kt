import kotlinx.coroutines.*
import mu.KotlinLogging
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@ExperimentalTime
class NodeManager {
    private val bc = BedrockClient()
    private val logger = KotlinLogging.logger {}

    private val acceleratorTypes = mutableMapOf<String, String>()
    private val acceleratorCurrentlyFree = mutableMapOf<String, Int>()
//        if (System.getProperty("user.name").equals("trever")) {
//            mapOf(
//                "0" to "gpu",
//                "mycpu" to "cpu"
//            )
//        } else {
//            mapOf(
////                "0" to "gpu"
//                "0" to "gpu",
//                "1" to "gpu",
////                "stick1" to "myriad",
//            )
//        }

    init {
        for (acc: String in Settings.resources.split(";")) {
            val items = acc.split(",")
            // name, type, amount
            acceleratorTypes[items[0]] = items[1]
            acceleratorCurrentlyFree[items[0]] = items[2].toInt()
        }
    }

//        if (System.getProperty("user.name").equals("trever")) {
//            mutableMapOf(
//                "0" to 2000,
//                "mycpu" to 100
//            )
//        } else {
//            mutableMapOf(
////                "0" to 2600
//                "0" to 3000,
//                "1" to 3000,
////                "0" to 1500,
////                "1" to 1500,
////                "stick1" to 1,
//            )
//        }
    private var job = GlobalScope.launch {
        var firstResourcesStarted = false
        while (isActive) {
            logger.debug { "Free Resources: $acceleratorCurrentlyFree" }
            acceleratorCurrentlyFree.forEach { accelerator ->
                if (accelerator.value > 0) {
                    logger.debug { "Trying to start new Resources for accelerator $accelerator, free=${acceleratorCurrentlyFree[accelerator.key]}" }
                    val success = startNewResources(accelerator.key, accelerator.value)
                    if (!firstResourcesStarted && success) firstResourcesStarted = true
                }
            }
            val waitForNewObjects = if (firstResourcesStarted) 10.seconds else 2.seconds
            logger.debug { "Waiting for $waitForNewObjects to start new resources on this node ($acceleratorCurrentlyFree)" }
            if (isActive) {
                delay(waitForNewObjects)
            }
        }
    }

    suspend fun registerFreedResources(accelerator: String, amount: Int) {
        val totalFree = synchronized(acceleratorCurrentlyFree) {
            changeAcceleratorCurrentlyFree(accelerator, amount)
            acceleratorCurrentlyFree[accelerator]!!
        }
        //startNewResources(accelerator, totalFree)
    }

    private fun changeAcceleratorCurrentlyFree(key: String, value: Int) {
        val currentFree = acceleratorCurrentlyFree[key]!!
        acceleratorCurrentlyFree[key] = currentFree + value
    }

    private suspend fun startNewResources(accelerator: String, amount: Int, alreadyStarted: Boolean = false): Boolean {
        logger.debug { "Trying to start new runtime_implementation on $accelerator, amount=$amount" }
        // Check for free space and reserve space
        val findAmount = synchronized(acceleratorCurrentlyFree) {
            val currFree = acceleratorCurrentlyFree[accelerator]!!
            if (currFree >= amount) {
                changeAcceleratorCurrentlyFree(accelerator, amount * -1)
                return@synchronized amount
            } else {
                return false
            }
        }

        val acceleratorType = acceleratorTypes[accelerator]
        val nextRInv = bc.getNextRuntimeAndInvocationToStart(acceleratorType!!, findAmount)
        val changedAmount = synchronized(acceleratorCurrentlyFree) {
            // Give back the space we reserved before calling getNextRuntime
            // will be removed again if the nextInv was a success, otherwise we will "return" this value to its
            //original point
            changeAcceleratorCurrentlyFree(accelerator, amount)
            if (nextRInv.success) {
                logger.info {
                    "Starting new runtime_implementation for $accelerator " +
                            "(free=${acceleratorCurrentlyFree[accelerator]}, amount=$amount, " +
                            "next_op=${nextRInv})"
                }
                changeAcceleratorCurrentlyFree(accelerator, nextRInv.amount * -1)
                Runner(accelerator, nextRInv, this)
                nextRInv.amount
            } else {
                -1
            }
        }
        // We started something but we didn't use every freed resource
        // if changed==-1 then we will go into else in the if statement below
        val restFreeAmount = amount - changedAmount
        return if (changedAmount != -1 && restFreeAmount > 0) {
            startNewResources(accelerator, restFreeAmount, true)
        } else {
            // Nothing different to start
            alreadyStarted
        }
    }

    suspend fun close() {
        job.cancelAndJoin()
    }
}
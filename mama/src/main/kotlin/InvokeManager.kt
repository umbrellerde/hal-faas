import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import mu.KotlinLogging
import java.util.concurrent.PriorityBlockingQueue

data class Invocation(
    val workloadName: String,
    val params: String,
    val returnChannel: Channel<String>,
    val accType: String,
    val created: Long = System.currentTimeMillis()
) : Comparable<Invocation> {
    override fun compareTo(other: Invocation): Int = when {
        created != other.created -> (created - other.created).toInt()
        else -> workloadName.compareTo(other.workloadName)
    }
}

class InvokeManager(val rm: ResourceManager) {
    private val logger = KotlinLogging.logger {}

    val toDoMutex = Mutex()
    val toDo = PriorityBlockingQueue<Invocation>()

    suspend fun registerInvocation(inv: Invocation) {
        toDo.add(inv)
        GlobalScope.launch {
            logger.debug { "Registered Invocation ${inv.workloadName}, trying to invoke it." }
            tryNextInvoke(inv.workloadName)
        }
    }

    suspend fun tryNextInvoke(workloadName: String) {
        // is there a waiting invocation with the same workloadname? then try it.
        // If not continue to same accelerator Type.
        // Last step: just try to run the oldest job. This should generally not start a job
        // (since this job should have been able to start when it was created)
        logger.info { "Trying to invoke the next function, workloadName=$workloadName" }

        if (toDo.size == 0) {
            return
        }

        // Current Step:
        // 0 = same Workload Name
        // 1 = same Accelerator Type (butt not same workload)
        // 2 = just generally...
        var tryStep = 0

        while (tryStep <= 2) {
            var invocationToDo: Invocation? = null
            toDoMutex.lock()
            when (tryStep) {
                0 -> {
                    invocationToDo = toDo.find { it.workloadName == workloadName }
                }
                1 -> {
                    val accType = rm.workloads.find { it.name == workloadName }!!.acceleratorType
                    invocationToDo = toDo.find { it.workloadName != workloadName && it.accType == accType }
                }
                2 -> {
                    invocationToDo = toDo.firstOrNull()
                }
            }

            // Did not find any invocation to do? Try next step
            if (invocationToDo == null) {
                tryStep++
                toDoMutex.unlock()
                continue
            } else {
                // Invocation found. Remove it and let the next coroutine find the next invocation
                toDo.remove(invocationToDo)
                toDoMutex.unlock()
            }

            // Actual Invocation:
            val res = rm.tryInvoke(invocationToDo.workloadName, invocationToDo.params)
            if (res == null) {
                // Invocation failed -> Try next step next
                tryStep++
                toDo.add(invocationToDo)
            } else {
                // Invocation successful
                logger.debug { "tryNextInvoke: Invocation on step $tryStep successful!" }
                invocationToDo.returnChannel.send(res)
            }
        }
    }
}
import kotlinx.coroutines.channels.Channel
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
        created != other.created -> (created - other.created) as Int
        else -> workloadName.compareTo(other.workloadName)
    }
}

class InvokeManager(val rm: ResourceManager) {
    private val logger = KotlinLogging.logger {}

    val toDo = PriorityBlockingQueue<Invocation>()

    suspend fun registerInvocation(inv: Invocation) {
        toDo.add(inv)
        tryNextInvoke(inv.workloadName)
    }

    suspend fun tryNextInvoke(workloadName: String) {
        // TODO Abend Das in einer While Schleife. Und wenn es GAR NICHTS mit der gleichen Workload gibt dann gleichen Accelerator probieren, auch in While schleife.
        // is there a waiting invocation with the same workloadname?
        val sameWo = toDo.find { it.workloadName == workloadName }

        if (sameWo != null) {
            val res = rm.tryInvoke(sameWo.workloadName, sameWo.params)
            if (res != null) {
                logger.debug { "Found invocation with same workloadName" }
                sameWo.returnChannel.send(res)
                toDo.remove(sameWo)
            }
            return
        }

        // there is not invocation left with the same workloadName
        // but maybe on the same acceleratorType?
        val accType = rm.workloads.find { it.name == workloadName }!!.acceleratorType
        val sameAccType = toDo.find { it.accType == accType && it.workloadName != workloadName }

        if (sameAccType != null) {
            val res = rm.tryInvoke(sameAccType.workloadName, sameAccType.params)
            if (res != null) {
                logger.debug { "Found invocation with different WorkloadName but same acceleratorType" }
                sameAccType.returnChannel.send(res)
                toDo.remove(sameWo)
            }
            return
        }

        // this should never work, but lets try anyway:
        // just try to run the oldest job...
        val oldest = toDo.firstOrNull()
        if (oldest != null) {
            val res = rm.tryInvoke(oldest.workloadName, oldest.params)
            if (res != null) {
                logger.debug { "Found invocation: just the oldest one..." }
                oldest.returnChannel.send(res)
                toDo.remove(oldest)
            }
            return
        }
    }
}
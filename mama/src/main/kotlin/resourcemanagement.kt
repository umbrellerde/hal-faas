import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import mu.KotlinLogging
import javax.annotation.concurrent.GuardedBy

data class Workload(
    val name: String,
    val acceleratorType: String,
    val acceleratorAmount: Int
)

enum class WorkloadInstanceState {
    BUSY, IDLE
}

data class WorkloadInstance(
    /**
     * Name of the workload that this instance can manage
     */
    val workloadName: String,
    /**
     * Name by which the NoMa can identify this instance (e.g. the pid)
     */
    val uniqueName: String,
    val acceleratorAmount: Int,
    var state: WorkloadInstanceState = WorkloadInstanceState.IDLE,
    val lastUsed: Long = System.currentTimeMillis()
)

data class Accelerator(
    val type: String,
    /**
     * the name by which the noma can identify the accelerator (like "0" or "1" for a system with two)
     */
    val name: String,
    val amount: Int,
    val amountType: String,
    val instances: MutableList<WorkloadInstance>
) {
    val free: Int
        get() = amount - instances.sumBy { it.acceleratorAmount }
}

data class Node(
    val address: String,
    val accelerators: MutableList<Accelerator>
)

private val nodesLock = Mutex()

/**
 * Manages Nodes, Accelerators and Workload Instances.
 */
class ResourceManager {
    private val logger = KotlinLogging.logger {}

    @GuardedBy("nodesLock")
    val nodes = ArrayList<Node>()
    val allAccelerators: List<Accelerator>
        get() = nodes.flatMap { it.accelerators }
    val allInstances: List<WorkloadInstance>
        get() = nodes.flatMap { it.accelerators }.flatMap { it.instances }

    val workloads = ArrayList<Workload>()

    init {
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                val jobs = ArrayList<Job>()
                allInstances.forEach {
                    jobs.add(
                        GlobalScope.launch {
                            val node = getNode(it)
                            logger.info { "Stopping workload instance $it on node $node" }
                            NodeClients.getNode(node.address).stop(it.uniqueName)
                        }
                    )
                }
                runBlocking {
                    jobs.forEach{
                        it.join()
                    }
                }
            }
        })
    }

    fun getWorkloadInstances(workloadName: String): List<WorkloadInstance> {
        return allInstances.filter { it.workloadName == workloadName }
    }

    fun getAccelerator(instance: WorkloadInstance): Accelerator {
        return allAccelerators.find { it.instances.contains(instance) }!!
    }

    fun getNode(accelerator: Accelerator): Node {
        return nodes.find { it.accelerators.contains(accelerator) }!!
    }

    fun getNode(instance: WorkloadInstance): Node {
        return nodes.find { node -> node.accelerators.flatMap { it.instances }.contains(instance) }!!
    }

    data class InstanceAndNode(val instance: WorkloadInstance, val node: Node)

    /**
     * returns null or an already BUSY instance with this workloadname
     */
    suspend fun tryPlaceInstance(workloadName: String): InstanceAndNode? {
        val workload = workloads.find { it.name == workloadName }!!
        // step 1: find accelerator that has free space
        val accFree =
            allAccelerators.filter { it.type == workload.acceleratorType && it.free >= workload.acceleratorAmount }
        logger.debug { "tryPlaceInstance: workload=$workload found IDLE instances: $accFree" }
        if (accFree.isNotEmpty()) {
            val accDecision = accFree.first()
            val node = getNode(accDecision)
            logger.info { "Starting new instance of type $workload on $accDecision (on node $node)" }

            // Here the new container is created
            val instanceName = NodeClients.getNode(node.address)
                .create(workload.name, accDecision.name, workload.acceleratorAmount)

            // Save this new container
            // Mark it as busy because it will be used immediately by invoke
            val newInstance = WorkloadInstance(
                workload.name,
                instanceName,
                workload.acceleratorAmount,
                WorkloadInstanceState.BUSY
            )
            accDecision.instances.add(newInstance)
            return InstanceAndNode(newInstance, node)
        }

        // step 2: find accelerator that has idle containers that could be stopped (but not because of current workload type...)
        fun hasEnoughIdleInstances(accelerator: Accelerator, amount: Int): Boolean =
            accelerator.instances.filter { it.state == WorkloadInstanceState.IDLE && it.workloadName != workloadName }
                .sumBy { amount } >= amount

        val accIdle = allAccelerators.find {
            it.type == workload.acceleratorType &&
                    hasEnoughIdleInstances(it, workload.acceleratorAmount)
        }
        // stop idle containers until there is enough space to start the new container
        if (accIdle != null) {
            val node = getNode(accIdle)
            val idleInstances = accIdle.instances.sortedWith(compareBy({ it.acceleratorAmount }, { it.lastUsed }))
            var stopped = 0
            logger.info { "Found ${idleInstances.size} IDLE instances that can be stopped to make space for new ones" }
            for (instance in idleInstances) {
                logger.debug { "Stopping instance $instance (node=$node) to make space for new instancess" }

                NodeClients.getNode(node.address).stop(instance.uniqueName)

                stopped += instance.acceleratorAmount
                ///...remove instance from list of instances...
                getAccelerator(instance).instances.remove(instance)
                if (stopped >= workload.acceleratorAmount) {
                    break
                }
            }
            // so now this accelerator has enough space to fit the new container.
            // repeat step 1 in placecontainer.
            return tryPlaceInstance(workloadName)
        }

        // step 3: there is no accelerator with enough space to fit this container...
        return null
    }

    /**
     * tries to invoke the workload.
     * If it works, return the response
     * If there are no free instances, return null
     */
    suspend fun tryInvoke(workloadName: String, params: String): String? {
        nodesLock.lock()
        val idle = getWorkloadInstances(workloadName).find { it.state == WorkloadInstanceState.IDLE }
        if (idle != null) {
            logger.info { "Invoking $workloadName (params=$params) on running instance $idle" }
            idle.state = WorkloadInstanceState.BUSY
            nodesLock.unlock()
            val node = getNode(idle)
            val res = NodeClients.getNode(node.address).invoke(idle.uniqueName, params)
            idle.state = WorkloadInstanceState.IDLE
            return res
        }

        // If there is no idle container then try to place one
        // tryPlaceContainer returns a container that is already BUSY, so no need to keep nodesLock afterwardsS
        val newPlacement = tryPlaceInstance(workloadName)
        nodesLock.unlock()

        if (newPlacement != null) {
            val (instance, node) = newPlacement
            logger.info { "Invoking $workloadName (params=$params) one newly created instance $instance" }
            val res = NodeClients.getNode(node.address).invoke(instance.uniqueName, params)
            instance.state = WorkloadInstanceState.IDLE
            return res
        }
        return null
    }
}
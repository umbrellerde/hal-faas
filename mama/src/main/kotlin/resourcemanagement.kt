import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import mu.KotlinLogging
import java.util.concurrent.locks.ReentrantLock
import javax.annotation.concurrent.GuardedBy

data class Workload(
    val name: String,
    val acceleratorType: String,
    val acceleratorAmount: Int,
    val dockerImage: String,
    val dockerOptions: String,
    val dockerParams: String
)

enum class AcceleratedContainerState {
    BUSY, IDLE
}

data class AcceleratedContainer(
    /**
     * Name of the workload that this container can manage
     */
    val workloadName: String,
    /**
     * Name by which the NoMa can identify this container
     */
    val uniqueName: String,
    val acceleratorAmount: Int,
    var state: AcceleratedContainerState = AcceleratedContainerState.IDLE,
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
    val containers: MutableList<AcceleratedContainer>
) {
    val free: Int
        get() = amount - containers.sumBy { it.acceleratorAmount }
}

data class Node(
    val address: String,
    val accelerators: MutableList<Accelerator>
)

private val nodesLock = Mutex()

/**
 * Manages Nodes, Accelerators and Accelerated Containers.
 */
class ResourceManager {
    private val logger = KotlinLogging.logger {}

    @GuardedBy("nodesLock")
    val nodes = ArrayList<Node>()
    val allAccelerators: List<Accelerator>
        get() = nodes.flatMap { it.accelerators }
    val allContainers: List<AcceleratedContainer>
        get() = nodes.flatMap { it.accelerators }.flatMap { it.containers }

    val workloads = ArrayList<Workload>()

    init {
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                val jobs = ArrayList<Job>()
                allContainers.forEach {
                    jobs.add(
                        GlobalScope.launch {
                            val node = getNode(it)
                            logger.info { "Stopping container $it on node $node" }
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

    fun getContainers(workloadName: String): List<AcceleratedContainer> {
        return allContainers.filter { it.workloadName == workloadName }
    }

    fun getAccelerator(container: AcceleratedContainer): Accelerator {
        return allAccelerators.find { it.containers.contains(container) }!!
    }

    fun getNode(accelerator: Accelerator): Node {
        return nodes.find { it.accelerators.contains(accelerator) }!!
    }

    fun getNode(container: AcceleratedContainer): Node {
        return nodes.find { node -> node.accelerators.flatMap { it.containers }.contains(container) }!!
    }

    data class ContainerAndNode(val container: AcceleratedContainer, val node: Node)

    /**
     * returns null or an already BUSY container with this workloadname
     */
    suspend fun tryPlaceContainer(workloadName: String): ContainerAndNode? {
        val workload = workloads.find { it.name == workloadName }!!
        // step 1: find accelerator that has free space
        val accFree =
            allAccelerators.filter { it.type == workload.acceleratorType && it.free >= workload.acceleratorAmount }
        logger.debug { "tryPlaceContainer: workload=$workload found IDLE containers: $accFree" }
        if (accFree.isNotEmpty()) {
            val accDecision = accFree.first()
            val node = getNode(accDecision)
            logger.info { "Starting new container of type $workload on $accDecision (on node $node)" }

            // Here the new container is created
            val containerName = NodeClients.getNode(node.address)
                .create(workload.dockerImage, workload.dockerOptions, workload.dockerParams)

            // Save this new container
            // Mark it as busy because it will be used immediately by invoke
            val newContainer = AcceleratedContainer(
                workload.name,
                containerName,
                workload.acceleratorAmount,
                AcceleratedContainerState.BUSY
            )
            accDecision.containers.add(newContainer)
            return ContainerAndNode(newContainer, node)
        }

        // step 2: find accelerator that has idle containers that could be stopped (but not because of current workload type...)
        fun hasEnoughIdleContainers(accelerator: Accelerator, amount: Int): Boolean =
            accelerator.containers.filter { it.state == AcceleratedContainerState.IDLE && it.workloadName != workloadName }
                .sumBy { amount } >= amount

        val accIdle = allAccelerators.find {
            it.type == workload.acceleratorType &&
                    hasEnoughIdleContainers(it, workload.acceleratorAmount)
        }
        // stop idle containers until there is enough space to start the new container
        if (accIdle != null) {
            val node = getNode(accIdle)
            val idleContainers = accIdle.containers.sortedWith(compareBy({ it.acceleratorAmount }, { it.lastUsed }))
            var stopped = 0
            logger.info { "Found ${idleContainers.size} IDLE containers that can be stopped to make space for new ones" }
            for (container in idleContainers) {
                logger.debug { "Stopping container $container (node=$node) to make space for new containers" }

                NodeClients.getNode(node.address).stop(container.uniqueName)

                stopped += container.acceleratorAmount
                ///...remove container from list of containers...
                getAccelerator(container).containers.remove(container)
                if (stopped >= workload.acceleratorAmount) {
                    break
                }
            }
            // so now this accelerator has enough space to fit the new container.
            // repeat step 1 in placecontainer.
            return tryPlaceContainer(workloadName)
        }

        // step 3: there is no accelerator with enough space to fit this container...
        return null
    }

    /**
     * tries to invoke the workload.
     * If it works, return the response
     * If there are no free containers, return null
     */
    suspend fun tryInvoke(workloadName: String, params: String): String? {
        nodesLock.lock()
        val idle = getContainers(workloadName).find { it.state == AcceleratedContainerState.IDLE }
        if (idle != null) {
            logger.info { "Invoking $workloadName (params=$params) on running container $idle" }
            idle.state = AcceleratedContainerState.BUSY
            nodesLock.unlock()
            val node = getNode(idle)
            val res = NodeClients.getNode(node.address).invoke(idle.uniqueName, params)
            idle.state = AcceleratedContainerState.IDLE
            return res
        }

        // If there is no idle container then try to place one
        // tryPlaceContainer returns a container that is already BUSY, so no need to keep nodesLock afterwardsS
        val newPlacement = tryPlaceContainer(workloadName)
        nodesLock.unlock()

        if (newPlacement != null) {
            val (container, node) = newPlacement
            logger.info { "Invoking $workloadName (params=$params) one newly created container $container" }
            val res = NodeClients.getNode(node.address).invoke(container.uniqueName, params)
            container.state = AcceleratedContainerState.IDLE
            return res
        }
        return null
    }
}
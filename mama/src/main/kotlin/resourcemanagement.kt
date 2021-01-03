import mu.KotlinLogging

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

/**
 * Manages Nodes, Accelerators and Accelerated Containers.
 */
class ResourceManager {
    private val logger = KotlinLogging.logger {}

    val nodes = ArrayList<Node>()
    val allAccelerators: List<Accelerator>
        get() = nodes.flatMap { it.accelerators }
    val allContainers: List<AcceleratedContainer>
        get() = nodes.flatMap { it.accelerators }.flatMap { it.containers }

    val workloads = ArrayList<Workload>()

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
    suspend fun tryPlaceContainer(workloadName: String): ContainerAndNode? {
        val workload = workloads.find { it.name == workloadName }!!
        // step 1: find accelerator that has free space
        val accFree = allAccelerators.filter { it.type == workload.acceleratorType && it.free >= workload.acceleratorAmount }
        var newContainer: AcceleratedContainer? = null
        if (accFree.isNotEmpty()) {
            val accDecision = accFree.first()
            val node = getNode(accDecision)
            logger.info { "Starting new container of type $workload on $accDecision" }

            // Here the new container is created
            val containerName = NodeClients.getNode(node.address).create(workload.dockerImage, workload.dockerOptions, workload.dockerParams)

            // Save this new container
            // Mark it as busy because it will be used immediately by invoke
            newContainer = AcceleratedContainer(workload.name, containerName, workload.acceleratorAmount, AcceleratedContainerState.BUSY)
            accDecision.containers.add(newContainer)
            return ContainerAndNode(newContainer, node)
        }

        // step 2: find accelerator that has idle containers that could be stopped
        fun hasEnoughIdleContainers(accelerator: Accelerator, amount: Int): Boolean =
            accelerator.containers.filter { it.state == AcceleratedContainerState.IDLE }.sumBy { amount } >= amount

        val accIdle = allAccelerators.find { it.type == workload.acceleratorType && hasEnoughIdleContainers(it, workload.acceleratorAmount) }
        // stop idle containers until there is enough space to start the new container
        if (accIdle != null) {
            val node = getNode(accIdle)
            val idleContainers = accIdle.containers.sortedBy { it.acceleratorAmount }
            var stopped = 0
            logger.info { "Found ${idleContainers.size} IDLE containers that can be stopped to make space for new ones" }
            for (container in idleContainers.sortedBy { it.lastUsed }) {
                logger.debug { "Stopping container $container to make space for new containers" }

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
        val idle = getContainers(workloadName).find { it.state == AcceleratedContainerState.IDLE }
        if (idle != null) {
            logger.info { "Invoking $workloadName on running container $idle" }
            idle.state = AcceleratedContainerState.BUSY
            val node = getNode(idle)
            val res = NodeClients.getNode(node.address).invoke(idle.uniqueName, params)
            idle.state = AcceleratedContainerState.IDLE
            return res
        }

        // If there is no idle container then try to place one
        val newPlacement = tryPlaceContainer(workloadName)
        if (newPlacement != null) {
            val (container, node) = newPlacement
            logger.info { "Invoking $workloadName one newly created container $container" }
            val res = NodeClients.getNode(node.address).invoke(container.uniqueName, params)
            container.state = AcceleratedContainerState.IDLE
            return res
        }
        return null
    }
}
import mu.KotlinLogging

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

class ResourceManager {
    private val logger = KotlinLogging.logger {}

    val nodes = ArrayList<Node>()
    val allAccelerators: List<Accelerator>
        get() = nodes.flatMap { it.accelerators }
    val allContainers: List<AcceleratedContainer>
        get() = nodes.flatMap { it.accelerators }.flatMap { it.containers }

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

    fun placeContainer(containerName: String, workload: Workload): AcceleratedContainer? {
        // step 1: find accelerator that has free space
        val accFree = allAccelerators.filter { it.type == workload.acceleratorType && it.free >= workload.acceleratorAmount }
        var newContainer: AcceleratedContainer? = null
        if (accFree.isNotEmpty()) {
            val accDecision = accFree.first()
            val node = getNode(accDecision)
            logger.info { "Starting new container of type $workload on $accDecision" }
            // TODO tell node to place a new container here
            newContainer = AcceleratedContainer(workload.name, containerName, workload.acceleratorAmount)
            accDecision.containers.add(newContainer)
            return newContainer
        }

        // step 2: find accelerator that has idle containers that could be stopped
        fun hasEnoughIdleContainers(accelerator: Accelerator, amount: Int): Boolean =
            accelerator.containers.filter { it.state == AcceleratedContainerState.IDLE }.sumBy { amount } >= amount

        val accIdle = allAccelerators.find { it.type == workload.acceleratorType && hasEnoughIdleContainers(it, workload.acceleratorAmount) }

        // stop idle containers until there is enough space to start the new container
        if (accIdle != null) {
            val idleContainers = accIdle.containers.sortedBy { it.acceleratorAmount }
            var stopped = 0
            logger.info { "Found ${idleContainers.size} IDLE containers that can be stopped to make space for new ones" }
            for (container in idleContainers.sortedBy { it.lastUsed }) {
                logger.debug { "Stopping container $container to make space for new containers" }
                // TODO tell node to stop container
                stopped += container.acceleratorAmount
                ///...remove container from list of containers...
                getAccelerator(container).containers.remove(container)
                if (stopped >= workload.acceleratorAmount) {
                    break
                }
            }
            // so now this accelerator has enough space to fit the new container.
            // repeat step 1 in placecontainer.
            return placeContainer(containerName, workload)
        }

        // step 3: there is no accelerator with enough space to fit this container...
        return null
    }
}
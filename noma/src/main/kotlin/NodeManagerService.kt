import halfaas.proto.NodeManagerGrpcKt
import halfaas.proto.Nodes
import mu.KotlinLogging
import java.lang.IllegalArgumentException

class NodeManagerService : NodeManagerGrpcKt.NodeManagerCoroutineImplBase() {
    private val logger = KotlinLogging.logger {}

    private enum class ContainerState {
        CREATED, STARTED, STOPPED, PAUSED, UNPAUSED
    }

    private data class Container(val name: String, var state: ContainerState = ContainerState.CREATED)

    private val containers = ArrayList<Container>()

    override suspend fun create(request: Nodes.CreateRequest): Nodes.CreateResponse {
        logger.info { "Create was called with $request" }
        val id = DockerHelper.runCreate(request.image, request.options, request.command)
        containers.add(Container(id))
        return Nodes.CreateResponse.newBuilder().setName(id).build()
    }

    override suspend fun invoke(request: Nodes.InvokeRequest): Nodes.InvokeResponse {
        logger.info { "Invoke was called with $request" }
        val container = containers.find { it.name == request.name }
            ?: throw IllegalArgumentException("Container does not exist")
        // Make sure that the container is started
        when (container.state) {
            ContainerState.CREATED, ContainerState.STOPPED -> DockerHelper.runOpOnContainer(
                container.name,
                ContainerOp.START
            )
            ContainerState.PAUSED -> DockerHelper.runOpOnContainer(container.name, ContainerOp.UNPAUSE)
            ContainerState.STARTED, ContainerState.UNPAUSED -> logger.info { "The container is in state ${container.state}, which it shouldn't be..." }
        }
        val res = DockerHelper.invoke(container.name, request.params)
        DockerHelper.runOpOnContainer(container.name, ContainerOp.PAUSE)
        return Nodes.InvokeResponse.newBuilder().setResult(res).build()
    }

    override suspend fun stop(request: Nodes.StopRequest): Nodes.StopResponse {
        logger.info { "Stop was called with $request" }
        DockerHelper.runOpOnContainer(request.name, ContainerOp.STOP)
        DockerHelper.runOpOnContainer(request.name, ContainerOp.RM)
        containers.removeIf { it.name == request.name }
        return Nodes.StopResponse.getDefaultInstance()
    }
}
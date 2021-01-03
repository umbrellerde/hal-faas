import halfaas.proto.client.HalFaasGrpcKt
import halfaas.proto.client.HalFaasOuterClass
import mu.KotlinLogging

data class Workload(
    val name: String,
    val acceleratorType: String,
    val acceleratorAmount: Int,
    val dockerImage: String,
    val dockerOptions: String,
    val dockerParams: String
)

class HalFaasServer(val rm: ResourceManager) : HalFaasGrpcKt.HalFaasCoroutineImplBase() {
    private val logger = KotlinLogging.logger {}

    private val workloads = ArrayList<Workload>()

    override suspend fun registerWorkload(request: HalFaasOuterClass.RegisterWorkloadRequest): HalFaasOuterClass.RegisterWorkloadResponse {
        workloads.add(Workload(
            request.workloadName,
            request.acceleratorType,
            request.acceleratorAmount,
            request.dockerImage,
            request.dockerOptions,
            request.dockerParams
        ))
        return HalFaasOuterClass.RegisterWorkloadResponse.getDefaultInstance()
    }

    override suspend fun invoke(request: HalFaasOuterClass.InvokeRequest): HalFaasOuterClass.InvokeResponse {
        val idle = rm.getContainers(request.workloadName).find { it.state == AcceleratedContainerState.IDLE }
        if (idle != null) {
            logger.info { "Invoking $request on running container $idle" }
            val node = rm.getNode(idle)
            val response = Clients.getNode(node.address).invoke(idle.uniqueName, request.params)
            return HalFaasOuterClass.InvokeResponse.newBuilder().setResult(response).build()
        }

        // try to place new container...
        // TODO notes
        return HalFaasOuterClass.InvokeResponse.getDefaultInstance()
    }
}
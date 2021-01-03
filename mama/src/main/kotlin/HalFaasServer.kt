import halfaas.proto.client.HalFaasGrpcKt
import halfaas.proto.client.HalFaasOuterClass
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import mu.KotlinLogging

class HalFaasServer(val rm: ResourceManager, val im: InvokeManager) : HalFaasGrpcKt.HalFaasCoroutineImplBase() {
    private val logger = KotlinLogging.logger {}

    override suspend fun registerWorkload(request: HalFaasOuterClass.RegisterWorkloadRequest): HalFaasOuterClass.RegisterWorkloadResponse {
        rm.workloads.add(Workload(
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
        val accType = rm.workloads.find { it.name == request.workloadName }!!.acceleratorType
        val invoc = Invocation(request.workloadName, request.params, Channel(), accType)
        im.registerInvocation(invoc)
        logger.info { "Waiting for my turn: $request" }
        val res = invoc.returnChannel.receive()
        logger.info { "Received answer for $request! Answer is $res" }
        GlobalScope.launch {
            im.tryNextInvoke(request.workloadName)
        }

        return HalFaasOuterClass.InvokeResponse.newBuilder().setResult(res).build()
    }
}
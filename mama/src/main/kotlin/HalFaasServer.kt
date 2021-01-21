import com.google.api.Metric
import hal_faas.monitoring.MetricsCollector
import halfaas.proto.client.HalFaasGrpcKt
import halfaas.proto.client.HalFaasOuterClass
import io.micrometer.core.annotation.Timed
import io.micrometer.core.instrument.LongTaskTimer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import mu.KotlinLogging

class HalFaasServer(val rm: ResourceManager, val im: InvokeManager) : HalFaasGrpcKt.HalFaasCoroutineImplBase() {
    private val logger = KotlinLogging.logger {}
    private val invocationTimer = MetricsCollector.registry.more().longTaskTimer("invocation")

    override suspend fun registerWorkload(request: HalFaasOuterClass.RegisterWorkloadRequest): HalFaasOuterClass.RegisterWorkloadResponse {
        rm.workloads.add(Workload(
            request.workloadName,
            request.acceleratorType,
            request.acceleratorAmount
        ))
        return HalFaasOuterClass.RegisterWorkloadResponse.getDefaultInstance()
    }

    override suspend fun invoke(request: HalFaasOuterClass.InvokeRequest): HalFaasOuterClass.InvokeResponse {
        val timer = invocationTimer.start()
        val accType = rm.workloads.find { it.name == request.workloadName }!!.acceleratorType
        val invoc = Invocation(request.workloadName, request.params, Channel(), accType)
        im.registerInvocation(invoc)
        logger.info { "Registered Invocation, waiting. workloadName=${request.workloadName} params=${request.params}" }
        val res = invoc.returnChannel.receive()
        logger.info { "Received answer for workloadName=${request.workloadName} params=${request.params}! Answer is $res" }
        GlobalScope.launch {
            im.tryNextInvoke(request.workloadName)
        }
        timer.stop()
        return HalFaasOuterClass.InvokeResponse.newBuilder().setResult(res).build()
    }
}
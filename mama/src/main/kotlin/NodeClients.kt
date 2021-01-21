import hal_faas.monitoring.MetricsCollector
import halfaas.proto.NodeManagerGrpcKt
import halfaas.proto.Nodes
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.delay
import mu.KotlinLogging
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Class that holds the connections to all NoMas
 */
class NodeClients {
    companion object {
        private val logger = KotlinLogging.logger {}
        private val createdCounter = MetricsCollector.registry.counter("worker.created")
        private val invokedCounter = MetricsCollector.registry.counter("worker.invoked")
        private val stoppedCounter = MetricsCollector.registry.counter("worker.stopped")

        class NodeClient(private var channel: ManagedChannel, private val address: String) : Closeable {
            private val stub = NodeManagerGrpcKt.NodeManagerCoroutineStub(channel)

            suspend fun create(workload: String, accelerator: String, acceleratorAmount: Int): String {
                val request = Nodes.CreateRequest.newBuilder().setWorkloadName(workload).setAccelerator(accelerator).setAcceleratorAmount(acceleratorAmount).build()
                logger.debug { "Sending create $workload on accelerator $accelerator" }
                val response = stub.create(request)
                createdCounter.increment()
                return response.name
            }

            suspend fun invoke(name: String, params: String): String {
                val request = Nodes.InvokeRequest.newBuilder().setName(name).setParams(params).build()
                logger.debug { "Sending invoke $name with params $params" }
                val response = stub.invoke(request)
                invokedCounter.increment()
                return response.result
            }

            suspend fun stop(name: String) {
                val request = Nodes.StopRequest.newBuilder().setName(name).build()
                logger.debug { "Sending stop $name" }
                stub.stop(request)
                stoppedCounter.increment()
            }

            override fun close() {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
            }

        }

        private val nodes = HashMap<String, NodeClient>()

        fun getNode(address: String): NodeClient {
            var existing = nodes[address]
            if (existing == null) {
                val split = address.split(":")
                logger.debug { "Creating a new node for address $split" }
                val channel = ManagedChannelBuilder.forAddress(split[0], split[1].toInt()).usePlaintext().build()
                existing = NodeClient(channel, address)
                nodes[address] = existing
            }
            return existing
        }
    }
}
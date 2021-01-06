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

        class NodeClient(private var channel: ManagedChannel) : Closeable {
            private val stub = NodeManagerGrpcKt.NodeManagerCoroutineStub(channel)

            suspend fun create(image: String, options: String, command: String): String {
                val request = Nodes.CreateRequest.newBuilder()
                    .setImage(image).setOptions(options).setCommand(command).build()
                logger.debug { "Sending create $image" }
                // TODO
                //val response = stub.create(request)
                //return response.name
                delay(2000)
                return "Container-" + Random.nextInt(10)
            }

            suspend fun invoke(name: String, params: String): String {
                val request = Nodes.InvokeRequest.newBuilder().setName(name).setParams(params).build()
                logger.debug { "Sending invoke $name with params $params" }
                // TODO
                //val response = stub.invoke(request)
                //return response.result
                delay(2000)
                return name
            }

            suspend fun stop(name: String) {
                val request = Nodes.StopRequest.newBuilder().setName(name).build()
                logger.debug { "Sending stop $name" }
                // TODO
                //stub.stop(request)
                delay(2000)
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
                existing = NodeClient(channel)
                nodes[address] = existing
            }
            return existing
        }
    }
}
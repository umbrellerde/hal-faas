import halfaas.proto.NodeManagerGrpcKt
import halfaas.proto.Nodes
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * Class that holds the connections to all NoMas
 */
class NodeClients {
    companion object {
        class NodeClient(private var channel: ManagedChannel) : Closeable {
            private val stub = NodeManagerGrpcKt.NodeManagerCoroutineStub(channel)

            suspend fun create(image: String, options: String, command: String): String {
                val request = Nodes.CreateRequest.newBuilder()
                    .setImage(image).setOptions(options).setCommand(command).build()
                val response = stub.create(request)
                return response.name
            }

            suspend fun invoke(name: String, params: String): String {
                val request = Nodes.InvokeRequest.newBuilder().setName(name).setParams(params).build()
                val response = stub.invoke(request)
                return response.result
            }

            suspend fun stop(name: String) {
                val request = Nodes.StopRequest.newBuilder().setName(name).build()
                val response = stub.stop(request)
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
                val channel = ManagedChannelBuilder.forAddress(split[0], split[1] as Int).usePlaintext().build()
                existing = NodeClient(channel)
                nodes[address] = existing
            }
            return existing
        }
    }
}
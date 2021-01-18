import com.github.kittinunf.fuel.Fuel
import halfaas.proto.NodeManagerGrpcKt
import halfaas.proto.Nodes
import mu.KotlinLogging
import java.lang.IllegalArgumentException
import kotlin.random.Random

class NodeManagerService(val portsStart: Int, val portsEnd: Int) : NodeManagerGrpcKt.NodeManagerCoroutineImplBase() {
    private val logger = KotlinLogging.logger {}

    private val usedPorts = HashSet<Int>()

    private fun getUnusedPort(): Int {
        val randomPort = Random.nextInt(portsStart, portsEnd)
        return if (usedPorts.contains(randomPort)) {
            getUnusedPort()
        } else {
            usedPorts.add(randomPort)
            randomPort
        }
    }

    override suspend fun create(request: Nodes.CreateRequest): Nodes.CreateResponse {
        logger.info { "Create was called with $request" }
        val port = getUnusedPort()
        // TODO run the workload
        return Nodes.CreateResponse.newBuilder().setName("localhost:$port").build()
    }

    override suspend fun invoke(request: Nodes.InvokeRequest): Nodes.InvokeResponse {
        logger.info { "Invoke was called with $request" }
        val (request, response, result) = Fuel.post(request.name).body(request.params).responseString()
        result.fold(
            success = {
                return Nodes.InvokeResponse.newBuilder().setResult(it).build()
            }, failure = {
                throw it
            })

    }

    override suspend fun stop(request: Nodes.StopRequest): Nodes.StopResponse {
        logger.info { "Stop was called with $request" }
        Fuel.delete(request.name)
        usedPorts.remove(request.name.split(":")[1])
        return Nodes.StopResponse.getDefaultInstance()
    }
}
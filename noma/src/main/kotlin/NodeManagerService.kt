import com.github.kittinunf.fuel.Fuel
import halfaas.proto.NodeManagerGrpcKt
import halfaas.proto.Nodes
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.*
import java.lang.IllegalArgumentException
import kotlin.random.Random

class NodeManagerService(private val portsStart: Int, private val portsEnd: Int) : NodeManagerGrpcKt.NodeManagerCoroutineImplBase() {
    private val logger = KotlinLogging.logger {}

    private val processes = HashMap<Int, Process>()

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                processes.forEach {
                    it.value.destroy()
                }
            }
        )
    }

    private fun getUnusedPort(): Int {
        val randomPort = Random.nextInt(portsStart, portsEnd)
        return if (processes[randomPort] != null) {
            getUnusedPort()
        } else {
            randomPort
        }
    }

    override suspend fun create(request: Nodes.CreateRequest): Nodes.CreateResponse {
        logger.info { "Create was called with $request" }
        val port = getUnusedPort()
        logger.debug { "Starting process on port $port" }
        val pb = ProcessBuilder("bash", "startup.sh", "-p", port.toString())
        pb.directory(File("workloads/${request.workloadName}"))
        pb.inheritIO()
        val process = pb.start()
        processes[port] = process
        return Nodes.CreateResponse.newBuilder().setName(port.toString()).build()
    }

    override suspend fun invoke(request: Nodes.InvokeRequest): Nodes.InvokeResponse {
        logger.info { "Invoke was called with $request" }
        val (_, _, result) = Fuel.post("http://localhost:${request.name}/").body(request.params).responseString()
        result.fold(
            success = {
                return Nodes.InvokeResponse.newBuilder().setResult(it).build()
            }, failure = {
                throw it
            })

    }

    override suspend fun stop(request: Nodes.StopRequest): Nodes.StopResponse {
        logger.info { "Stop was called with ${request.name}" }
        processes[request.name.toInt()]!!.destroy()
        processes.remove(request.name.toInt())
        return Nodes.StopResponse.getDefaultInstance()
    }
}
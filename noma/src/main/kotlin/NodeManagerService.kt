import com.github.kittinunf.fuel.Fuel
import halfaas.proto.NodeManagerGrpcKt
import halfaas.proto.Nodes
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.*
import java.lang.Exception
import java.lang.IllegalArgumentException
import kotlin.random.Random

class NodeManagerService() : NodeManagerGrpcKt.NodeManagerCoroutineImplBase() {
    private val logger = KotlinLogging.logger {}

    private val processes = ArrayList<Process>()

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                processes.forEach {
                    logger.debug { "Shutting down process ${it.pid()}" }
                    it.destroy()
                }
            }
        )
    }

    override suspend fun create(request: Nodes.CreateRequest): Nodes.CreateResponse {
        logger.info { "Create was called with ${request.toString().replace("\n", " ")}" }
        var process: Process? = null
        try {
            val pb = ProcessBuilder("bash", "startup.sh", request.accelerator, request.acceleratorAmount.toString())
            pb.directory(File("workloads/${request.workloadName}"))
            pb.redirectError(ProcessBuilder.Redirect.INHERIT)
            process = pb.start()
        } catch (e: Exception) {
            logger.error("Could not start Process! workloadname=${request.workloadName}", e)
        }
        if (process == null){
            logger.error { "Process is null!" }
            throw IllegalArgumentException()
        }

        processes.add(process)
        logger.info { "Created process ${process.pid()}" }
        return Nodes.CreateResponse.newBuilder().setName(process.pid().toString()).build()
    }

    override suspend fun invoke(request: Nodes.InvokeRequest): Nodes.InvokeResponse {
        logger.info { "Invoke was called on ${request.name} with params: ${request.params}" }
        val process = processes.find { it.pid().toString() == request.name }!!
        process.outputStream.write("${request.params}\n".toByteArray())
        process.outputStream.flush()
        val resp = process.inputStream.bufferedReader().readLine()
        return Nodes.InvokeResponse.newBuilder().setResult(resp).build()
    }

    override suspend fun stop(request: Nodes.StopRequest): Nodes.StopResponse {
        logger.info { "Stop was called with ${request.name}" }
        val process = processes.find { it.pid().toString() == request.name }!!
        processes.remove(process)
        process.destroy()
        return Nodes.StopResponse.getDefaultInstance()
    }
}
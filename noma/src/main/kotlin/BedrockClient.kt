import com.beust.klaxon.Json
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import kotlinx.coroutines.delay
import mu.KotlinLogging
import java.lang.RuntimeException
import java.net.Socket
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

data class InvocationParams(val payload: String)

data class Invocation(val runtime: String, val workload: String, val params: InvocationParams)

@ExperimentalTime
val runtimeTimeout = 20.seconds

/**
 * Implementation according to https://bedrockdb.com/jobs.html
 */
class BedrockClient(url: String = "localhost", port: Int = 8888) {
    private val logger = KotlinLogging.logger {}
    private val client = Socket(url, port)
    private val reader = client.getInputStream().bufferedReader()

    fun createInvocation(inv: Invocation): Boolean {
        logger.debug { "CreateInvocation: $inv" }
        val params = Klaxon().toJsonString(inv.params)
        val message = "CreateJob\nname: ${inv.runtime}.${inv.workload}\ndata: $params\n\n"
        client.getOutputStream().write(message.toByteArray())
        val res = parseResponse()
        return if (res.status == 200) {
            true
        } else {
            logger.warn { "CreateInvocation failed with response: $res" }
            false
        }
    }
    data class BedrockJobResponse(val data: InvocationParams, val jobID: java.lang.Long, val name: String)
    data class ConsumeInvocation(val inv: Invocation, val status: Int)
    fun consumeInvocation(runtime: String = "*", workload: String = "*", timeout: Int = 3600): ConsumeInvocation {
        logger.debug { "ConsumeInvocation: $runtime, $workload, $timeout" }
        val message = "GetJob\nname: $runtime.$workload\nconnection: wait\ntimeout: $timeout\n\n"
        client.getOutputStream().write(message.toByteArray())
        val res = parseResponse()
        return if (res.status == 200) {
            val rawResponse = Klaxon().parse<BedrockJobResponse>(res.payload)!!
            val splitName = rawResponse.name.split(".")
            ConsumeInvocation(Invocation(splitName[0], splitName[1], rawResponse.data), 200)
        } else {
            ConsumeInvocation(Invocation(runtime, workload, InvocationParams("")), res.status)
        }
    }

    /**
     * create table $name ( $parameters );
     */
    fun createTable(name: String, parameters: String) {
        val message = "Query: create table $name ( $parameters );\n\n"
        client.getOutputStream().write(message.toByteArray())
        val res = parseResponse()
        if (res.status != 200) {
            throw RuntimeException("Could not create Table. response=$res")
        }
    }

    data class DatabaseResponse(val status: Int, val response: JsonObject)
    /**
     * select $what from $from;
     */
    private fun select(what: String, from: String = "*"): DatabaseResponse {
        val message = "Query\nquery: select $what from $from;\nformat:json\n\n"
        client.getOutputStream().write(message.toByteArray())
        val res = parseResponse()
        val builder = java.lang.StringBuilder().append(res)
        return DatabaseResponse(res.status, Klaxon().parser().parse(builder) as JsonObject)
    }

    data class RuntimeImplementation(val acceleratorType: String, val name: String, val location: String)
    /**
     * if there is a free accelerator of type $acceleratorType, get runtimeImplementations it might run
     */
    fun getRuntimeImplementation(acceleratorType: String): List<RuntimeImplementation> {
        val message = "Query\nquery: select name, location from runtime_implementation where accelerator_type == $acceleratorType;\nformat:json\n\n"
        return listOf(RuntimeImplementation("","",""))
    }

    fun getRuntimeImplementation(acceleratorType: String, runtime: String): RuntimeImplementation {
        return RuntimeImplementation("", "", "")
    }

    data class ImplementationAndInvocation(val inv: Invocation, val runtime: RuntimeImplementation)
    @ExperimentalTime
    suspend fun getNextRuntimeAndInvocationToStart(acceleratorType: String): ImplementationAndInvocation {
        val query_runtime = "Query\nquery: select * from runtime join blah where runtime_impl.accelerator_type == $acceleratorType"
        val runtimes = listOf("a", "b", "c") // from the query
        for (runtime in runtimes) {
            val query_jobs = "Query\nquery: select name from jobs where name GLOB $runtime.*"
            val res = ""
            if (res.isNotEmpty()){
                val inv = consumeInvocation(runtime="runtime", timeout = 5)
                if (inv.status != 200)
                    continue
                return ImplementationAndInvocation(inv.inv, getRuntimeImplementation(acceleratorType, runtime))
            }
        }
        logger.info { "There are no Runtimes that this accelerator can run at the moment. Waiting 20s and trying again" }
        delay(runtimeTimeout.toLongMilliseconds())
        return getNextRuntimeAndInvocationToStart(acceleratorType)
    }

    

    private data class ParsedResponse(val status: Int, val payload: String, val debugInformation: String)
    /**
     * call this instead of reading the response from the socket
     */
    private fun parseResponse(): ParsedResponse {
        val debugInformation: java.lang.StringBuilder = java.lang.StringBuilder().append(reader.readLine())
        val status = debugInformation.substring(0,3).toInt()
        while (true) {
            val line = reader.readLine()
            if (line.startsWith("Content-Length: ")) {
                // This is the final line
                val restLen = line.replace("Content-Length: ", "").toInt()
                val emptyLine = reader.readLine()
                val contentLineBuilder = StringBuilder()
                repeat(restLen) {
                    contentLineBuilder.append(reader.read().toChar())
                }
                return ParsedResponse(status, contentLineBuilder.toString(), debugInformation.toString())
            } else if (line.isEmpty()) {
                // This is weird
                return ParsedResponse(status, "", debugInformation.toString())
            } else {
                debugInformation.append("\n" + line)
            }
        }
    }

    fun close(){
        client.close()
    }
}
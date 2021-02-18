import com.beust.klaxon.Klaxon
import mu.KotlinLogging
import java.net.Socket

data class InvocationParams(val payload: String)

data class Invocation(val runtime: String, val workload: String, val params: InvocationParams)

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

    data class ParsedResponse(val status: Int, val payload: String, val debugInformation: String)
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
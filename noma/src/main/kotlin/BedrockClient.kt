import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import mu.KotlinLogging
import java.lang.RuntimeException
import java.net.Socket

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

    fun consumeInvocation(runtime: String = "*", workload: String = "*", timeout_s: Int = 3600): ConsumeInvocation {
        logger.debug { "ConsumeInvocation: $runtime, $workload, $timeout_s" }
        val message = "GetJob\nname: $runtime.$workload\nconnection: wait\ntimeout: $timeout_s\n\n"
        val res = runCommand(message)
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
     * parameters are foo int, bar int
     */
    fun createTable(name: String, parameters: String) {
        val message = "Query: create table $name ( $parameters );\n\n"
        val res = runCommand(message)
        if (res.status != 200) {
            throw RuntimeException("Could not create Table. response=$res")
        }
    }

    /**
     * Get the  location of the runImpl for this acceleratorType from the database
     */
    fun getRuntimeImplementation(acceleratorType: String, runtimeName: String): RuntimeImplementation {
        val queryRuntime =
            "Query\nquery: select location from runtime_impl" +
                    "where runtime_id=(select runtime_id from runtime where name='$runtimeName') " +
                    "and acceleratorType=$acceleratorType;\nformat:json\n\n"
        val runtimeResponse = runCommandJson(queryRuntime)
        if (runtimeResponse.status != 200) {
            throw RuntimeException("Got status != 200 from query $queryRuntime. Response=${runtimeResponse.debugInformation}")
        }
        val json = turnBedrockJsonToListOfList(runtimeResponse.response)
        return RuntimeImplementation(acceleratorType, runtimeName, json[0][0])
    }

    fun getNextRuntimeAndInvocationToStart(acceleratorType: String, acceleratorAmount: Int):
            ImplementationAndInvocation {
        // List of all runtimes that can be run on this acceleratorType
        val queryRuntime =
            "Query\nquery: select runtime.name from runtime " +
                    "left join runtime_impl on runtime_impl.runtime_id = runtime.runtime_id" +
                    "where runtime_impl.accelerator_type='$acceleratorType';\nformat: json\n\n"
        val jsonResponse = runCommandJson(queryRuntime)
        val runtimes = turnBedrockJsonToListOfList(jsonResponse.response)

        for (runtime in runtimes) {
            val runtimeName = runtime[0]
            // TODO maybe just consume an invocation with timeout 0?
            //List of all workloads that can be run on this runtime with this acceleratorAmount
            logger.debug { "Searching for invocations for runtime $runtimeName" }
            val queryWorkloads =
                    "Query:\n" +
                    "query: select name from workload_impl " +
                    "left join runtime_impl on runtime_impl.runtime_impl_id = workload_impl.runtime_impl_id " +
                    "where workload_impl.accelerator_amount > $acceleratorAmount" +
                    ";\nformat: json\n\n"
            val availWorkloads = turnBedrockJsonToListOfList(runCommandJson(queryWorkloads).response)
            for (job in availWorkloads) {
                logger.debug { "Searching for invocations for workload ${job[0]} on runtime $runtimeName" }
                val queryJobs = "Query\nquery: select name from jobs where name = $runtimeName.${job[0]};\nformat: json\n\n"
                val availJobs = runCommand(queryJobs)
                if (availJobs.payload.isNotEmpty()) {
                    val inv = consumeInvocation(runtime = runtimeName, timeout_s = 5)
                    if (inv.status != 200)
                        continue
                    return ImplementationAndInvocation(
                        true, inv.inv, getRuntimeImplementation(
                            acceleratorType,
                            runtimeName
                        )
                    )
                }
            }
        }
        return ImplementationAndInvocation(
            false,
            Invocation("", "", InvocationParams("")),
            RuntimeImplementation("", "", "")
        )
    }


    private data class ParsedResponse(val status: Int, val payload: String, val debugInformation: String)

    /**
     * call this instead of reading the response from the socket
     */
    private fun parseResponse(): ParsedResponse {
        val debugInformation = StringBuilder().append(reader.readLine())
        val status = debugInformation.substring(0, 3).toInt()
        while (true) {
            val line = reader.readLine()
            when {
                line.startsWith("Content-Length: ") -> {
                    // This is the final line
                    val restLen = line.replace("Content-Length: ", "").toInt()
                    val emptyLine = reader.readLine()
                    val contentLineBuilder = StringBuilder()
                    repeat(restLen) {
                        contentLineBuilder.append(reader.read().toChar())
                    }
                    return ParsedResponse(status, contentLineBuilder.toString(), debugInformation.toString())
                }
                line.isEmpty() -> {
                    // This is weird
                    return ParsedResponse(status, "", debugInformation.toString())
                }
                else -> debugInformation.append(line + "\n")
            }
        }
    }

    private fun runCommand(query: String): ParsedResponse {
        client.getOutputStream().write(query.toByteArray())
        return parseResponse()
    }

    data class JsonResponse(val status: Int, val response: JsonObject, val debugInformation: String)

    fun runCommandJson(query: String): JsonResponse {
        val res = runCommand(query)
        val builder = java.lang.StringBuilder().append(res.payload)
        return JsonResponse(res.status, Klaxon().parser().parse(builder) as JsonObject, res.debugInformation)
    }

    fun turnBedrockJsonToListOfList(obj: JsonObject): List<List<String>> {
        val rows = obj["rows"] as JsonArray<*>
        val res = ArrayList<ArrayList<String>>()
        for (row in rows) {
            val items = row as JsonArray<*>
            val newElem = ArrayList<String>()
            for (item in items) {
                newElem.add(item.toString())
            }
            res.add(newElem)
        }
        return res
    }

    fun close() {
        client.close()
    }

    fun initializeDatasbase() {
        createTable("runtime", "runtime_id integer primary key autoincrement, name text not null")
        parseResponse()
        createTable(
            "runtime_impl",
            "runtime_impl_id integer primary key autoincrement, accelerator_type text not null, location text not null, runtime_id int, Foreign Key (runtime_id) References runtime(runtime_id)"
        )
        parseResponse()
        createTable(
            "workload_impl",
            "workload_impl_id integer primary key autoincrement, accelerator_amount integer not null, runtime_impl_id" +
                    " integer, Foreign Key (runtime_impl_id) References runtime_impl(runtime_impl_id)"
            )
        println("Initialized Database: ${parseResponse()}")
        // Query: insert into runtime (name) values ('test'); hat lastInsertRowID: 1
        // Query: insert into runtime_impl (accelerator_type, location, runtime_id) values ('acc', 'loc', 1);
        // Query: select * from runtime_impl where runtime_id=(select runtime_id from runtime where name='test');

    }
}
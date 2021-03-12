import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import kotlinx.coroutines.delay
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
        val message = "CreateJob\nname: ${inv.runtime}.${inv.configuration}\ndata: $params\n\n"
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

    suspend fun consumeInvocation(runtime: String = "*", workload: String = "*", timeout_s: Int = 30):
    ConsumeInvocation {
        logger.debug { "ConsumeInvocation: $runtime, $workload, $timeout_s" }
        val timeoutReachedMs = System.currentTimeMillis() + (timeout_s * 1000)
        val message = "GetJob\nname: $runtime.$workload\nconnection: wait\ntimeout: $timeout_s\n\n"
        var res: ParsedResponse
        do {
            res = runCommand(message)
            // TODO Bedrock doesn't recognize timeout parameter??
            delay(1100)
        } while (res.status == 404 && timeoutReachedMs < System.currentTimeMillis())

        return if (res.status == 200) {
            val rawResponse = Klaxon().parse<BedrockJobResponse>(res.payload)!!
            val splitName = rawResponse.name.split(".")
            ConsumeInvocation(Invocation(splitName[0], splitName[1], rawResponse.data), 200)
        } else {
            ConsumeInvocation(Invocation(runtime, workload, InvocationParams("", "")), res.status)
        }
    }

    /**
     * create table $name ( $parameters );
     * parameters are foo int, bar int
     */
    private fun createTable(name: String, parameters: String): ParsedResponse {
        val message = "Query: create table $name ( $parameters );\n\n"
        return runCommand(message)
    }

    /**
     * Get the  location of the runImpl for this acceleratorType from the database
     */
    fun getRuntimeImplementation(acceleratorType: String, runtimeName: String): RuntimeImplementation {
        // Query: select location from runtime_impl where runtime_id = (select runtime_id from runtime where name='helloWorld') and accelerator_type = 'gpu';
        val queryRuntime =
            "Query\nquery: select location from runtime_impl " +
                    "where runtime_id = (select runtime_id from runtime where name='$runtimeName') " +
                    "and accelerator_type = '$acceleratorType';\nformat:json\n\n"
        val runtimeResponse = runCommandJson(queryRuntime)
        if (runtimeResponse.status != 200) {
            throw RuntimeException("Got status != 200 from query $queryRuntime. Response=${runtimeResponse.debugInformation}")
        }
        val json = turnBedrockJsonToListOfList(runtimeResponse.response)
        return RuntimeImplementation(acceleratorType, runtimeName, json[0][0])
    }

    suspend fun getNextRuntimeAndInvocationToStart(acceleratorType: String, acceleratorAmount: Int):
            ImplementationAndInvocation {
        // List of all runtimes that can be run on this acceleratorType
        // select name from runtime r left join runtime_impl ri on r.runtime_id = ri.runtime_id where ri.accelerator_type = 'gpu';
        val queryRuntime =
            "Query\nquery: select name from runtime r " +
                    "left join runtime_impl ri on r.runtime_id = ri.runtime_id " +
                    "where ri.accelerator_type ='$acceleratorType';\nformat: json\n\n"
        val jsonResponse = runCommandJson(queryRuntime)
        val runtimes = turnBedrockJsonToListOfList(jsonResponse.response)

        for (runtime in runtimes) {
            val runtimeName = runtime[0]
            // TODO maybe just consume an invocation with timeout 0?
            //List of all workloads that can be run on this runtime with this acceleratorAmount
            logger.debug { "Searching for invocations for runtime $runtimeName" }
            // select name from workload_impl wi left join runtime_impl ri on ri.runtime_impl_id = wi.runtime_impl_id where wi.accelerator_amount < 200;
            val queryWorkloads =
                "Query\nquery: select name, accelerator_amount from workload_impl wi " +
                        "left join runtime_impl ri on ri.runtime_impl_id = wi.runtime_impl_id " +
                        "where wi.accelerator_amount <= $acceleratorAmount" +
                        ";\nformat: json\n\n"
            val availWorkloads = turnBedrockJsonToListOfList(runCommandJson(queryWorkloads).response)
            for (job in availWorkloads) {
                logger.debug { "Searching for invocations for workload ${job[0]} on runtime $runtimeName" }
                val queryJobs =
                    "Query\nquery: select name from jobs where name = '$runtimeName.${job[0]}';\nformat: json\n\n"
                val availJobs = runCommand(queryJobs)
                if (availJobs.payload.isNotEmpty()) {
                    val inv = consumeInvocation(runtime = runtimeName, timeout_s = 5)
                    if (inv.status != 200) {
                        logger.debug { "Invocation $inv is not suitable for starting" }
                        continue
                    }
                    logger.debug { "Found invocation to start: $inv" }
                    return ImplementationAndInvocation(
                        true, inv.inv, getRuntimeImplementation(
                            acceleratorType,
                            runtimeName,
                        ),
                        job[1].toInt()
                    )
                }
            }
        }
        return ImplementationAndInvocation(
            false,
            Invocation("", "", InvocationParams("", "")),
            RuntimeImplementation("", "", ""), 0
        )
    }


    private data class ParsedResponse(val status: Int, val payload: String, val debugInformation: String) {
        override fun toString(): String {
            return "CustomParsedResponse(status=$status, payload=$payload, debugInformation=${debugInformation
                .replace("\n", " ")})"
        }
    }

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
                    if (restLen == 0 && status == 200) {
                        return ParsedResponse(204, "", debugInformation.toString())
                    }
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

    private fun runCommandJson(query: String): JsonResponse {
        val res = runCommand(query)
        logger.debug { "Trying to parse $res as Json (query=${query.replace("\n", " ")})" }
        return JsonResponse(res.status, Klaxon().parser().parse(java.lang.StringBuilder(res.payload)) as
                JsonObject,
            res
            .debugInformation)
    }

    private fun turnBedrockJsonToListOfList(obj: JsonObject): List<List<String>> {
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

    fun getQueuedAmount(): Int {
        val res = runCommand("Query: select count(*) from jobs where state='QUEUED';\n\n")
        return res.payload.split("\n")[1].toInt()
    }

    fun close() {
        client.close()
    }

    fun initializeDatasbase() {
        // Check if the first table already exists, if yes skip this
        var resJson = turnBedrockJsonToListOfList(runCommandJson("Query\n" +
                "query: select count(*) from sqlite_master where type='table' and name='runtime';\n" +
                "format: json\n\n").response)
        if (resJson[0][0].toInt() == 1) {
            logger.info { "Database is already initialized..." }
            return
        }

        var res = createTable("runtime", "runtime_id integer primary key autoincrement, name text not null")
        logger.info { "Create runtime: $res" }
        res = createTable(
            "runtime_impl",
            "runtime_impl_id integer primary key autoincrement, accelerator_type text not null, location text not null, runtime_id int, Foreign Key (runtime_id) References runtime(runtime_id)"
        )
        logger.info { "Create runtime_impl: $res" }
        res = createTable(
            "workload_impl",
            "workload_impl_id integer primary key autoincrement, name text not null, accelerator_amount integer not " +
                    "null, runtime_impl_id" +
                    " integer, Foreign Key (runtime_impl_id) References runtime_impl(runtime_impl_id)"
        )
        logger.info { "Create workload_impl: $res" }
        res = runCommand("Query: Insert into runtime (name) values ('helloWorld');\n\n")
        logger.info { "Insert runtime: $res" }
        res = runCommand("Query: Insert into runtime_impl (accelerator_type, location, runtime_id) values ('gpu', " +
                "'helloWorld', 1);\n\n")
        logger.info { "Insert runtime_impl: $res" }
        res = runCommand(
            "Query: Insert into workload_impl (name, accelerator_amount, runtime_impl_id) values ('wl-1', 20, 1);\n\n"
        )
        logger.info { "Insert workload_impl: $res" }
        res = runCommand("Query: Insert into workload_impl (name, accelerator_amount, runtime_impl_id) values " +
                "('wl-2', 20," +
                " 1" +
                ");\n\n")
        logger.info { "Insert workload_impl: $res" }
        // Query: insert into runtime (name) values ('test'); hat lastInsertRowID: 1
        // Query: insert into runtime_impl (accelerator_type, location, runtime_id) values ('acc', 'loc', 1);
        // Query: select * from runtime_impl where runtime_id=(select runtime_id from runtime where name='test');

    }
}
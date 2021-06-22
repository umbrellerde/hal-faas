import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.math.round
import kotlin.system.exitProcess

data class BenchmarkDefinition(
    val p0Duration: Int = Settings.p0duration, val p0Trps: Int = Settings.p0trps,
    val p1Duration: Int = Settings.p1duration,
    val p2Duration: Int = Settings.p2duration, val p2Trps: Int = Settings.p2trps
)

class BenchmarkRunner(
    val bw: BenchmarkWriter,
    val bench: BenchmarkDefinition,
    val runtime: String = "onnx",
    val workload: String = "test|tinyyolov2-7.onnx",
    val payloadReference: S3File = S3File(
        S3Bucket(bucketName = "test"),
        "input_0_tiny.pb"
    ),
    val callbackBase: String = Settings.callbackBaseUrl,
    val payload: String = ""
) {
    private val logger = KotlinLogging.logger {}
    private fun createInvocation(bc: BedrockClient) {
        val params = if (payload != "") {
            InvocationParams(
                PayloadTypes.VALUE, S3File.empty(), payload, S3Bucket(bucketName = "results"),
                callbackBase + "/" + RandomIDGenerator.next()
            )
        } else {
            InvocationParams(
                PayloadTypes.REFERENCE, payloadReference, "", S3Bucket(bucketName = "results"),
                callbackBase + "/" + RandomIDGenerator.next()
            )
        }
        val inv =
            Invocation(
                runtime, workload, params
            )
        bw.collectStart(inv)
        bc.createInvocation(inv)
    }

    private val clientList = ArrayList<BedrockClient>()
    private fun getClientNumber(i: Int):BedrockClient {
        while (i > (clientList.size - 1)) {
            clientList.add(BedrockClient())
        }
        return clientList[i]
    }

    private fun launchConcurrent(trps: Int, step: String): Long {
        val thisRoundStart = System.currentTimeMillis()
        repeat(trps) {
            GlobalScope.launch {
                // TODO maybe this is not fast enough wen aiming for higher TPS? This creates $tps Instances of
                // TODO BedrockClient every second
                val bc = getClientNumber(it)
                createInvocation(bc)
                delay(100)
            }
        }
        val nextRound = 1000 + thisRoundStart
        val timeToSleep = nextRound - System.currentTimeMillis()
        if (timeToSleep < 0) {
            logger.error {
                "Warning: $step Could not keep up with starting Coroutines for test! " +
                        "trps=$trps, start=$thisRoundStart"
            }
            exitProcess(1)
        }
        return timeToSleep
    }

    suspend fun doP0(startP0: Long = System.currentTimeMillis()) {
        val endP0 = startP0 + bench.p0Duration
        while (System.currentTimeMillis() < endP0) {
            val timeToSleep = launchConcurrent(bench.p0Trps, "P0")
            delay(timeToSleep)
        }
    }

    suspend fun doP1(startP1: Long = System.currentTimeMillis()) {
        val endP1 = startP1 + bench.p1Duration
        //  Normalize the difference in tprs to 0..100 so that the formula
        // p0Trps + (trpsDiffNorm * percentDone)
        // gives the trps for any second
        val trpsDiffNorm = (bench.p2Trps * 1.0 - bench.p0Trps) / 100
        while (System.currentTimeMillis() < endP1) {
            val percentDone = 1 - ((endP1 - System.currentTimeMillis()) / (bench.p1Duration * 1.0))
            val trps = round(bench.p0Trps + (percentDone * 100) * trpsDiffNorm)
            logger.debug { "TRPS: $trps" }
            val timeToSleep = launchConcurrent(trps.toInt(), "P1")
            delay(timeToSleep)
        }
    }

    suspend fun doP2(startP2: Long = System.currentTimeMillis()) {
        val endP2 = startP2 + bench.p2Duration
        while (System.currentTimeMillis() < endP2) {
            val timeToSleep = launchConcurrent(bench.p2Trps, "P2")
            delay(timeToSleep)
        }
        clientList.forEach { it.close() }
    }
}
import com.beust.klaxon.Klaxon
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging

data class BenchmarkDefinition(
    val p0Duration: Long = 60_000, val p0Trps: Int = 0,
    val p1Duration: Long = 60_000,
    val p2Duration: Long = 180_000, val p2Trps: Int = 15
)

class BenchmarkRunner(
    val bc: BedrockClient,
    val bw: BenchmarkWriter,
    val bench: BenchmarkDefinition,
    val runtime: String = "onnx",
    val workload: String = "test|yolov4.onnx",
    val payload: S3File = S3File(
        S3Bucket(bucketName = "test"),
        "street.jpg"
    ),
    val callbackBase: String = "localhost:3358",
) {
    private val logger = KotlinLogging.logger {}
    private fun createInvocation(bc: BedrockClient) {
        val inv =
            Invocation(
                runtime, workload, InvocationParams(
                    PayloadTypes.REFERENCE, payload, "", S3Bucket(bucketName = "results"),
                    callbackBase + "/" + RandomIDGenerator.next()
                )
            )
        bw.collectStart(inv)
        bc.createInvocation(inv)
    }

    private fun launchConcurrent(trps: Int, step: String): Long {
        val thisRoundStart = System.currentTimeMillis()
        repeat(trps) {
            GlobalScope.launch {
                // TODO maybe this is not fast enough wen aiming for higher TPS? This creates $tps Instances of
                // TODO BedrockClient every second
                val bc = BedrockClient()
                createInvocation(bc)
                bc.close()
            }
        }
        val thisRoundEnd = System.currentTimeMillis()
        val thisRoundTime = thisRoundEnd - thisRoundStart
        val timeToSleep = 1000 - thisRoundTime
        if (timeToSleep < 0) {
            logger.error {
                "Warning: $step Could not keep up with starting Coroutines for test! " +
                        "trps=$trps, start=$thisRoundStart, " +
                        "end=$thisRoundEnd, time=$thisRoundTime"
            }
            return 0
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
        val trpsDiffNorm = (bench.p2Trps - bench.p0Trps) / 100
        while (true) {
            val currentTime = System.currentTimeMillis()
            if (currentTime >= endP1) break
            val percentDone = bench.p1Duration / (currentTime - endP1)
            val trps = bench.p0Trps + percentDone * trpsDiffNorm
            val timeToSleep = launchConcurrent(bench.p0Trps, "P1")
            delay(timeToSleep)
        }
    }

    suspend fun doP2(startP2: Long = System.currentTimeMillis()) {
        val endP0 = startP2 + bench.p2Duration
        while (System.currentTimeMillis() < endP0) {
            val timeToSleep = launchConcurrent(bench.p2Trps, "P2")
            delay(timeToSleep)
        }
    }
}
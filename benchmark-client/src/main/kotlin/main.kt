import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

fun main(args: Array<String>) {
    Settings.set(args)
    val logger = KotlinLogging.logger {}
    //val benchmark = BenchmarkDefinition(30_000, 6, 90_000, 30_000, 20)
    val benchmark = BenchmarkDefinition()
    val bw = BenchmarkWriter(Settings.runName, benchmark)
    val server = ResultsCollector(bw = bw)
    val runner = BenchmarkRunner(bw, benchmark)

    val queueReporter = QueueReporter(BedrockClient(), bw, 1000)

    if (Settings.runBothDirs) {
        // Switch Benchmarks around
        val otherBenchmark = BenchmarkDefinition(p0Trps = Settings.p2trps, p2Trps = Settings.p0trps)
        val otherRunner = BenchmarkRunner(
            bw,
            otherBenchmark,
            workload = "test|othertinyyolov2-7.onnx"
//            payloadReference = S3File(
//                S3Bucket(bucketName = "test"),
//                "input_0_tiny.pb"
//            )
        )

        GlobalScope.launch {
            logger.info { "Starting Other P0..." }
            otherRunner.doP0()
            logger.info { "Starting Other P1..." }
            otherRunner.doP1()
            logger.info { "Starting Other P2..." }
            otherRunner.doP2()
        }
        runBlocking {
            logger.info { "Starting P0..." }
            runner.doP0()
            logger.info { "Starting P1..." }
            runner.doP1()
            logger.info { "Starting P2..." }
            runner.doP2()
        }

    } else {
        runBlocking {
            logger.info { "Starting P0..." }
            runner.doP0()
            logger.info { "Starting P1..." }
            runner.doP1()
            logger.info { "Starting P2..." }
            runner.doP2()
        }
    }

    logger.info { "Done! Press Enter to Stop collecting Metrics..." }
    val ignored = readLine()
    logger.trace { ignored }

    queueReporter.close()
    bw.writeToFile()
    server.close()
}
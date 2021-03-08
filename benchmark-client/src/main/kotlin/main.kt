import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

fun main() {
    val logger = KotlinLogging.logger {}
    val bw = BenchmarkWriter("firstReal")
    val server = ResultsCollector(bw = bw)
    val bc = BedrockClient()

    val benchmark = BenchmarkDefinition(10_000,  1, 10_000,10_000, 3)
    val runner = BenchmarkRunner(bc, bw, benchmark)
    val queueReporter = QueueReporter(BedrockClient(), bw, 1000)
    runBlocking {
        logger.info { "Starting P0..." }
        runner.doP0()
        logger.info { "Starting P1..." }
        runner.doP1()
        logger.info { "Starting P2..." }
        runner.doP2()
    }

    logger.info { "Done! Press Enter to Stop collecting Metrics..." }
    var ignored = readLine()
    logger.trace { ignored }

    queueReporter.close()
    bw.writeToFile()
    server.close()
}
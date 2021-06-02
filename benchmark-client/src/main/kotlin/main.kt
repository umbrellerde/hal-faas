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
    runBlocking {
        logger.info { "Starting P0..." }
        runner.doP0()
        logger.info { "Starting P1..." }
        runner.doP1()
        logger.info { "Starting P2..." }
        runner.doP2()
    }

    logger.info { "Done! Press Enter to Stop collecting Metrics..." }
    val ignored = readLine()
    logger.trace { ignored }

    queueReporter.close()
    bw.writeToFile()
    server.close()
}
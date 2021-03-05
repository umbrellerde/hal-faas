import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import kotlin.time.ExperimentalTime

@ExperimentalTime
suspend fun main() {
    val logger = KotlinLogging.logger {}
    var read: String? = ""
//    logger.info { "Press Enter to start" }
//    read = readLine()
//    logger.trace { "Read line $read" }

    val c = BedrockClient()
    c.initializeDatasbase()
    logger.debug { "Initialized Database." }


//    logger.info { "Press Enter to start Node Manager:" }
//    read = readLine()
//    logger.trace { "Read line $read" }
    val noMa = NodeManager()

    logger.info { "Press Enter to stop:" }
    read = readLine()
    logger.trace { "Read line $read" }
    runBlocking {
        noMa.close()
    }
}
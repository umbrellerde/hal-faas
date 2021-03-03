import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import kotlin.time.ExperimentalTime

@ExperimentalTime
suspend fun main() {
    val logger = KotlinLogging.logger {}
    logger.info { "Press Enter to start" }
    var read = readLine()
    logger.trace { "Read line $read" }
    val c = BedrockClient()

    // val res = c.initializeDatasbase()
    // logger.debug { "Initialized Database: $res" }

    logger.info { "Press Enter to create invocations:" }
    read = readLine()
    logger.trace { "Read line $read" }
    repeat(10) {
        c.createInvocation(Invocation("helloWorld", "wl-1", InvocationParams("i=$it")))
    }


//    logger.info { "Press Enter to create second invocation:" }
//    read = readLine()
//    logger.trace { "Read line $read" }
//    c.createInvocation(Invocation("helloWorld", "wl-2", InvocationParams("Yolo!")))

    logger.info { "Press Enter to start Node Manager:" }
    read = readLine()
    logger.trace { "Read line $read" }

    val noMa = NodeManager()

    logger.info { "Press Enter to stop:" }
    read = readLine()
    logger.trace { "Read line $read" }

    runBlocking {
        noMa.close()
    }
}
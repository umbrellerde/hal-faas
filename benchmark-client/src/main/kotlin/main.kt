import mu.KotlinLogging

fun main() {
    val logger = KotlinLogging.logger {}
    val server = ResultsCollector()
    val c = BedrockClient()

    logger.info { "Press Enter to create invocations:" }
    var read = readLine()
    logger.trace { "Read line $read" }
    repeat(10) {
        c.createInvocation(Invocation("helloWorld", "wl-1", InvocationParams("i=$it", "localhost:3358")))
        c.createInvocation(Invocation("helloWorld", "wl-2", InvocationParams("i=${it+10}", "localhost:3358")))
    }

    logger.info { "Press enter to stop:" }
    read = readLine()
    logger.trace { "Got line $read" }

    server.close()
}
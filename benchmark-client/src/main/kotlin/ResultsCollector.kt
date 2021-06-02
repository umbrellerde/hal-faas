import com.beust.klaxon.Klaxon
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import mu.KotlinLogging
import java.net.InetSocketAddress

class ResultsCollector(
    private val hostname: String = Settings.callbackServerHost,
    private val port: Int = 3358,
    private val bw: BenchmarkWriter
) {
    private val server = HttpServer.create(InetSocketAddress(hostname, port), 0)

    init {
        server.createContext("/", ResultsHandler(bw))
        server.start()
    }

    fun close() {
        server.stop(0)
    }

    private class ResultsHandler(private val bw: BenchmarkWriter) : HttpHandler {
        private val logger = KotlinLogging.logger {}
        override fun handle(t: HttpExchange?) {
            if (t == null) {
                return
            }
            val body = t.requestBody.bufferedReader().use { it.readText() }
            var invRes = Klaxon().parse<InvocationResult>(body)
            t.sendResponseHeaders(200, "".toByteArray().size.toLong())
            t.responseBody.write("".toByteArray())
            logger.debug { "Response $invRes on address ${t.requestURI.path}" }
            if (invRes == null) {
                logger.warn { "Response to ${t.requestURI.path} ($body) could not be parsed as JSON..." }
                invRes = InvocationResult.empty()
            }
            bw.collectEnd(t.requestURI.path, invRes)
            t.close()
        }
    }
}
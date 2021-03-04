import com.beust.klaxon.Klaxon
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import mu.KotlinLogging
import java.net.InetSocketAddress

class ResultsCollectorServer(private val hostname: String = "localhost", private val port: Int = 3358) {
    private val server = HttpServer.create(InetSocketAddress(hostname, port), 0)

    init {
        server.createContext("/", ResultsHandler())
        server.start()
    }

    fun close() {
        server.stop(0)
    }

    private class ResultsHandler: HttpHandler {
        private val logger = KotlinLogging.logger {}
        override fun handle(t: HttpExchange?) {
            if (t == null) {
                return
            }
            val body = t.requestBody.bufferedReader().use { it.readText() }
            val invRes = Klaxon().parse<InvocationResult>(body)
            t.sendResponseHeaders(200, "".toByteArray().size.toLong())
            t.responseBody.write("".toByteArray())
            logger.info { "Response $invRes on address ${t.requestURI.path}" }
            t.close()
        }
    }
}
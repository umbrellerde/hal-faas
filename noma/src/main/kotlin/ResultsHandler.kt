import com.beust.klaxon.Klaxon
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class ResultsHandler {
    companion object {
        private val logger = KotlinLogging.logger {}
        val client = HttpClient.newHttpClient()
        fun returnResult(inv: Invocation, result: String) {
            val invRes = InvocationResult(inv.params.payload, result)
            val invResString = Klaxon().toJsonString(invRes)
            val request = HttpRequest.newBuilder().uri(
                URI.create(
                    "http://${inv.params.callbackUrl}/${inv.runtime}/${inv.workload}"
                )
            ).POST(HttpRequest.BodyPublishers.ofString(invResString)).build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() > 299 || response.statusCode() < 200) {
                logger.warn { "ResultsHandler: Server returned an error! response=$response, inv=$inv, result=$result" }
            } else {
                logger.debug { "Successfully sent response" }
            }
        }
    }
}
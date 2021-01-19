import io.grpc.Server
import io.grpc.ServerBuilder
import mu.KotlinLogging

class NodeManagerServer(private val port: Int) {
    private val logger = KotlinLogging.logger {}
    val server: Server = ServerBuilder.forPort(port).addService(NodeManagerService()).build()

    fun start() {
        server.start()
        logger.info { "Starting Server on port $port" }
        Runtime.getRuntime().addShutdownHook(
            Thread {
                logger.info { "Shutting Down GRPC Server" }
                this@NodeManagerServer.server.shutdown()
            }
        )
    }

    fun blockUntilShutdown() {
        server.awaitTermination()
    }
}
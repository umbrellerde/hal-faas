import java.io.File
import java.nio.file.Paths

fun main(args: Array<String>) {
    val port = args[0].toInt()
    val server = NodeManagerServer(port)
    server.start()
    server.blockUntilShutdown()
}
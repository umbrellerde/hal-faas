import java.io.File
import java.nio.file.Paths

fun main(args: Array<String>) {
    val port = args[0] as Int
    val workloadPortStart = args[1] as Int
    val workloadPortEnd = args[2] as Int
    val server = NodeManagerServer(port, workloadPortStart, workloadPortEnd)
    server.start()
    server.blockUntilShutdown()
}
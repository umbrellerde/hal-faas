fun main(args: Array<String>) {
    val port = args[0] as Int
    val server = NodeManagerServer(port)
    server.start()
    server.blockUntilShutdown()
}
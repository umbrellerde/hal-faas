import mu.KotlinLogging
import java.io.*
import java.lang.Exception
import java.lang.IllegalArgumentException

class Processes {
    companion object {
        private val logger = KotlinLogging.logger {}

        private val processes = ArrayList<Process>()
        init {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    processes.forEach {
                        logger.debug { "Shutting down process ${it.pid()}" }
                        it.destroy()
                    }
                }
            )
        }

        // TODO maybe the acceleratorAmount can be stored in the runtime? Although it might depend on the accelerator chosen (older model gets more resources etc??)
        fun startProcess(runtime: RuntimeImplementation, accelerator: String, acceleratorAmount: String): String {
            logger.info { "Create was called with $runtime on $accelerator (Qty: $acceleratorAmount)" }
            var process: Process? = null
            try {
                val pb = ProcessBuilder("bash", "startup.sh", accelerator, acceleratorAmount)
                pb.directory(File("runtimes/${runtime.location}"))
                pb.redirectError(ProcessBuilder.Redirect.INHERIT)
                process = pb.start()
            } catch (e: Exception) {
                logger.error("Could not start Process! workloadname=workloadName", e)
            }
            if (process == null){
                logger.error { "Process is null!" }
                throw IllegalArgumentException()
            }

            processes.add(process)
            logger.info { "Created process ${process.pid()}" }
            return process.pid().toString()
        }

        fun invoke(name: String, params: String): String {
            logger.info { "Invoke was called on $name with params: $params" }
            val process = processes.find { it.pid().toString() == name }!!
            process.outputStream.write("$params\n".toByteArray())
            process.outputStream.flush()
            return process.inputStream.bufferedReader().readLine()
        }

        fun stopProcess(name: String) {
            logger.info { "Stop was called with $name" }
            val process = processes.find { it.pid().toString() == name }!!
            processes.remove(process)
            process.destroy()
        }
    }
}
import com.beust.klaxon.Klaxon
import kotlinx.coroutines.delay
import mu.KotlinLogging
import java.io.File
import java.lang.RuntimeException

class Processes {
    companion object {
        private val logger = KotlinLogging.logger {}

        private val processes = ArrayList<Process>()

        init {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    if (processes.isNotEmpty()) {
                        logger.warn { processes.joinToString (", ", prefix = "Currently running:") }
                    }
                    processes.forEach {
                        logger.debug { "Shutting down process ${it.pid()}" }
                        it.destroy()
                    }
                }
            )
        }

        fun startProcess(runtime: RuntimeImplementation, accelerator: String, acceleratorAmount: String): String {
            logger.info { "Create was called with $runtime on $accelerator (Qty: $acceleratorAmount)" }
            var process: Process? = null
            try {
                val pb = ProcessBuilder("bash", "startup.sh", accelerator, acceleratorAmount)
                pb.directory(File("runtimes/${runtime.location}"))
                pb.redirectError(ProcessBuilder.Redirect.INHERIT)
                process = pb.start()
            } catch (e: Exception) {
                logger.error("Could not start Process! accelerator=$accelerator", e)
            }
            if (process == null) {
                logger.error { "Process is null!" }
                throw IllegalArgumentException("Process could not be created")
            }

            processes.add(process)
            logger.info { "Created process ${process.pid()}" }
            return process.pid().toString()
        }

        suspend fun invoke(name: String, inv: Invocation): String {
            logger.info { "Invoke was called on $name with params: $inv" }
            val process = processes.find { it.pid().toString() == name }!!
            val json = Klaxon().toJsonString(inv).replace("\n", "")
            process.outputStream.write("$json\n".toByteArray())
            process.outputStream.flush()
            if (process.inputStream == null) {
                throw RuntimeException("Input Stream of process null. process=$process")
            }
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
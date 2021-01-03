import com.github.kittinunf.fuel.Fuel
import mu.KotlinLogging
import java.io.BufferedReader
import java.lang.IllegalArgumentException

const val CONTAINER_PORT = "5555"

enum class ContainerOp {
    START, STOP, PAUSE, UNPAUSE, RM
}

class DockerHelper {
    companion object {
        private val logger = KotlinLogging.logger {}

        data class CmdOutput(val status: Int, val stdout: String, val stderr: String)

        private fun runCmd(cmd: String): CmdOutput {
            val p = Runtime.getRuntime().exec(cmd)
            p.waitFor()
            val stdout = p.inputStream.bufferedReader().use(BufferedReader::readText)
            val stderr = p.errorStream.bufferedReader().use(BufferedReader::readText)
            val res = CmdOutput(p.exitValue(), stdout, stderr)
            logger.debug { "Ran command $cmd with result $res" }
            return res
        }

        private fun runCmdThrowOnError(command: String) {
            val res = runCmd(command)
            if (res.status != 0) {
                throw IllegalArgumentException("$command returned non0 exit code: $res")
            }
        }

        /**
         * returns the container id of the newly created container
         */
        fun runCreate(image: String, options: String, command: String): String {
            val (status, stdout, stderr) = runCmd("docker ps $options $image $command")
            if (status == 0) {
                logger.debug { "Succesfully created docker container $stdout" }
                return stdout
            } else {
                logger.debug { "runCreate has exit code $status" }
                throw IllegalArgumentException("Could not create container! $stderr")
            }
        }

        fun runOpOnContainer(containerId: String, op: ContainerOp) {
            logger.debug { "Performing Operation $op on container $containerId" }
            runCmdThrowOnError("docker ${op.name.toLowerCase()} $containerId")
        }

        private fun getContainerIp(containerId: String): String {
            val res =  runCmd("docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' $containerId").stdout
            logger.debug { "getContainerIp got IP $res (for container $containerId)" }
            return res
        }

        fun invoke(containerId: String, params: String): String {
            val ip = getContainerIp(containerId)
            val (request, response, result) = Fuel.post(ip + CONTAINER_PORT).body(params).responseString()
            result.fold(
                success = {
                    return it
                }, failure = {
                    throw it
                })
        }
    }
}
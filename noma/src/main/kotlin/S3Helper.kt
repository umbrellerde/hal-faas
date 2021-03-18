import mu.KotlinLogging
import java.io.File

class S3Helper {
    companion object {

        val folder = File("s3cache/").apply { mkdirs() }

        private val logger = KotlinLogging.logger {}
        /**
         * Checks if the ConfigurationInput (s3-link) is already downloaded
         * if not downloads it
         * returns the absolute path to the file
         */
        fun getPathFromConfigurationInput(runtime: String, s3Path: String): String{
            val filename = ""
            // TODO
            val destFile = File(folder, filename)
            return destFile.absolutePath
        }

        /**
         * if the payload_type of the param is REFERENCE, this will download the file and return the place where it
         * is saved.
         */
        fun getPathFomDataInput(runtime: String, s3Path: String): String {
            throw RuntimeException("Implement me!")
            return ""
        }

        /**
         * returns the s3 path to the result
         */
        fun uploadResults(filepath: List<String>): List<String> {
            throw RuntimeException("Implement me!")
            return listOf("")
        }
    }
}
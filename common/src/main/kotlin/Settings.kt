import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default

class Settings {
    companion object {
        private val parser = ArgParser("Hal-FaaS")
        val bedrockHost by parser.option(ArgType.String, shortName = "bedrockHost").default("localhost")
        val bedrockPort by parser.option(ArgType.Int, shortName = "bedrockPort").default(8888)
        val s3AccessKey by parser.option(ArgType.String, shortName = "s3access").default("minio-admin")
        val s3SecretKey by parser.option(ArgType.String, shortName = "s3secret").default("minio-admin")
        val s3Endpoint by parser.option(ArgType.String, shortName = "s3host").default("localhost:9000")

        fun set(args: Array<String>) {
            parser.parse(args)
        }

    }
}
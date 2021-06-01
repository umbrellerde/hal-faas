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
        val s3Endpoint by parser.option(ArgType.String, shortName = "s3host").default("http://localhost:9000")
        val callbackBaseUrl by parser.option(ArgType.String, shortName = "callbackBase", description = "Address/Port that will be prepended to the callback url, without a /").default("localhost:3358")
        val p0trps by parser.option(ArgType.Int, shortName = "p0trps").default(6)
        val p2trps by parser.option(ArgType.Int, shortName = "p2trps").default(20)
        val p0duration by parser.option(ArgType.Int, shortName = "p0dur", description = "in milliseconds").default(30000)
        val p1duration by parser.option(ArgType.Int, shortName = "p1dur", description = "in milliseconds").default(90000)
        val p2duration by parser.option(ArgType.Int, shortName = "p2dur", description = "in milliseconds").default(30000)
        val runName by parser.option(ArgType.String, shortName = "name").default("fulltest")

        fun set(args: Array<String>) {
            parser.parse(args)
        }

    }
}
import mu.KotlinLogging

fun main(args: Array<String>) {
    val logger = KotlinLogging.logger {}
    Settings.set(args)
    S3Helper.getRuntime("onnx")
}
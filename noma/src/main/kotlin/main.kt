import mu.KotlinLogging

fun main(args: Array<String>) {
    val logger = KotlinLogging.logger {}
    val c = BedrockClient()
    c.createInvocation(Invocation("onnx", "yolov3", InvocationParams("I have no idea what i'm doing")))

    println(c.consumeInvocation(workload = "syolov3"))
}
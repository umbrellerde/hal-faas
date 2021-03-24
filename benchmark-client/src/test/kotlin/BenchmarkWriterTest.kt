import org.junit.Test

class BenchmarkWriterTest {

    @Test
    fun writeSimpleResult() {
        val bw = BenchmarkWriter("test", folder = "results-tests")
        val inv1 = Invocation("onnx", "yolov4", InvocationParams("payload", "callback"))
        bw.collectStart(inv1, 100)
        bw.collectEnd(inv1.params.callbackUrl, InvocationResult("params", "result", 110, 115), 150)
        bw.writeToFile()
    }
}
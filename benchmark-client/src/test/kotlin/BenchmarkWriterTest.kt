import org.junit.Test

class BenchmarkWriterTest {

    @Test
    fun writeSimpleResult(){
        val bw = BenchmarkWriter("test", folder = "results-tests")
        val inv1 = Invocation("onnx", "yolov4", InvocationParams("payload", "callback"))
        bw.collectStart(inv1,100)
        bw.collectEnd(inv1.params.callbackUrl, "result", 150)
        bw.writeToFile()
    }
}
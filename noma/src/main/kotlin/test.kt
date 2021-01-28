import halfaas.proto.Nodes
import kotlinx.coroutines.delay

suspend fun main() {
    //File("workloads/helloWorld").walk().forEach { println(it) }
    val service = Processes()
    val pid = service.startProcess(Nodes.CreateRequest.newBuilder().setWorkloadName("helloWorld").build())
    delay(1000)
    val resp =
        service.invoke(Nodes.InvokeRequest.newBuilder().setName(pid.name).setParams("{\"test\": \"abc\"}").build())
    delay(1000)
    val delResp = service.stopProcess(Nodes.StopRequest.newBuilder().setName(pid.name).build())
}
import halfaas.proto.Nodes
import kotlinx.coroutines.delay

suspend fun main() {
    //File("workloads/helloWorld").walk().forEach { println(it) }
    val service = NodeManagerService()
    val pid = service.create(Nodes.CreateRequest.newBuilder().setWorkloadName("helloWorld").build())
    delay(1000)
    val resp =
        service.invoke(Nodes.InvokeRequest.newBuilder().setName(pid.name).setParams("{\"test\": \"abc\"}").build())
    delay(1000)
    val delResp = service.stop(Nodes.StopRequest.newBuilder().setName(pid.name).build())
}
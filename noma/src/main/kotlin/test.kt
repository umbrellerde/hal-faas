import halfaas.proto.Nodes
import java.io.File

suspend fun main() {
    //File("workloads/helloWorld").walk().forEach { println(it) }
    val service = NodeManagerService(10000, 10500)
    val res = service.create(Nodes.CreateRequest.newBuilder().setWorkloadName("helloWorld").build())
    Thread.sleep(1000)
    service.invoke(Nodes.InvokeRequest.newBuilder().setName(res.name).setParams("{'test': 'abc'}").build())
    Thread.sleep(1000)
    service.stop(Nodes.StopRequest.newBuilder().setName(res.name).build())
}
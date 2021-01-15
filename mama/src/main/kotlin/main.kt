import halfaas.proto.client.HalFaasOuterClass
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

fun main(args: Array<String>) {
    System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
    System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");

    val logger = KotlinLogging.logger {}

    val rm = ResourceManager()
    rm.nodes.add(
        Node(
            "node1:5678", arrayListOf(
                Accelerator("gpu", "gpu-1-1", 1000, "Mb", ArrayList()),
                Accelerator("gpu", "gpu-1-2", 1000, "Mb", ArrayList()),
            )
        )
    )
    rm.nodes.add(
        Node(
            "node2:5678", arrayListOf(
                Accelerator("gpu", "gpu-2-1", 1000, "Mb", ArrayList()),
                Accelerator("gpu", "gpu-2-2", 1000, "Mb", ArrayList()),
            )
        )
    )

    val im = InvokeManager(rm)

    val server = HalFaasServer(rm, im)

    runBlocking {
        val regReq1 = HalFaasOuterClass.RegisterWorkloadRequest.newBuilder().setWorkloadName("wl1").setDockerImage("test1").setAcceleratorAmount(500).setAcceleratorType("gpu").build()
        server.registerWorkload(regReq1)
        val regReq2 = HalFaasOuterClass.RegisterWorkloadRequest.newBuilder().setWorkloadName("wl2").setDockerImage("test2").setAcceleratorAmount(500).setAcceleratorType("gpu").build()
        server.registerWorkload(regReq2)

        val wl1 = HalFaasOuterClass.InvokeRequest.newBuilder().setWorkloadName("wl1").setParams("wl1-params").build()
        server.invoke(wl1)

        val wl2 = HalFaasOuterClass.InvokeRequest.newBuilder().setWorkloadName("wl2").setParams("wl2-params").build()
        server.invoke(wl2)

        //val wl3 = HalFaasOuterClass.InvokeRequest.newBuilder().setWorkloadName("wl1").setParams("wl1-params-3").build()

        val jobs = ArrayList<Job>()
        for (i in 1..10) {
            jobs.add(GlobalScope.launch {
                val wl3 = HalFaasOuterClass.InvokeRequest.newBuilder().setWorkloadName("wl1").setParams("wl1-params-$i").build()
                server.invoke(wl3)
            })
        }
        jobs.forEach {it.join()}
//        for (i in 1..50) {
//            server.invoke(wl3)
//        }
    }

}
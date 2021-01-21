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
            "localhost:1555", arrayListOf(
                Accelerator("gpu", "gpu-1-1", 500, "Mb", ArrayList()),
                Accelerator("gpu", "gpu-1-2", 500, "Mb", ArrayList()),
            )
        )
    )
//    rm.nodes.add(
//        Node(
//            "localhost:1555", arrayListOf(
//                Accelerator("gpu", "gpu-2-1", 1000, "Mb", ArrayList()),
//                Accelerator("gpu", "gpu-2-2", 1000, "Mb", ArrayList()),
//            )
//        )
//    )

    val im = InvokeManager(rm)

    val server = HalFaasServer(rm, im)

    logger.info { "Current Setup: ${rm.nodes}" }

    runBlocking {
        val regReq1 = HalFaasOuterClass.RegisterWorkloadRequest.newBuilder().setWorkloadName("helloWorld").setAcceleratorAmount(250).setAcceleratorType("gpu").build()
        server.registerWorkload(regReq1)
//        val regReq2 = HalFaasOuterClass.RegisterWorkloadRequest.newBuilder().setWorkloadName("helloWorld").setDockerImage("test2").setAcceleratorAmount(500).setAcceleratorType("gpu").build()
//        server.registerWorkload(regReq2)

//        val wl1 = HalFaasOuterClass.InvokeRequest.newBuilder().setWorkloadName("wl1").setParams("wl1-params").build()
//        server.invoke(wl1)
//
//        val wl2 = HalFaasOuterClass.InvokeRequest.newBuilder().setWorkloadName("wl2").setParams("wl2-params").build()
//        server.invoke(wl2)

        //val wl3 = HalFaasOuterClass.InvokeRequest.newBuilder().setWorkloadName("wl1").setParams("wl1-params-3").build()

        val jobs = ArrayList<Job>()
        for (i in 1..3) {
            jobs.add(GlobalScope.launch {
                val wl3 = HalFaasOuterClass.InvokeRequest.newBuilder().setWorkloadName("helloWorld").setParams("{\"test\": \"$i\"}").build()
                server.invoke(wl3)
            })
        }
        jobs.forEach {it.join()}
    }

}
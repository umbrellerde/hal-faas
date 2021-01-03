import mu.KotlinLogging

fun main(args: Array<String>) {
    val rm = ResourceManager()
    rm.nodes.add(
        Node(
            "node1", arrayListOf(
                Accelerator("gpu", "gpu-1-1", 1000, "Mb", ArrayList()),
                Accelerator("gpu", "gpu-1-2", 1000, "Mb", ArrayList()),
            )
        )
    )
    rm.nodes.add(
        Node(
            "node2", arrayListOf(
                Accelerator("gpu", "gpu-2-1", 1000, "Mb", ArrayList()),
                Accelerator("gpu", "gpu-2-2", 1000, "Mb", ArrayList()),
            )
        )
    )

    println(rm.nodes)
}
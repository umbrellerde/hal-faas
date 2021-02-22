import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import mu.KotlinLogging

fun main(args: Array<String>) {
//    val logger = KotlinLogging.logger {}
//    val c = BedrockClient()
//    c.createInvocation(Invocation("onnx", "yolov3", InvocationParams("I have no idea what i'm doing")))
//
//    println(c.consumeInvocation(workload = "syolov3"))
//
//    c.close()

//    val jsonString = StringBuilder("""
//{"headers":["foo","bar"],"rows":[[1,2],[2,3]]}
//    """)
//    val parser = Klaxon().parser()
//    val json = parser.parse(jsonString) as JsonObject
//    println((json["rows"] as JsonArray<*>)[0])

    val query = """
        Abc
        def
    """.trimIndent() + "\n\n"
    print(query.toCharArray())
    print("Fin")
}
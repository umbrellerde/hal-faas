enum class PayloadTypes() {
    REFERENCE, VALUE
}

data class S3File(val endpoint: String, val accessKey: String, val secretKey: String, val bucketName: String, val
fileName: String)

data class InvocationParams(
    val payload_type: PayloadTypes = PayloadTypes.REFERENCE,
    val payload: String,
    // callbackUrl to POST the Results to
    val resultFile: S3File,
    val callbackUrl: String
)

data class Invocation(val runtime: String, val configuration: String, val params: InvocationParams)

data class ImplementationAndInvocation(
    val success: Boolean, val inv: Invocation, val runtime: RuntimeImplementation,
    val amount: Int
)

data class ConsumeInvocation(val inv: Invocation, val status: Int)

data class RuntimeImplementation(val acceleratorType: String, val name: String, val location: String)

data class InvocationResult(
    val request: String,
    val accelerator: String,
    val amount: Int,
    val pid: String,
    val result_type: String,
    var result: List<String>,
    val metadata: String,
    var start_computation: Long,
    var end_computation: Long
) {
    companion object {
        fun empty(): InvocationResult {
            return InvocationResult("", "", -1, "", "", listOf(""), "", -1, -1)
        }
    }
}
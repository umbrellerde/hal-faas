enum class PayloadTypes {
    REFERENCE, VALUE
}

data class S3Bucket(val endpoint: String, val accessKey: String, val secretKey: String, val bucketName: String) {
    companion object {
        fun empty(): S3Bucket {
            return S3Bucket("", "", "", "")
        }
    }
}

/**
 * Used for inputdata that is a reference. In this case the inv.params String is of this type.
 */
data class S3File(val bucket: S3Bucket, val file: String)

data class InvocationParams(
    val payload_type: PayloadTypes = PayloadTypes.REFERENCE,
    var payload: String,
    // callbackUrl to POST the Results to
    val resultBucket: S3Bucket,
    val callbackUrl: String
)

data class Invocation(val runtime: String, var configuration: String, val params: InvocationParams)

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
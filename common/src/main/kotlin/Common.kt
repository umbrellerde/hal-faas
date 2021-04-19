import com.beust.klaxon.JsonObject

enum class PayloadTypes {
    REFERENCE, VALUE
}

data class S3Bucket(val endpoint: String = Settings.s3Endpoint, val accessKey: String = Settings.s3AccessKey, val secretKey:
String = Settings.s3SecretKey, val bucketName:
String) {
    companion object {
        fun empty(): S3Bucket {
            return S3Bucket("", "", "", "")
        }
    }
}

/**
 * Used for inputdata that is a reference. In this case the inv.params String is of this type.
 */
data class S3File(val bucket: S3Bucket, val file: String) {
    companion object {
        fun empty() = S3File(S3Bucket.empty(), "")
    }
}

data class InvocationParams(
    val payload_type: PayloadTypes = PayloadTypes.REFERENCE,
    var payload_reference: S3File,
    var payload: String,
    // callbackUrl to POST the Results to
    val resultBucket: S3Bucket,
    val callbackUrl: String
) {
    companion object {
        fun empty() = InvocationParams(PayloadTypes.VALUE, S3File.empty(), "", S3Bucket.empty(),"")
    }
}

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
    var amount: Int? = -1,
    val pid: String,
    val result_type: String,
    var result: java.util.ArrayList<String>,
    var metadata: Map<String, Any>?,
    var start_computation: Long? = -1,
    var end_computation: Long? = -1
) {
    companion object {
        fun empty(): InvocationResult {
            return InvocationResult("", "", -1 as Int, "", "", ArrayList(), null, -1, -1)
        }
    }
}
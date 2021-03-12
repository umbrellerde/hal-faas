enum class PayloadTypes() {
    REFERENCE, VALUE
}

data class InvocationParams(
    val payload_type: PayloadTypes = PayloadTypes.REFERENCE,
    val payload: String,
    // callbackUrl to POST the Results to
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
    val params: String, val result: String, val start_computation: Long, val end_computation:
    Long
)
data class InvocationParams(val payload: String, val callbackUrl: String) // callbackUrl to POST the Results to

data class Invocation(val runtime: String, val workload: String, val params: InvocationParams)

data class ImplementationAndInvocation(val success: Boolean, val inv: Invocation, val runtime: RuntimeImplementation)

data class ConsumeInvocation(val inv: Invocation, val status: Int)

data class RuntimeImplementation(val acceleratorType: String, val name: String, val location: String)

data class InvocationResult(val params: String, val result: String)
interface IDatabseClient {
    fun createInvocation(inv: Invocation): Boolean

    suspend fun consumeInvocation(runtime: String = "*", workload: String = "*", timeout_s: Int = 30):
            ConsumeInvocation

    /**
     * Get the  location of the runImpl for this acceleratorType from the database
     */
    fun getRuntimeImplementation(acceleratorType: String, runtimeName: String): RuntimeImplementation

    suspend fun getNextRuntimeAndInvocationToStart(acceleratorType: String, acceleratorAmount: Int):
            ImplementationAndInvocation

    fun getQueuedAmount(): Int
    fun close()
    fun initializeDatasbase()
}
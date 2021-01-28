import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.DeliverCallback
import hal_faas.rabbitmq.RabbitMQHelper
import hal_faas.rabbitmq.publish
import hal_faas.rabbitmq.subscribe
import hal_faas.rabbitmq.unsubscribe
import hal_faas.serializables.Invocation
import hal_faas.serializables.InvocationResponse
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import mu.KotlinLogging
import kotlinx.serialization.json.*

/**
 * a WorkloadInstance that has a process attached to it, listens to the queue of its workload
 * and stops if there is no request for X seconds.
 */
class QueueListeningWorkloadInstance(workloadName: String, val accelerator: String, val accceleratorAmount: String, initialInvocation: Invocation) {
    private val logger = KotlinLogging.logger {}

    val pid = Processes.startProcess(workloadName, accelerator, accceleratorAmount)
    var lastUsed = System.currentTimeMillis()
    var busy = false
    var cancelled = false

    val channel = RabbitMQHelper.createChannel()
    // The tag that this client will get, necessary to stop the subscription
    var consumerTag: String?

    private fun handleInvocation(inv: Invocation): InvocationResponse {
        logger.info { "WorkloadInstance $pid has received ${inv.toString().replace("\n", " ")}" }
        val res = Processes.invoke(pid, inv.parameters)
        lastUsed = System.currentTimeMillis()
        return InvocationResponse(inv.uniqueId, res)
    }

    fun shutDown(){
        channel.unsubscribe(consumerTag!!)
        cancelled = true
    }

    val deliver: DeliverCallback = DeliverCallback { consumerTag, delivery ->
        busy = true
        val invocation = Json.decodeFromString<Invocation>(delivery.body.toString())
        val res = handleInvocation(invocation)
        channel.publish(invocation.returnQueue, res.toJson())
        busy = false
    }

    val cancel: CancelCallback = CancelCallback { consumerTag ->
        logger.info { "WorkloadInstance $pid has been canceled!!" }
        Processes.stopProcess(pid)
        cancelled = true
    }

    init {
        // Handle the initial invocation that was responsible for creating this workloadInstance
        val res = handleInvocation(initialInvocation)
        channel.publish(initialInvocation.returnQueue, res.toJson())

        // Subscribe to future events
        consumerTag = channel.subscribe(workloadName, deliver, cancel)

        // Shut down if nothing happens for 30secs
        GlobalScope.launch {
            // Every 10 seconds: check if this workloadInstance is free and hasn't been used in the last 30s
            while (!cancelled) {
                delay(10_000)
                val tooLongAgo = System.currentTimeMillis() - 30000
                if (!cancelled && !busy && lastUsed < tooLongAgo) {
                    logger.info { "WorkloadInstance $pid has not been used since $tooLongAgo and is not busy -> Shutting down" }
                    shutDown()
                    break
                }
            }
        }
    }
}
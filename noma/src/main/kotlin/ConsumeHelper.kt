import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.lang.RuntimeException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Runtime -> (Config -> Coroutine)
 */
typealias runningMap = MutableMap<String, MutableMap<String, SingleInvocationConfig>>
/**
 * ConsumeInvocations so that every type of InvocationConfiguration only needs a single coroutine
 */
class ConsumeHelper {
    val logger = KotlinLogging.logger {}
    val running: runningMap = mutableMapOf()
    suspend fun consumeInvocation(runtime: String = "*", config: String = "*", timeout_s: Long = 5):
            ConsumeInvocation? {
        val getter = synchronized(running) {
            if (running[runtime] == null) {
                running[runtime] = HashMap()
            }
            val getter = running[runtime]?.get(config)
            if (getter == null) {
                val newGet = SingleInvocationConfig(runtime, config, running)
                running[runtime]?.set(config, newGet)
                return@synchronized newGet
            } else {
                return@synchronized getter
            }
        }

        return getter.requestInvocation(timeout_s)
    }
}

/**
 * Request: From a Runtime to a runtime/config combo.
 */
class Request(val timeout_s: Long = 5) {
    val logger = KotlinLogging.logger {}
    val latch = CountDownLatch(1)
    var inv: ConsumeInvocation? = null

    data class MaybeInvocation(val inv: ConsumeInvocation?, val timeout: Boolean)
    fun waitForInvocation(timeout_s: Long = this.timeout_s): MaybeInvocation {
        // Wait timeout for an invocation
        val success = latch.await(timeout_s, TimeUnit.SECONDS)
//        // If it wasnt successful lock the latch so that we can get no invocations in here
        if (!success)
            latch.countDown()
        return MaybeInvocation(inv, !success)
    }

    fun giveInvocation(inv: ConsumeInvocation?): Boolean {
        if (latch.count == 0L) {
            // timeout has already happened...
            logger.warn { "Trying to give invocation to Request that has timed out..." }
            return false
        }
        this.inv = inv
        latch.countDown()
        return true
    }
}

/**
 * Runs a coroutine, used for every single runtime/config combo currently used by this program.
 */
class SingleInvocationConfig(private val runtime: String, private val config: String, private val running: runningMap) {
    private val requests = LinkedBlockingQueue<Request>()
    private val logger = KotlinLogging.logger {}
    init {
        GlobalScope.launch {
            val bc = BedrockClient()
            while (isActive) {
                // Wait up for a request. If there is none shutdown this coroutine.
                val req = requests.poll(2, TimeUnit.MINUTES)
                if(req == null) {
                    val itWasAFluke = synchronized(running) {
                        // Now look if there is an invocation
                        if (requests.size > 0) {
                            return@synchronized true
                        }
                        running[runtime]!!.remove(config)
                        return@synchronized false
                    }
                    if (itWasAFluke) {
                        continue
                    } else {
                        break
                    }
                } else {
                    // request is not null
                    val inv = bc.consumeInvocation(runtime, config, req.timeout_s.toInt())
                    if (inv.status == 200) {
                        val worked = req.giveInvocation(inv)
                        if (!worked) {
                            // We have the invocation but we can't give it to this request anymore
                            logger.error { "Rescheduling invocation $inv..." }
                            val req = requests.poll(2, TimeUnit.MINUTES)
                            if (req == null) {
                                // This is a prototype, just reschedule it.
                                bc.createInvocation(inv.inv)
                            } else {
                                val worked = req.giveInvocation(inv)
                                if (!worked) {
                                    bc.createInvocation(inv.inv)
                                }
                            }
                        }
                    } else {
                        req.giveInvocation(null)
                    }
                }
            }
        }
    }
    suspend fun requestInvocation(timeout_s: Long): ConsumeInvocation? {
        val r = Request(timeout_s)
        requests.add(r)
        val (inv, timeout) = r.waitForInvocation()
        // If we didn't get an invocation but reached the timeout then return null and remove this invocation
        // from the queue.
        if (timeout) {
            val changed = requests.remove(r)
            if (!changed) {
                // A Coroutine is currently working on this request (because it has taken() this request, so it
            //cannot be removed)
                // So lets take this one, even if it takes longer than timeout...
//                val (inv, timeout) = r.waitForInvocation(timeout_s*2)
//                if (timeout) {
//                    logger.error { "There should be an invocation here (or no Invocation) but there was a " +
//                            "timeout again... I waited ${timeout_s*2} seconds and inv is $inv" }
//                }
//                return inv
                logger.error { "This request is currently getting answered by a coroutine. But the timeout has " +
                        "already happened, so now its the coroutines problem to give this invocation to another " +
                        "requester." }
            }
        }
        return inv
    }
}
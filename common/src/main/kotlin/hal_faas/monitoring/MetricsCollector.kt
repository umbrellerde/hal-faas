package hal_faas.monitoring

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.LongTaskTimer
import io.micrometer.influx.InfluxConfig
import io.micrometer.influx.InfluxMeterRegistry
import java.time.Duration

class MetricsCollector {
    companion object {
        private val config = object : InfluxConfig {
            override fun step(): Duration {
                return Duration.ofSeconds(2)
            }
            override fun db(): String {
                return "hal-faas"
            }
            override fun password(): String? {
                return "admin"
            }
            override fun userName(): String? {
                return "admin"
            }
            override fun get(p0: String): String? {
                return null
            }
        }

        val registry = InfluxMeterRegistry(config, Clock.SYSTEM)

        init {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    // Just so that everything else can finish first
                    Thread.sleep(1000)
                    registry.close()
                }
            )
        }
    }
}
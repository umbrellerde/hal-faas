package hal_faas.rabbitmq

import com.rabbitmq.client.*

class RabbitMQHelper {
    companion object {
        private val factory = ConnectionFactory()

        init {
            factory.host = "localhost"
        }

        fun createChannel(): Channel {
            return factory.newConnection().createChannel()
        }
    }
}

fun Channel.publish(queueName: String, message: String) {
    this.queueDeclare(queueName, false, false, false, null)
    this.basicPublish("", queueName, null, message.toByteArray())
}

/**
 * returns the consumerTag that is necessary to "unsubscribe"
 */
fun Channel.subscribe(queueName: String, deliver: DeliverCallback, cancel: CancelCallback): String {
    this.queueDeclare(queueName, false, false, false, null)
    return this.basicConsume(queueName, deliver, cancel)
}

fun Channel.unsubscribe(clientTag: String) {
    this.basicCancel(clientTag)
}
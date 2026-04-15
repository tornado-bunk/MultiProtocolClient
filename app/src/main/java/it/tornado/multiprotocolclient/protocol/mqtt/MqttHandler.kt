package it.tornado.multiprotocolclient.protocol.mqtt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID

class MqttHandler {

    fun subscribeMqtt(
        host: String,
        port: Int = 1883,
        topic: String,
        useSsl: Boolean = false,
        username: String = "",
        password: String = "",
        timeoutSeconds: Int = 10
    ): Flow<String> = flow {
        var client: MqttClient? = null
        val receivedMessages = mutableListOf<String>()

        try {
            val protocol = if (useSsl) "ssl://" else "tcp://"
            val brokerUrl = "$protocol$host:$port"
            val clientId = "MultiProtocolClient-${UUID.randomUUID()}"
            
            emit("═══════════════════════════════════════")
            emit("  MQTT Subscribe: $host:$port")
            emit("═══════════════════════════════════════")
            emit("Broker URL: $brokerUrl")
            emit("Client ID: $clientId")
            emit("Connecting...")

            val persistence = MemoryPersistence()
            client = MqttClient(brokerUrl, clientId, persistence)

            val connOpts = MqttConnectOptions()
            connOpts.isCleanSession = true
            connOpts.connectionTimeout = 5
            
            if (username.isNotEmpty()) {
                connOpts.userName = username
                if (password.isNotEmpty()) {
                    connOpts.password = password.toCharArray()
                }
            }

            client.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    receivedMessages.add("⚠️ Connection lost: ${cause?.message}")
                }

                override fun messageArrived(msgTopic: String?, message: MqttMessage?) {
                    if (msgTopic != null && message != null) {
                        receivedMessages.add("[$msgTopic] ${String(message.payload)}")
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // Not used for subscribers
                }
            })

            client.connect(connOpts)
            emit("✅ Connected. Subscribing to topic: '$topic'...")
            
            client.subscribe(topic)
            emit("✅ Subscribed. Waiting for messages for $timeoutSeconds seconds...")
            emit("── Messages ──")

            val startTime = System.currentTimeMillis()
            var lastPrintedIndex = 0

            while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
                if (receivedMessages.size > lastPrintedIndex) {
                    for (i in lastPrintedIndex until receivedMessages.size) {
                        emit(receivedMessages[i])
                    }
                    lastPrintedIndex = receivedMessages.size
                }
                delay(200)
            }

            emit("...")
            emit("(Timeout reached. Automatically disconnecting.)")

        } catch (e: Exception) {
            emit("❌ MQTT Error: ${e.message}")
        } finally {
            try {
                if (client?.isConnected == true) {
                    client.disconnect()
                }
                client?.close()
            } catch (e: Exception) {
                // Ignore
            }
            emit("═══════════════════════════════════════")
        }
    }.flowOn(Dispatchers.IO)
}

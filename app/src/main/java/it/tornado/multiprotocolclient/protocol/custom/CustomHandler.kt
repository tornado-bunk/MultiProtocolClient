// CustomHandler.kt
package it.tornado.multiprotocolclient.protocol.custom

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onEach

class CustomHandler {
    private val tcpHandler = TcpHandler()
    private val udpHandler = UdpHandler()

    // Process a custom request
    fun processCustomRequest(request: CustomRequest): Flow<List<String>> = flow {
        // Emit a message to the UI to inform the user that the connection is being attempted
        emit(listOf("Attempting ${if (request.useTcp) "TCP" else "UDP"} connection to ${request.ip}:${request.port}...\n"))

        try {
            val handler = if (request.useTcp) {
                tcpHandler.handleConnection(request.ip, request.port.toInt())
            } else {
                udpHandler.handleConnection(request.ip, request.port.toInt())
            }

            handler.collect { message ->
                emit(message)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Custom Protocol Error", e)
            emit(listOf("Error: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
        .onEach { message -> Log.d(TAG, "Message: $message") }
        .catch { e ->
            Log.e(TAG, "Flow error", e)
            emit(listOf("Error: ${e.message}"))
        }

    companion object {
        private const val TAG = "CustomHandler"
    }
}

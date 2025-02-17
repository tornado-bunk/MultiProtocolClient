package it.tornado.multiprotocolclient.protocol.custom

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

class TcpHandler {
    // Handle TCP connection
    fun handleConnection(
        ip: String,
        port: Int
    ): Flow<List<String>> = flow {
        var socket: Socket? = null
        var writer: PrintWriter? = null
        var reader: BufferedReader? = null

        try {
            emit(listOf("Initializing TCP connection..."))

            // Create socket
            socket = Socket()
            socket.soTimeout = SOCKET_TIMEOUT

            socket.connect(InetSocketAddress(ip, port), CONNECTION_TIMEOUT)
            emit(listOf("Connected successfully\n"))

            // Setup streams
            writer = PrintWriter(socket.getOutputStream(), true)
            reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            // Send message
            val testMessage = "TEST from MultiProtocolClient"
            writer.println(testMessage)
            emit(listOf("Sent message: $testMessage"))

            // Read response
            val response = reader.readLine()
            if (response != null) {
                emit(listOf("Received response: $response"))
            } else {
                emit(listOf("No response received"))
            }

        } catch (e: Exception) {
            emit(listOf("Error: ${e.javaClass.simpleName} - ${e.message}"))
        } finally {
            try {
                writer?.close()
                reader?.close()
                socket?.close()
                emit(listOf("\nConnection closed"))
            } catch (e: Exception) {
                emit(listOf("\nError closing connection: ${e.message}"))
            }
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val SOCKET_TIMEOUT = 5000
        private const val CONNECTION_TIMEOUT = 5000
    }
}

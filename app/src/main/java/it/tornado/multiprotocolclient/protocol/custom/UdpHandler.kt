package it.tornado.multiprotocolclient.protocol.custom

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpHandler {
    // Function to handle the UDP connection
    fun handleConnection(ip: String, port: Int): Flow<List<String>> = flow {
        // Create a new DatagramSocket and set the timeout
        val socket = DatagramSocket()
        socket.soTimeout = SOCKET_TIMEOUT

        try {
            // Adding a TEST message to the UDP packet
            val messageNotEncoded = "TEST from MultiProtocolClient"
            val message = messageNotEncoded.toByteArray()
            val address = InetAddress.getByName(ip)
            val sendPacket = DatagramPacket(message, message.size, address, port)

            socket.send(sendPacket)
            emit(listOf("UDP packet sent: $messageNotEncoded\n"))

            val buffer = ByteArray(BUFFER_SIZE)
            val receivePacket = DatagramPacket(buffer, buffer.size)

            try {
                // Receive the response from the server
                socket.receive(receivePacket)
                val response = String(receivePacket.data, 0, receivePacket.length)
                if (response.isNotEmpty()) {
                    emit(listOf("UDP Response received"))
                    emit(listOf("UDP Response content: $response"))
                } else {
                    emit(listOf("UDP Response was empty"))
                }
            } catch (e: Exception) {
                emit(listOf("No UDP response received (timeout)"))
            }
        } catch (e: Exception) {
            emit(listOf("UDP Communication failed: ${e.message}"))
        } finally {
            socket.close()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val SOCKET_TIMEOUT = 5000
        private const val BUFFER_SIZE = 1024
    }
}

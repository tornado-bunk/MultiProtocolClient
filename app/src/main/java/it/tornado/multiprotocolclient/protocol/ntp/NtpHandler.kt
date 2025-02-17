package it.tornado.multiprotocolclient.protocol.ntp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class NtpHandler {
    // Flow that sends an NTP request and processes the response
    fun processNtpRequest(request: NtpRequest) = flow {
        try {
            // NTP request buffer
            val buffer = ByteArray(48)
            buffer[0] = (0x1B).toByte()

            // Create a DatagramSocket and send the request
            val socket = DatagramSocket()
            val address = InetAddress.getByName(request.ip)
            val packet = DatagramPacket(buffer, buffer.size, address, NTP_PORT)

            emit(listOf("Sending NTP request to ${request.ip}...\n"))

            // Receive the response and process it
            socket.use { s ->
                // Set the socket timeout
                s.soTimeout = SOCKET_TIMEOUT
                // Send the request
                s.send(packet)

                // Receive the response
                val response = DatagramPacket(buffer, buffer.size)
                s.receive(response)

                // Extract the timestamp and fraction from the response
                val seconds = extractTimestamp(response.data)
                val fraction = extractFraction(response.data)
                val ntpTime = calculateNtpTime(seconds, fraction)

                val dateTime = convertToLocalDateTime(ntpTime, request.timezone)

                // Emit the formatted response
                emitFormattedResponse(dateTime)
            }
        } catch (e: Exception) {
            emit(listOf("Error: ${e.message}"))
            Log.e(TAG, "NTP Request Error", e)
        }
    }.flowOn(Dispatchers.IO)

    // Calculate the NTP time from the seconds and fraction
    private fun calculateNtpTime(seconds: Long, fraction: Long): Long {
        return (seconds * 1000L) + ((fraction * 1000L) / 0x100000000L)
    }

    // Convert the NTP time to a LocalDateTime object
    private fun convertToLocalDateTime(ntpTime: Long, timezone: String): LocalDateTime {
        val instant = Instant.ofEpochMilli(ntpTime)
        return LocalDateTime.ofInstant(instant, ZoneId.of(timezone))
    }

    // Emit the formatted response
    private suspend fun kotlinx.coroutines.flow.FlowCollector<List<String>>.emitFormattedResponse(
        dateTime: LocalDateTime
    ) {
        emit(listOf("NTP Response received:"))
        emit(listOf("Server time: $dateTime\n"))
        emit(listOf("Formatted response:"))
        emit(listOf(
            "Time: ${dateTime.toLocalTime()}",
            "Date: ${dateTime.toLocalDate()}"
        ))
    }

    // Extract the timestamp from the response
    private fun extractTimestamp(data: ByteArray): Long {
        var seconds: Long = 0
        for (i in 40..43) {
            seconds = seconds shl 8
            seconds = seconds or (data[i].toInt() and 0xFF).toLong()
        }
        return seconds - SECONDS_FROM_1900_TO_1970
    }

    // Extract the fraction from the response
    private fun extractFraction(data: ByteArray): Long {
        var fraction: Long = 0
        for (i in 44..47) {
            fraction = fraction shl 8
            fraction = fraction or (data[i].toInt() and 0xFF).toLong()
        }
        return fraction
    }

    // Companion object with constants
    companion object {
        private const val TAG = "NtpHandler"
        private const val NTP_PORT = 123
        private const val SOCKET_TIMEOUT = 5000
        private const val SECONDS_FROM_1900_TO_1970 = 2208988800L
    }
}
package it.tornado.multiprotocolclient.viewmodel

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier


class ClientViewModel : ViewModel() {
    private val _response = MutableStateFlow<List<String>>(emptyList())
    val response: StateFlow<List<String>> = _response.asStateFlow()

    //NTP Section
    fun sendNtpRequest(ip: String, timezone: String) {
        viewModelScope.launch {
            ntpResponse(ip, timezone).collect { chunk ->
                _response.value += chunk
            }
        }
    }

    //Function to send an NTP request to the specified server
    private fun ntpResponse(ipAddress: String, timezone: String) = flow {
        try {
            // Create the buffer for the NTP request
            val buffer = ByteArray(48)
            buffer[0] = (0x1B).toByte()

            // Create the socket and packet
            val socket = DatagramSocket()
            // Convert the IP address to InetAddress
            val address = InetAddress.getByName(ipAddress)

            // Create the packet
            val packet = DatagramPacket(buffer, buffer.size, address, 123)

            emit(listOf("Sending NTP request to $ipAddress...\n"))

            // Send the packet
            socket.soTimeout = 5000
            socket.send(packet)

            // Receive the response
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)

            // Extract the timestamp and fraction from the response
            val seconds = extractTimestamp(response.data)
            val fraction = extractFraction(response.data)

            // Convert the timestamp to milliseconds used in UNIX time
            val ntpTime = (seconds * 1000L) + ((fraction * 1000L) / 0x100000000L)

            // Create a LocalDateTime object from the NTP time
            val instant = Instant.ofEpochMilli(ntpTime)
            val dateTime = LocalDateTime.ofInstant(instant, ZoneId.of(timezone))

            emit(listOf("NTP Response received:"))
            emit(listOf("Server time: $dateTime\n"))
            emit(listOf("Formatted response:"))
            emit(listOf(
                "Time: ${dateTime.toLocalTime()}",
                "Date: ${dateTime.toLocalDate()}"
            ))

            socket.close()
        } catch (e: Exception) {
            emit(listOf("Error: ${e.message}"))
            Log.e("ClientViewModel", "NTP Request Error", e)
        }
    }.flowOn(Dispatchers.IO)

    //Function to extract the timestamp from the NTP response
    private fun extractTimestamp(data: ByteArray): Long {
        var seconds: Long = 0
        //The timestamp is in bytes 40-43
        for (i in 40..43) {
            seconds = seconds shl 8
            seconds = seconds or (data[i].toInt() and 0xFF).toLong()
        }
        // Subtract the number of seconds between 1900 and 1970
        return seconds - 2208988800L
    }

    //Function to extract the fraction from the NTP response
    private fun extractFraction(data: ByteArray): Long {
        var fraction: Long = 0
        //The fraction is in bytes 44-47
        for (i in 44..47) {
            fraction = fraction shl 8
            fraction = fraction or (data[i].toInt() and 0xFF).toLong()
        }
        return fraction
    }

    //HTTP Section
    fun sendHttpRequest(protocol: String, ip: String, port: String, useSSL: Boolean, seeOnlyStatusCode: Boolean, trustSelfSigned: Boolean = false) {
        viewModelScope.launch {
            if (protocol == "HTTP") {
                val urlString = if (useSSL) "https://$ip:$port" else "http://$ip:$port"
                httpResponse(urlString, seeOnlyStatusCode, trustSelfSigned).collect { chunk ->
                    _response.value += chunk
                }
            }
        }
    }

    //Function to send an HTTP request to the specified server
    private fun httpResponse(urlString: String, seeOnlyStatusCode: Boolean, trustSelfSigned: Boolean) = flow {
        try {
            // Create the URL object
            val url = URL(urlString)
            // Open the connection
            var connection = url.openConnection() as HttpURLConnection

            // If the connection is HTTPS and trustSelfSigned is false, verify the SSL certificate
            if (connection is HttpsURLConnection && !trustSelfSigned) {
                emit(listOf("Verifying SSL certificate...\n"))
                try {
                    connection.connect()
                } catch (e: Exception) {
                    emit(listOf("SSL Certificate Error: ${e.message}"))
                    return@flow
                }
                // If the certificate is valid, disconnect and reconnect to read the response
            } else if (connection is HttpsURLConnection) {
                // Create a trusting connection
                val trustingConnection = createTrustingConnection(url)
                connection.disconnect()
                connection = trustingConnection
            }

            // Set the GET method and timeouts
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            // Receive the response code
            val responseCode = connection.responseCode
            emit(listOf("HTTP Response Code: $responseCode"))

            // If the checkbox is not checked, read the response
            if (!seeOnlyStatusCode) {
                // If the response code is OK, read the response
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    var line: String? = reader.readLine()
                    while (line != null) {
                        emit(listOf(line))
                        line = reader.readLine()
                    }
                    reader.close()
                    inputStream.close()
                } else {
                    emit(listOf("HTTP Error: $responseCode"))
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            emit(listOf("Error: ${e.message}"))
            Log.e("ClientViewModel", "HTTP Request Error", e)
        }
    }.flowOn(Dispatchers.IO)

    //Function to create a trusting connection
    private fun createTrustingConnection(url: URL): HttpURLConnection {
        // Create a TrustManager that accepts all certificates
        val trustAllCerts = arrayOf<TrustManager>(@SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            //Return an empty array of accepted certificates
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            //Do not check the client certificates
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            //Do not check the server certificates
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        })

        // Create a SSLContext that uses the TrustManager
        val sc = SSLContext.getInstance("SSL")
        sc.init(
            null,
            trustAllCerts,          // Pass the TrustManager that accepts all certificates
            java.security.SecureRandom()  // Generate a SecureRandom
        )
        // Set the SSLContext as the default SSLSocketFactory
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)

        // Create a HostnameVerifier that accepts all hosts
        val allHostsValid = HostnameVerifier { _, _ -> true }
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)

        // Return the connection
        return url.openConnection() as HttpURLConnection
    }

    //CUSTOM Section
    fun sendCustomRequest(ip: String, port: String, useTcp: Boolean) {
        viewModelScope.launch {
            customResponse(ip, port, useTcp).collect { chunk ->
                _response.value += chunk
            }
        }
    }

    //Function to send a custom protocol request to the specified server
    private fun customResponse(ipAddress: String, port: String, useTcp: Boolean) = flow {
        try {
            emit(listOf("Attempting ${if (useTcp) "TCP" else "UDP"} connection to $ipAddress:$port...\n"))

            // Case for TCP connection
            if (useTcp) {
                // Create the TCP socket
                val socket = Socket()
                socket.soTimeout = 5000
                try {
                    // Connect to the server
                    socket.connect(InetSocketAddress(ipAddress, port.toInt()), 5000)
                    emit(listOf("TCP Connection established"))

                    // Send a TEST message (used for testing)
                    val outputStream = socket.getOutputStream()
                    val message = "TEST\n".toByteArray()
                    outputStream.write(message)
                    outputStream.flush()
                    emit(listOf("TCP message sent: TEST\n"))

                    // Get the response
                    val inputStream = socket.getInputStream()
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    var line: String? = reader.readLine()
                    while (line != null) {
                        emit(listOf("TCP Response: $line"))
                        line = reader.readLine()
                    }

                    // Close the streams and socket
                    reader.close()
                    inputStream.close()
                    outputStream.close()
                    socket.close()

                } catch (e: Exception) {
                    emit(listOf("TCP Connection failed: ${e.message}"))
                }
                // Case for UDP connection
            } else {
                // Create the UDP socket
                val socket = DatagramSocket()
                socket.soTimeout = 5000
                try {
                    // Send a TEST message (used for testing)
                    val message = "TEST".toByteArray()
                    val address = InetAddress.getByName(ipAddress)

                    val sendPacket = DatagramPacket(message, message.size, address, port.toInt())
                    socket.send(sendPacket)
                    emit(listOf("UDP packet sent: TEST\n"))

                    // Get the response
                    val buffer = ByteArray(1024)
                    val receivePacket = DatagramPacket(buffer, buffer.size)

                    try {
                        // Receive the response
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

                    socket.close()
                } catch (e: Exception) {
                    emit(listOf("UDP Communication failed: ${e.message}"))
                }
            }
        } catch (e: Exception) {
            emit(listOf("Error: ${e.message}"))
            Log.e("ClientViewModel", "Custom Protocol Error", e)
        }
    }.flowOn(Dispatchers.IO)


    fun resetResponse() {
        _response.value = emptyList()
    }
}
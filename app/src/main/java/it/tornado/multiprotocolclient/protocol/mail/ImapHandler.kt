package it.tornado.multiprotocolclient.protocol.mail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

class ImapHandler {

    // Flow to handle IMAP connection
    fun testImap(host: String, port: Int, useSsl: Boolean) = flow {
        var socket: Socket? = null
        var writer: PrintWriter? = null
        var reader: BufferedReader? = null

        try {
            emit(listOf("Connecting to IMAP server at $host:$port (SSL: $useSsl)..."))

            // Create socket and connect
            if (useSsl) {
                val factory = SSLSocketFactory.getDefault()
                socket = factory.createSocket(host, port)
            } else {
                socket = Socket()
                socket.connect(InetSocketAddress(host, port), 5000)
            }
            
            socket?.soTimeout = 5000
            emit(listOf("Connected.\n"))

            // Create reader and writer
            reader = BufferedReader(InputStreamReader(socket?.getInputStream()))
            writer = PrintWriter(socket?.getOutputStream(), true)

            // 1. Read Banner (* OK ...)
            val banner = reader.readLine()
            emit(listOf("S: $banner"))

            // 2. Send CAPABILITY
            val tag = "A001"
            val capaCmd = "$tag CAPABILITY"
            emit(listOf("C: $capaCmd"))
            writer.println(capaCmd)

            // 3. Read Response
            // Response typically includes * CAPABILITY ...
            // And ends with A001 OK ...
            var response: String?
            while (reader.readLine().also { response = it } != null) {
                emit(listOf("S: $response"))
                if (response?.startsWith(tag) == true) break
            }

            // 4. LOGOUT
            val logoutTag = "A002"
            val logoutCmd = "$logoutTag LOGOUT"
            emit(listOf("C: $logoutCmd"))
            writer.println(logoutCmd)
            
             // Read until Bye or tagged OK
             while (reader.readLine().also { response = it } != null) {
                emit(listOf("S: $response"))
                if (response?.startsWith(logoutTag) == true) break
                // Server might send * BYE before tag
            }

        } catch (e: Exception) {
            emit(listOf("Error: ${e.message}"))
        } finally {
             try {
                writer?.close()
                reader?.close()
                socket?.close()
                emit(listOf("\nConnection closed."))
            } catch (e: Exception) {
                // ignore
            }
        }
    }.flowOn(Dispatchers.IO)
}

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

class Pop3Handler {

    // Flow to handle POP3 connection
    fun testPop3(host: String, port: Int, useSsl: Boolean) = flow {
        var socket: Socket? = null
        var writer: PrintWriter? = null
        var reader: BufferedReader? = null

        try {
            emit(listOf("Connecting to POP3 server at $host:$port (SSL: $useSsl)..."))

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

            reader = BufferedReader(InputStreamReader(socket?.getInputStream()))
            writer = PrintWriter(socket?.getOutputStream(), true)

            // 1. Read Banner (+OK ...)
            val banner = reader.readLine()
            emit(listOf("S: $banner"))

            // 2. Send CAPA (Capabilities)
            val capaCmd = "CAPA"
            emit(listOf("C: $capaCmd"))
            writer.println(capaCmd)

            // 3. Read Response
            // Response to CAPA is multi-line, ended by single DOT "."
            var response: String?
            while (reader.readLine().also { response = it } != null) {
                emit(listOf("S: $response"))
                if (response == ".") break
                // Also stop if error
                if (response?.startsWith("-ERR") == true) break
            }

            // 4. QUIT
            val quitCmd = "QUIT"
            emit(listOf("C: $quitCmd"))
            writer.println(quitCmd)
            
            response = reader.readLine()
            if (response != null) {
                emit(listOf("S: $response"))
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

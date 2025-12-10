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

class SmtpHandler {

    // Flow to handle SMTP connection
    fun testSmtp(host: String, port: Int, useSsl: Boolean, useStartTls: Boolean) = flow {
        var socket: Socket? = null
        var writer: PrintWriter? = null
        var reader: BufferedReader? = null

        try {
            emit(listOf("Connecting to SMTP server at $host:$port (SSL: $useSsl, STARTTLS: $useStartTls)..."))

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

            // 1. Read Banner
            var banner = reader.readLine()
            emit(listOf("S: $banner"))
            
            // Handle multi-line banner
            while (banner != null && (banner.startsWith("220-") || !banner.startsWith("220"))) {
                 if (banner.startsWith("220 ")) break 
                 if(reader.ready()) {
                    banner = reader.readLine()
                    emit(listOf("S: $banner"))
                 } else {
                     break
                 }
            }

            // 2. Send EHLO
            val heloCmd = "EHLO localhost"
            emit(listOf("C: $heloCmd"))
            writer.println(heloCmd)

            // 3. Read Capabilities
            readMultiLineResponse(reader) { line -> emit(listOf("S: $line")) }

            // 4. STARTTLS if requested
            if (!useSsl && useStartTls) {
                emit(listOf("C: STARTTLS"))
                writer.println("STARTTLS")
                
                val startTlsResponse = reader.readLine()
                emit(listOf("S: $startTlsResponse"))
                
                if (startTlsResponse != null && startTlsResponse.startsWith("220")) {
                     emit(listOf("Upgrading connection to SSL/TLS..."))
                     val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                     val sslSocket = factory.createSocket(socket, host, port, true)
                     
                     // Re-init streams on new socket
                     socket = sslSocket
                     reader = BufferedReader(InputStreamReader(socket?.getInputStream()))
                     writer = PrintWriter(socket?.getOutputStream(), true)
                     
                     // Send EHLO again after upgrade
                     emit(listOf("C: $heloCmd (Secure)"))
                     writer.println(heloCmd)
                     readMultiLineResponse(reader) { line -> emit(listOf("S: $line")) }
                } else {
                    emit(listOf("Server did not accept STARTTLS. Aborting upgrade."))
                }
            }

            // 5. QUIT
            val quitCmd = "QUIT"
            emit(listOf("C: $quitCmd"))
            writer.println(quitCmd)
            
            val response = reader.readLine()
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

    // Helper function must be suspend to allow calling emit (via lambda)
    private suspend fun readMultiLineResponse(reader: BufferedReader?, onLine: suspend (String) -> Unit) {
         if (reader == null) return
         var response: String?
            while (reader.readLine().also { response = it } != null) {
                onLine(response ?: "")
                // SMTP multiline responses have a hyphen: "250-SIZE"
                // The last line has a space: "250 OK"
                if (response?.length != null && response!!.length >= 4) {
                    val separator = response!![3]
                    if (separator == ' ') break
                } else {
                     break
                }
            }
    }
}

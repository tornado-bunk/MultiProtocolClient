package it.tornado.multiprotocolclient.protocol.telnet

import org.apache.commons.net.telnet.TelnetClient
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream

class InteractiveTelnetHandler {
    private var client: TelnetClient? = null
    private var readerJob: Job? = null

    suspend fun connect(host: String, port: Int, onLog: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                client = TelnetClient().apply {
                    connectTimeout = 5000
                    connect(host, port)
                }
                onLog("Connected to Telnet server at $host:$port")
                val input = client?.inputStream ?: return@withContext
                readerJob = launch(Dispatchers.IO) {
                    val buffer = ByteArray(1024)
                    try {
                        while (isActive) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            onLog(String(buffer, 0, bytesRead))
                        }
                    } catch (e: Exception) {
                        onLog("Error reading from server: ${e.message}")
                    }
                    onLog("Connection closed by server.")
                    disconnect()
                }
            } catch (e: Exception) {
                onLog("Telnet connection failed: ${e.message}")
                disconnect()
            }
        }
    }

    fun sendCommand(command: String, onLog: (String) -> Unit) {
        val out = client?.outputStream
        if (out == null) {
            onLog("Cannot send: not connected.")
            return
        }
        try {
            out.write((command + "\r\n").toByteArray())
            out.flush()
            onLog("> $command")
        } catch (e: Exception) {
            onLog("Failed to send command: ${e.message}")
        }
    }

    fun disconnect() {
        readerJob?.cancel()
        try {
            client?.disconnect()
        } catch (e: Exception) {
            // ignore
        }
        client = null
    }
}

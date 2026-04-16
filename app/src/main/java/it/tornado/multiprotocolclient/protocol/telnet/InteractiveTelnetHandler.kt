package it.tornado.multiprotocolclient.protocol.telnet

import org.apache.commons.net.telnet.TelnetClient
import kotlinx.coroutines.*

class InteractiveTelnetHandler {
    private var client: TelnetClient? = null
    private var readerJob: Job? = null
    private val handlerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Legacy string-based API used by the inline preview. */
    suspend fun connect(host: String, port: Int, onLog: (String) -> Unit) {
        connect(
            host = host,
            port = port,
            onBytes = { buf, len -> onLog(String(buf, 0, len)) },
            onStatus = { onLog(it) }
        )
    }

    /**
     * Byte-oriented connect: [onBytes] receives raw data from the telnet socket (no
     * reformatting) while [onStatus] receives human readable status messages.
     */
    suspend fun connect(
        host: String,
        port: Int,
        onBytes: (ByteArray, Int) -> Unit,
        onStatus: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                client = TelnetClient().apply {
                    connectTimeout = 5000
                    connect(host, port)
                }
                onStatus("Connected to Telnet server at $host:$port")
                val input = client?.inputStream ?: return@withContext
                readerJob = launch(Dispatchers.IO) {
                    val buffer = ByteArray(4096)
                    try {
                        while (isActive) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            if (bytesRead > 0) onBytes(buffer, bytesRead)
                        }
                    } catch (e: Exception) {
                        onStatus("Error reading from server: ${e.message}")
                    }
                    onStatus("Connection closed by server.")
                    disconnect()
                }
            } catch (e: Exception) {
                onStatus("Telnet connection failed: ${e.message}")
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

    /** Write arbitrary bytes typed from a terminal view directly to the telnet socket. */
    fun writeBytes(data: ByteArray, offset: Int = 0, length: Int = data.size): Boolean {
        val out = client?.outputStream ?: return false
        val copy = data.copyOfRange(offset, offset + length)
        handlerScope.launch {
            try {
                out.write(copy)
                out.flush()
            } catch (_: Exception) {
            }
        }
        return true
    }

    fun isConnected(): Boolean = client?.isConnected == true

    fun disconnect() {
        handlerScope.coroutineContext.cancelChildren()
        readerJob?.cancel()
        try {
            client?.disconnect()
        } catch (_: Exception) {
            // ignore
        }
        client = null
    }
}

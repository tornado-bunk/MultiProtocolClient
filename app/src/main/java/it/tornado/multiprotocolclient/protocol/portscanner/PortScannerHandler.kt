package it.tornado.multiprotocolclient.protocol.portscanner

import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class PortScannerHandler {

    fun scanTcpPorts(
        host: String,
        startPort: Int = 1,
        endPort: Int = 1024,
        connectTimeoutMs: Int = 250
    ): Flow<String> = flow {
        emit("TCP Port Scanner")
        emit("Target: $host")
        emit("Range: $startPort-$endPort")
        emit("Timeout: ${connectTimeoutMs}ms")
        emit("")

        val openPorts = mutableListOf<Int>()
        val totalPorts = (endPort - startPort + 1).coerceAtLeast(0)
        var scanned = 0

        for (port in startPort..endPort) {
            scanned++
            if (scanned == 1 || scanned % 100 == 0 || scanned == totalPorts) {
                emit("Progress: $scanned/$totalPorts")
            }

            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                    openPorts.add(port)
                    emit("OPEN $port")
                }
            } catch (_: Exception) {
                // Closed, filtered or timeout.
            }
        }

        emit("")
        emit("── Scan Results ──")
        if (openPorts.isEmpty()) {
            emit("No open TCP ports found in range $startPort-$endPort.")
        } else {
            emit("Open ports: ${openPorts.joinToString(", ")}")
            emit("Found ${openPorts.size} open port(s).")
        }
    }.flowOn(Dispatchers.IO)
}

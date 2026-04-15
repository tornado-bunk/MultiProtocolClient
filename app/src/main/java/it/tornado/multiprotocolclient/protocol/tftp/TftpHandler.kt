package it.tornado.multiprotocolclient.protocol.tftp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.apache.commons.net.tftp.TFTPClient
import java.io.ByteArrayOutputStream

class TftpHandler {

    fun downloadFile(host: String, port: Int = 69, filename: String): Flow<String> = flow {
        val tftp = TFTPClient()
        try {
            emit("═══════════════════════════════════════")
            emit("  TFTP Download: $host:$port")
            emit("═══════════════════════════════════════")
            emit("Requesting file: '$filename'...")

            tftp.defaultTimeout = 5000
            tftp.open()
            
            val outputStream = ByteArrayOutputStream()
            val bytesReceived = tftp.receiveFile(filename, org.apache.commons.net.tftp.TFTP.OCTET_MODE, outputStream, host, port)
            
            if (bytesReceived > 0) {
                emit("✅ TFTP Download successful!")
                emit("Received ${bytesReceived} bytes for '$filename'.")
                
                // If it's a text config, let's try to preview the first lines
                val content = String(outputStream.toByteArray())
                val previewLines = content.lines().take(20)
                emit("")
                emit("── File Preview ──")
                previewLines.forEach { emit(it) }
                if (content.lines().size > 20) emit("... (truncated)")
            } else {
                emit("❌ Received 0 bytes. File might be empty.")
            }
            
            emit("═══════════════════════════════════════")
            
        } catch (e: Exception) {
            emit("❌ TFTP Error: ${e.message}")
            if (e.message?.contains("File not found") == true) {
                emit("(Note: TFTP does not support directory listing. You must provide an exact filename).")
            }
        } finally {
            if (tftp.isOpen) {
                try { tftp.close() } catch (e: Exception) {}
            }
        }
    }.flowOn(Dispatchers.IO)
}

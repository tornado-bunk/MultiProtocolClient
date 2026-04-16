package it.tornado.multiprotocolclient.protocol.ssh

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import kotlinx.coroutines.*
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

class InteractiveSshHandler {
    private var client: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var readerJob: Job? = null
    private val handlerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Legacy string-based API used by the inline preview. Keeps backwards compatibility
     * for callers that only want a textual transcript.
     */
    suspend fun connect(host: String, port: Int, user: String, pass: String, onLog: (String) -> Unit) {
        connect(
            host = host,
            port = port,
            user = user,
            pass = pass,
            onBytes = { buf, len -> onLog(String(buf, 0, len)) },
            onStatus = { onLog(it) }
        )
    }

    /**
     * Byte-oriented connect: [onBytes] receives raw data exactly as it arrives from the
     * remote shell (suitable for a terminal emulator), while [onStatus] receives human
     * readable status messages (connection established / closed / errors).
     */
    suspend fun connect(
        host: String,
        port: Int,
        user: String,
        pass: String,
        onBytes: (ByteArray, Int) -> Unit,
        onStatus: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                Security.removeProvider("BC")
                Security.insertProviderAt(BouncyCastleProvider(), 1)

                client = SSHClient().apply {
                    addHostKeyVerifier(PromiscuousVerifier())
                    connect(host, port)

                    try {
                        authPassword(user, pass)
                    } catch (e: Exception) {
                        try {
                            val finder = object : PasswordFinder {
                                override fun reqPassword(resource: Resource<*>?): CharArray = pass.toCharArray()
                                override fun shouldRetry(resource: Resource<*>?): Boolean = false
                            }
                            auth(user, AuthKeyboardInteractive(PasswordResponseProvider(finder)))
                        } catch (e2: Exception) {
                            // If both fail, let it bubble up
                            throw Exception("Authentication failed (tried password and keyboard-interactive).", e2)
                        }
                    }
                }
                session = client?.startSession()
                session?.allocateDefaultPTY()
                shell = session?.startShell()

                onStatus("Connected via SSH to $host")

                val input = shell?.inputStream ?: return@withContext
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
                    onStatus("Connection closed.")
                    disconnect()
                }
            } catch (e: Exception) {
                onStatus("SSH connection failed: ${e.message}")
                disconnect()
            }
        }
    }

    fun sendCommand(command: String, onLog: (String) -> Unit) {
        val out = shell?.outputStream
        if (out == null) {
            onLog("Cannot send: not connected.")
            return
        }
        try {
            out.write((command + "\n").toByteArray())
            out.flush()
        } catch (e: Exception) {
            onLog("Failed to send command: ${e.message}")
        }
    }

    /** Write arbitrary bytes (typed from a terminal view) directly to the shell. */
    fun writeBytes(data: ByteArray, offset: Int = 0, length: Int = data.size): Boolean {
        val out = shell?.outputStream ?: return false
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

    fun isConnected(): Boolean = shell != null

    fun disconnect() {
        handlerScope.coroutineContext.cancelChildren()
        readerJob?.cancel()
        try { shell?.close() } catch (_: Exception) {}
        try { session?.close() } catch (_: Exception) {}
        try { client?.disconnect() } catch (_: Exception) {}
        shell = null
        session = null
        client = null
    }
}

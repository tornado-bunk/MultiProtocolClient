package it.tornado.multiprotocolclient.protocol.ssh

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream

import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

class InteractiveSshHandler {
    private var client: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var readerJob: Job? = null

    suspend fun connect(host: String, port: Int, user: String, pass: String, onLog: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                // Remove stripped-down Android BC and add full BC for ed25519/x25519 support
                Security.removeProvider("BC")
                Security.insertProviderAt(BouncyCastleProvider(), 1)
                
                client = SSHClient().apply {
                    addHostKeyVerifier(PromiscuousVerifier())
                    connect(host, port)
                    
                    try {
                        authPassword(user, pass)
                    } catch (e: Exception) {
                        // Fallback to keyboard-interactive
                        val finder = object : PasswordFinder {
                            override fun reqPassword(resource: Resource<*>?): CharArray = pass.toCharArray()
                            override fun shouldRetry(resource: Resource<*>?): Boolean = false
                        }
                        auth(user, AuthKeyboardInteractive(PasswordResponseProvider(finder)))
                    }
                }
                session = client?.startSession()
                session?.allocateDefaultPTY()
                shell = session?.startShell()

                onLog("Connected via SSH to $host")

                val input = shell?.inputStream ?: return@withContext
                readerJob = launch(Dispatchers.IO) {
                    val buffer = ByteArray(4096)
                    try {
                        while (isActive) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            onLog(String(buffer, 0, bytesRead))
                        }
                    } catch (e: Exception) {
                        onLog("Error reading from server: ${e.message}")
                    }
                    onLog("Connection closed.")
                    disconnect()
                }
            } catch (e: Exception) {
                onLog("SSH connection failed: ${e.message}")
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

    fun disconnect() {
        readerJob?.cancel()
        try { shell?.close() } catch (e: Exception) {}
        try { session?.close() } catch (e: Exception) {}
        try { client?.disconnect() } catch (e: Exception) {}
        shell = null
        session = null
        client = null
    }
}

package it.tornado.multiprotocolclient.protocol.ftp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply

class FtpHandler {

    fun listFilesFtp(host: String, port: Int = 21, user: String = "anonymous", pass: String = ""): Flow<String> = flow {
        val ftp = FTPClient()
        try {
            emit(" FTP Connection: $host:$port")
            emit("Connecting...")

            ftp.connectTimeout = 10000
            ftp.connect(host, port)
            
            val reply = ftp.replyCode
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect()
                emit("FTP server refused connection. Code: $reply")
                return@flow
            }
            
            emit("Connected. Authenticating as '$user'...")
            val success = ftp.login(user, pass)
            
            if (!success) {
                emit("Authentication failed.")
                ftp.logout()
                return@flow
            }
            emit("Authenticated.")
            
            ftp.enterLocalPassiveMode()
            
            val pwd = ftp.printWorkingDirectory()
            emit("Current directory: $pwd")
            emit("Fetching file list...")
            emit("── Files ──")
            
            val files = ftp.listFiles()
            if (files.isEmpty()) {
                emit("(empty directory)")
            } else {
                for (file in files) {
                    val typeStr = if (file.isDirectory) "DIR " else "FILE"
                    val sizeStr = if (file.isDirectory) "-" else "${file.size} B"
                    emit(String.format("%-4s | %-12s | %s", typeStr, sizeStr, file.name))
                }
            }
            
            ftp.logout()
            emit("\nConnection closed cleanly.")
            
        } catch (e: Exception) {
            emit("FTP Error: ${e.message}")
        } finally {
            if (ftp.isConnected) {
                try { ftp.disconnect() } catch (e: Exception) {}
            }
        }
    }.flowOn(Dispatchers.IO)

    fun listFilesSftp(host: String, port: Int = 22, user: String, pass: String): Flow<String> = flow {
        var client: SSHClient? = null
        var sftp: SFTPClient? = null
        try {
            emit("SFTP Connection: $host:$port")
            emit("Connecting...")

            client = SSHClient()
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connectTimeout = 10000
            client.connect(host, port)
            
            emit("Connected. Authenticating as '$user'...")
            
            try {
                client.authPassword(user, pass)
                emit("Authenticated via password.")
            } catch (e: Exception) {
                emit("Password auth failed, trying interactive...")
                client.authInteractive(user) { _, _, _, prompt, _ ->
                    prompt.map { pass }
                }
                emit("Authenticated via keyboard-interactive.")
            }
            
            emit("Starting SFTP subsystem...")
            sftp = client.newSFTPClient()
            
            val ls = sftp.ls(".")
            emit("Fetching file list for current directory...")
            emit("── Files ──")
            
            if (ls.isEmpty()) {
                emit("(empty directory)")
            } else {
                for (file in ls) {
                    val attrs = file.attributes
                    val typeStr = if (attrs.type == net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY) "DIR " else "FILE"
                    val sizeStr = if (attrs.type == net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY) "-" else "${attrs.size} B"
                    
                    emit(String.format("%-4s | %-12s | %s", typeStr, sizeStr, file.name))
                }
            }
            
            emit("\nConnection closed cleanly.")
            
        } catch (e: Exception) {
            emit("SFTP Error: ${e.message}")
        } finally {
            try { sftp?.close() } catch (e: Exception) {}
            try { client?.disconnect() } catch (e: Exception) {}
        }
    }.flowOn(Dispatchers.IO)
}

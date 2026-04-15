package it.tornado.multiprotocolclient.protocol.iperf

import android.content.Context
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class Iperf2Handler(context: Context) {
    private val resolver = IperfBinaryResolver(context.applicationContext)
    private val bundledVersion = "2.2.1"

    fun runIperf2Client(
        host: String,
        port: Int = 5001,
        durationSeconds: Int = 10,
        useUdp: Boolean = false
    ): Flow<String> = flow {
        emit("iPerf2 client test")
        emit("Bundled iPerf version: $bundledVersion")
        emit("Target: $host:$port")
        emit("Duration: ${durationSeconds}s")
        emit("Mode: ${if (useUdp) "UDP" else "TCP"}")
        emit("")

        val command = mutableListOf(
            "iperf",
            "-c",
            host,
            "-p",
            port.toString(),
            "-t",
            durationSeconds.toString()
        )
        if (useUdp) {
            command.add("-u")
        }

        val iperf2Binary = resolver.resolve("iperf")
        if (iperf2Binary == null) {
            emit(resolver.diagnostics("iperf"))
            return@flow
        }
        command[0] = iperf2Binary

        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { emit(it) }
                }
            }

            val exitCode = process.waitFor()
            emit("")
            if (exitCode == 0) {
                emit("iPerf2 completed successfully.")
            } else {
                emit("iPerf2 exited with code $exitCode")
            }
        } catch (e: IOException) {
            emit("Failed to run iPerf2: ${e.message}")
            emit(resolver.diagnostics("iperf"))
        } catch (e: Exception) {
            emit("Failed to run iPerf2: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
}

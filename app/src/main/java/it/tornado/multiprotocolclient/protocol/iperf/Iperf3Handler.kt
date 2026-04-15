package it.tornado.multiprotocolclient.protocol.iperf

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class Iperf3Handler {

    fun runIperf3Client(
        host: String,
        port: Int = 5201,
        durationSeconds: Int = 10,
        useUdp: Boolean = false,
        reverse: Boolean = false
    ): Flow<String> = flow {
        emit("iPerf3 client test")
        emit("Target: $host:$port")
        emit("Duration: ${durationSeconds}s")
        emit("Mode: ${if (useUdp) "UDP" else "TCP"}")
        if (reverse) {
            emit("Direction: reverse (-R)")
        }
        emit("")

        val command = mutableListOf(
            "iperf3",
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
        if (reverse) {
            command.add("-R")
        }

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
                emit("iPerf3 completed successfully.")
            } else {
                emit("iPerf3 exited with code $exitCode")
            }
        } catch (e: Exception) {
            emit("Failed to run iPerf3: ${e.message}")
            emit("Make sure the iPerf3 binary is available on the device.")
        }
    }.flowOn(Dispatchers.IO)
}

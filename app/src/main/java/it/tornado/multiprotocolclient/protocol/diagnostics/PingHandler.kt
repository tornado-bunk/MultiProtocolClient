package it.tornado.multiprotocolclient.protocol.diagnostics

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PingHandler {

    // Execute ping command and return output
    suspend fun executePing(host: String): List<String> = withContext(Dispatchers.IO) {
        // Create a mutable list to store the output
        val output = mutableListOf<String>()
        try {
            // Execute ping command
            val command = "ping -c 4 $host"
            // Run the command
            val process = Runtime.getRuntime().exec(command)

            // Read the output
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?

            // Append each line to the output list
            while (reader.readLine().also { line = it } != null) {
                line?.let { output.add(it) }
            }

            // Wait for the process to complete
            val exitCode = process.waitFor()

            // Check if the ping command failed
            if (exitCode != 0) {
                 // Check if we have error output
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                while (errorReader.readLine().also { line = it } != null) {
                    line?.let { output.add("Error: $it") }
                }
                if (output.isEmpty()) {
                     output.add("Ping failed with exit code $exitCode")
                }
            } else if (output.isEmpty()) {
                output.add("No output received from ping command.")
            }

        } catch (e: Exception) {
            output.add("Error executing ping: ${e.message}")
        }
        output
    }
}

package it.tornado.multiprotocolclient.protocol.diagnostics

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TracerouteHandler {

    // Execute traceroute and return the output as a list of strings
    suspend fun executeTraceroute(host: String): List<String> = withContext(Dispatchers.IO) {
        val output = mutableListOf<String>()
        
        // Run traceroute command with ping
        output.addAll(runPingTraceroute(host))

        // If output is empty, add a default message
        if (output.isEmpty()) {
            output.add("Traceroute failed to produce output.")
        }
        
        output
    }

    // Execute traceroute using ping
    private fun runPingTraceroute(host: String): List<String> {
        val output = mutableListOf<String>()
        val maxHops = 30
        
        output.add("Tracing route to $host over a maximum of $maxHops hops:")
        
        for (ttl in 1..maxHops) {
            try {
                // Run ping with the current TTL
                val process = Runtime.getRuntime().exec("ping -c 1 -t $ttl -W 2 $host")
                // Read the output
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                val lines = mutableListOf<String>()
                // Read each line and add it to the output
                while (reader.readLine().also { line = it } != null) {
                    line?.let { lines.add(it) }
                }
                process.waitFor()

                // Parse output to find the responder
                // Usually: "From x.x.x.x: icmp_seq=1 Time to live exceeded"
                // Or successful reply.
                
                var hopInfo = "* * *"
                var stop = false

                for (l in lines) {
                    if (l.contains("Time to live exceeded", ignoreCase = true)) {
                        // Extract IP
                        val parts = l.split(" ")
                        // Rough parsing
                         for (part in parts) {
                             if (part.contains(".") && !part.contains("ping", ignoreCase = true)) {
                                 // Remove colon if present
                                 val ip = part.replace(":", "")
                                 hopInfo = ip
                                 break
                             }
                         }
                    } else if (l.contains("bytes from", ignoreCase = true)) {
                        // Destination reached
                         val parts = l.split(" ")
                         for (part in parts) {
                             if (part.contains(".") && !part.contains("bytes", ignoreCase = true)) {
                                 val ip = part.replace(":", "")
                                 hopInfo = "$ip (Target Reached)"
                                 stop = true
                                 break
                             }
                        }
                    }
                }
                
                output.add("$ttl\t$hopInfo")
                if (stop) break
                
            } catch (e: Exception) {
                 output.add("$ttl\tError: ${e.message}")
            }
        }
        
        return output
    }
}

package it.tornado.multiprotocolclient.protocol.whois

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

class WhoisHandler {

    fun queryWhois(domain: String, server: String = "whois.iana.org", port: Int = 43): Flow<String> = flow {
        try {
            emit("═══════════════════════════════════════")
            emit("  WHOIS Lookup: $domain")
            emit("═══════════════════════════════════════")
            emit("")
            emit("Querying $server:$port ...")
            emit("")

            // First query IANA to find the right WHOIS server
            val ianaResult = performWhoisQuery(domain, server, port)

            // Try to find a more specific whois server from the IANA response
            val referralServer = extractReferralServer(ianaResult)

            if (referralServer != null && referralServer != server) {
                emit("── Referral: $referralServer ──")
                emit("")
                val specificResult = performWhoisQuery(domain, referralServer, 43)
                specificResult.lines().forEach { line ->
                    emit(line)
                }
            } else {
                ianaResult.lines().forEach { line ->
                    emit(line)
                }
            }

        } catch (e: Exception) {
            emit("❌ WHOIS query failed: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    private fun performWhoisQuery(domain: String, server: String, port: Int): String {
        val socket = Socket(server, port)
        socket.soTimeout = 10000

        val writer = OutputStreamWriter(socket.getOutputStream())
        writer.write("$domain\r\n")
        writer.flush()

        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val result = reader.readText()

        reader.close()
        writer.close()
        socket.close()

        return result
    }

    private fun extractReferralServer(response: String): String? {
        // Common patterns for referral WHOIS servers
        val patterns = listOf(
            Regex("refer:\\s*(\\S+)", RegexOption.IGNORE_CASE),
            Regex("whois server:\\s*(\\S+)", RegexOption.IGNORE_CASE),
            Regex("Registrar WHOIS Server:\\s*(\\S+)", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(response)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }
}

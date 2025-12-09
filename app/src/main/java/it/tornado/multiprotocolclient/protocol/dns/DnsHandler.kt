package it.tornado.multiprotocolclient.protocol.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import org.xbill.DNS.Address
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.ReverseMap
import org.xbill.DNS.Section
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.net.URL
import java.time.Duration
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

class DnsHandler {
    // Process a DNS request
    fun processDnsRequest(request: DnsRequest) = flow {
        try {
            emit(listOf("Processing DNS query for ${request.domain}..."))

            when {
                // Case when the user wants to use DNS over HTTPS
                request.useHttps -> {
                    val provider = getDnsProvider(request.selectedResolver)
                    emit(listOf("Using DNS over HTTPS with ${provider.hostname}\n"))
                    try {
                        val type = getDnsType(request.queryType)
                        val name = getDnsName(request.domain, request.queryType)

                        // Create the DNS query
                        val record = Record.newRecord(name, type, DClass.IN)
                        val query = Message.newQuery(record)
                        if (request.useRecursion) {
                            query.header.setFlag(Flags.RD.toInt())
                        }
                        
                        // Convert to wire format
                        val wireFormat = query.toWire()

                        // Creating the URL for the request
                        val url = URL(provider.dohUrl)
                        val connection = url.openConnection() as HttpsURLConnection
                        
                        // Setting the request method and headers for RFC 8484
                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/dns-message")
                        connection.setRequestProperty("Accept", "application/dns-message")
                        
                        // Send binary data
                        connection.outputStream.use { os ->
                            os.write(wireFormat)
                        }

                        // Read binary response
                        val responseBytes = connection.inputStream.readBytes()
                        
                        // Parse response
                        val response = Message(responseBytes)
                        processAndEmitResponse(response)

                    } catch (e: Exception) {
                        emit(listOf("DNS over HTTPS Error: ${e.message}"))
                    }
                }
                
                // Case when the user wants to use DNS over TLS
                request.useTls -> {
                    try {
                        val provider = getDnsProvider(request.selectedResolver)
                        emit(listOf("Using DNS over TLS with ${provider.hostname}\n"))

                        // Create the SSL context and socket
                        val sslContext = SSLContext.getInstance("TLS")
                        sslContext.init(null, null, null)
                        
                        // Use hostname for SNI and certificate validation
                        val socket = sslContext.socketFactory.createSocket(provider.hostname, 853) as SSLSocket
                        
                        val sslParameters = socket.sslParameters
                        sslParameters.endpointIdentificationAlgorithm = "HTTPS"
                        socket.sslParameters = sslParameters
                        socket.soTimeout = 5000

                        emit(listOf("TLS connection established"))

                        val type = getDnsType(request.queryType)
                        val name = getDnsName(request.domain, request.queryType)

                        // Create the DNS query
                        val record = Record.newRecord(name, type, DClass.IN)
                        val query = Message.newQuery(record)
                        if (request.useRecursion) {
                            query.header.setFlag(Flags.RD.toInt())
                        }

                        // Send the query
                        val wireFormat = query.toWire()
                        val outputStream = socket.getOutputStream()
                        outputStream.write(((wireFormat.size shr 8) and 0xFF))
                        outputStream.write((wireFormat.size and 0xFF))
                        outputStream.write(wireFormat)
                        outputStream.flush()

                        // Receive the response
                        val inputStream = socket.getInputStream()
                        val lengthBytes = ByteArray(2)
                        inputStream.read(lengthBytes)
                        val responseLength = ((lengthBytes[0].toInt() and 0xFF) shl 8) or (lengthBytes[1].toInt() and 0xFF)
                        val responseBytes = ByteArray(responseLength)
                        inputStream.read(responseBytes)

                        val response = Message(responseBytes)
                        processAndEmitResponse(response)
                        socket.close()
                    } catch (e: Exception) {
                        emit(listOf("DNS over TLS Error: ${e.message}"))
                    }
                }

                // Normal DNS query
                else -> {
                    try {
                        val provider = getDnsProvider(request.selectedResolver)
                        emit(listOf("Using DNS resolver: ${provider.ip}"))

                        val resolver = SimpleResolver(provider.ip)
                        resolver.timeout = Duration.ofSeconds(5)

                        if (request.useTcp) {
                            resolver.tcp = true
                            emit(listOf("Using TCP for DNS query"))
                        }

                        val type = getDnsType(request.queryType)
                        val name = getDnsName(request.domain, request.queryType)

                        val record = Record.newRecord(name, type, DClass.IN)
                        val query = Message.newQuery(record)

                        if (request.useRecursion) {
                            query.header.setFlag(Flags.RD.toInt())
                            emit(listOf("Recursion requested"))
                        }

                        val response = resolver.send(query)
                        processAndEmitResponse(response)

                    } catch (e: Exception) {
                        emit(listOf("DNS Query Error: ${e.message}"))
                    }
                }
            }
        } catch (e: Exception) {
            emit(listOf("DNS Error: ${e.message ?: "Unknown error"}"))
        }
    }.flowOn(Dispatchers.IO)

    // Helper functions
    private fun getDnsType(queryType: String): Int {
        return when (queryType) {
            "A" -> Type.A
            "MX" -> Type.MX
            "CNAME" -> Type.CNAME
            "NS" -> Type.NS
            "PTR" -> Type.PTR
            "ANY" -> Type.ANY
            else -> Type.A
        }
    }

    private fun getDnsName(domain: String, queryType: String): Name {
        return if (queryType == "PTR") {
            val addr = Address.getByAddress(domain)
            ReverseMap.fromAddress(addr)
        } else {
            Name.fromString(if (domain.endsWith(".")) domain else "$domain.")
        }
    }

    private fun getDnsProvider(resolver: String): DnsConstants.DnsProvider {
        return DnsConstants.RESOLVERS[resolver] ?: DnsConstants.FALLBACK_PROVIDER
    }

    private suspend fun FlowCollector<List<String>>.processAndEmitResponse(response: Message) {
        val answers = response.getSection(Section.ANSWER)
        val authority = response.getSection(Section.AUTHORITY)

        when (response.header.rcode) {
            Rcode.NXDOMAIN -> {
                emit(listOf("\nDomain does not exist (NXDOMAIN)!"))
                if (authority.isNotEmpty()) {
                    emit(listOf("\nAuthority Section (SOA Record):"))
                    authority.forEach { record ->
                        emit(listOf(record.toString() + "\n"))
                    }
                }
                return
            }
            Rcode.SERVFAIL -> {
                emit(listOf("\nServer failed to complete the request\n"))
                return
            }
            Rcode.REFUSED -> {
                emit(listOf("\nQuery refused by server\n"))
                return
            }
        }

        if (answers.isNotEmpty()) {
            emit(listOf("\nAnswer Section:"))
            answers.forEach { record ->
                emit(listOf(record.toString() + "\n"))
            }
        }

        if (authority.isNotEmpty()) {
            val hasSoaRecord = authority.any { it.type == Type.SOA }
            if (hasSoaRecord && answers.isEmpty()) {
                emit(listOf("\nNo records of requested type found"))
            }
            emit(listOf("\nAuthority Section:"))
            authority.forEach { record ->
                emit(listOf(record.toString() + "\n"))
            }
        }

        if (answers.isEmpty() && authority.isEmpty()) {
            emit(listOf("No records found"))
        }
    }
}

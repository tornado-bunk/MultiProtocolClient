package it.tornado.multiprotocolclient.protocol.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
import java.net.InetAddress

class DnsHandler {
    // Process a DNS request
    fun processDnsRequest(request: DnsRequest) = flow {
        try {
            emit(listOf("Processing DNS query for ${request.domain}..."))

            when {
                // DNS over QUIC (DoQ) - RFC 9250
                request.useQuic -> {
                    try {
                        val hostname: String
                        val port: Int

                        if (request.useCustomResolver && request.customResolverHost.isNotBlank()) {
                            hostname = request.customResolverHost.trim()
                            port = request.customResolverPort
                            emit(listOf("Using DNS over QUIC with custom server: $hostname:$port\n"))
                        } else {
                            val provider = getDnsProvider(request.selectedResolver)
                            if (!provider.supportsDoq) {
                                emit(listOf("Error: ${request.selectedResolver} does not support DNS over QUIC"))
                                return@flow
                            }
                            hostname = provider.hostname
                            port = provider.doqPort
                            emit(listOf("Using DNS over QUIC with $hostname:$port\n"))
                        }

                        val type = getDnsType(request.queryType)
                        val name = getDnsName(request.domain, request.queryType)

                        // Create the DNS query
                        val record = Record.newRecord(name, type, DClass.IN)
                        val query = Message.newQuery(record)
                        if (request.useRecursion) {
                            query.header.setFlag(Flags.RD.toInt())
                        }

                        val wireFormat = query.toWire()

                        try {
                            emit(listOf("Establishing QUIC connection to $hostname:$port..."))

                            // Resolve the hostname
                            val serverAddress = InetAddress.getByName(hostname)
                            emit(listOf("Resolved $hostname to ${serverAddress.hostAddress}"))

                            // Using Kwik for the QUIC transport
                            val quicConnection = tech.kwik.core.QuicClientConnection.newBuilder()
                                .uri(java.net.URI("https://$hostname:$port"))
                                .noServerCertificateCheck()
                                .applicationProtocol("doq")
                                .connectTimeout(java.time.Duration.ofSeconds(5))
                                .build()

                            quicConnection.connect()
                            emit(listOf("QUIC connection established"))

                            // Create a new bidirectional QUIC stream for the DNS query
                            val quicStream = quicConnection.createStream(true)

                            // Write the DNS wire format prefixed with 2-byte length (RFC 9250)
                            val lengthPrefix = byteArrayOf(
                                ((wireFormat.size shr 8) and 0xFF).toByte(),
                                (wireFormat.size and 0xFF).toByte()
                            )
                            quicStream.getOutputStream().write(lengthPrefix)
                            quicStream.getOutputStream().write(wireFormat)
                            quicStream.getOutputStream().close() // Signal end of stream

                            // Read the response
                            val responseStream = quicStream.getInputStream()
                            val responseLengthBytes = ByteArray(2)
                            val bytesRead = responseStream.read(responseLengthBytes)
                            if (bytesRead < 2) {
                                emit(listOf("DNS over QUIC Error: Invalid response length"))
                                quicConnection.close()
                                return@flow
                            }
                            val responseLength = ((responseLengthBytes[0].toInt() and 0xFF) shl 8) or
                                    (responseLengthBytes[1].toInt() and 0xFF)
                            val responseBytes = ByteArray(responseLength)
                            var totalRead = 0
                            while (totalRead < responseLength) {
                                val read = responseStream.read(responseBytes, totalRead, responseLength - totalRead)
                                if (read == -1) break
                                totalRead += read
                            }

                            val response = Message(responseBytes)
                            emit(listOf("Response received via QUIC (DoQ)"))
                            processAndEmitResponse(response)

                            quicConnection.close()

                        } catch (e: Exception) {
                            emit(listOf("DNS over QUIC Connection Error: ${e.message}"))
                            emit(listOf("Note: DoQ requires the server to support QUIC on port $port"))
                        }

                    } catch (e: Exception) {
                        emit(listOf("DNS over QUIC Error: ${e.message}"))
                    }
                }

                // Case when the user wants to use DNS over HTTPS
                request.useHttps -> {
                    val dohUrl: String
                    val displayName: String

                    if (request.useCustomResolver && request.customResolverHost.isNotBlank()) {
                        // Custom resolver: build DoH URL from hostname
                        val host = request.customResolverHost.trim()
                        val port = request.customResolverPort
                        dohUrl = if (port == 443) {
                            "https://$host/dns-query"
                        } else {
                            "https://$host:$port/dns-query"
                        }
                        displayName = "$host:$port"
                    } else {
                        val provider = getDnsProvider(request.selectedResolver)
                        dohUrl = provider.dohUrl
                        displayName = provider.hostname
                    }

                    // Check if user wants to force HTTP/3 via Cronet
                    if (request.forceHttp3) {
                        emit(listOf("Using DNS over HTTPS (HTTP/3) with $displayName\n"))
                        try {
                            val type = getDnsType(request.queryType)
                            val name = getDnsName(request.domain, request.queryType)

                            val record = Record.newRecord(name, type, DClass.IN)
                            val query = Message.newQuery(record)
                            if (request.useRecursion) {
                                query.header.setFlag(Flags.RD.toInt())
                            }

                            val wireFormat = query.toWire()

                            emit(listOf("Sending DoH request via HTTP/3 (Cronet/QUIC)..."))
                            emit(listOf("URL: $dohUrl"))

                            val responseBytes = CronetHelper.performDoH3Request(dohUrl, wireFormat)

                            val response = Message(responseBytes)
                            emit(listOf("Response received via HTTP/3"))
                            processAndEmitResponse(response)

                        } catch (e: Exception) {
                            emit(listOf("DNS over HTTPS (HTTP/3) Error: ${e.message}"))
                            emit(listOf("Tip: Ensure the resolver supports HTTP/3 (e.g., Cloudflare, Google)"))
                        }
                    } else {
                        // Standard DoH via HttpsURLConnection (HTTP/2)
                        emit(listOf("Using DNS over HTTPS with $displayName\n"))
                        try {
                            val type = getDnsType(request.queryType)
                            val name = getDnsName(request.domain, request.queryType)

                            val record = Record.newRecord(name, type, DClass.IN)
                            val query = Message.newQuery(record)
                            if (request.useRecursion) {
                                query.header.setFlag(Flags.RD.toInt())
                            }

                            val wireFormat = query.toWire()

                            emit(listOf("URL: $dohUrl"))

                            val url = URL(dohUrl)
                            val connection = url.openConnection() as HttpsURLConnection

                            connection.requestMethod = "POST"
                            connection.doOutput = true
                            connection.setRequestProperty("Content-Type", "application/dns-message")
                            connection.setRequestProperty("Accept", "application/dns-message")

                            connection.outputStream.use { os ->
                                os.write(wireFormat)
                            }

                            val responseBytes = connection.inputStream.readBytes()

                            val response = Message(responseBytes)
                            processAndEmitResponse(response)

                        } catch (e: Exception) {
                            emit(listOf("DNS over HTTPS Error: ${e.message}"))
                        }
                    }
                }

                // Case when the user wants to use DNS over TLS
                request.useTls -> {
                    try {
                        val hostname: String
                        val port: Int

                        if (request.useCustomResolver && request.customResolverHost.isNotBlank()) {
                            hostname = request.customResolverHost.trim()
                            port = request.customResolverPort
                        } else {
                            val provider = getDnsProvider(request.selectedResolver)
                            hostname = provider.hostname
                            port = 853
                        }

                        emit(listOf("Using DNS over TLS with $hostname:$port\n"))

                        val sslContext = SSLContext.getInstance("TLS")
                        sslContext.init(null, null, null)

                        val socket = sslContext.socketFactory.createSocket(hostname, port) as SSLSocket

                        val sslParameters = socket.sslParameters
                        sslParameters.endpointIdentificationAlgorithm = "HTTPS"
                        socket.sslParameters = sslParameters
                        socket.soTimeout = 5000

                        emit(listOf("TLS connection established"))

                        val type = getDnsType(request.queryType)
                        val name = getDnsName(request.domain, request.queryType)

                        val record = Record.newRecord(name, type, DClass.IN)
                        val query = Message.newQuery(record)
                        if (request.useRecursion) {
                            query.header.setFlag(Flags.RD.toInt())
                        }

                        val wireFormat = query.toWire()
                        val outputStream = socket.getOutputStream()
                        outputStream.write(((wireFormat.size shr 8) and 0xFF))
                        outputStream.write((wireFormat.size and 0xFF))
                        outputStream.write(wireFormat)
                        outputStream.flush()

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
                        val resolverIp: String

                        if (request.useCustomResolver && request.customResolverHost.isNotBlank()) {
                            resolverIp = request.customResolverHost.trim()
                            emit(listOf("Using custom DNS resolver: $resolverIp:${request.customResolverPort}"))
                        } else {
                            val provider = getDnsProvider(request.selectedResolver)
                            resolverIp = provider.ip
                            emit(listOf("Using DNS resolver: ${provider.ip}"))
                        }

                        val resolver = SimpleResolver(resolverIp)
                        resolver.timeout = Duration.ofSeconds(5)

                        if (request.useCustomResolver) {
                            resolver.port = request.customResolverPort
                        }

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

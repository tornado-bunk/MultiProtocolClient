package it.tornado.multiprotocolclient.viewmodel

import android.app.Application
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.tornado.multiprotocolclient.protocol.custom.CustomHandler
import it.tornado.multiprotocolclient.protocol.custom.CustomRequest
import it.tornado.multiprotocolclient.protocol.dns.CronetHelper
import it.tornado.multiprotocolclient.protocol.dns.DnsHandler
import it.tornado.multiprotocolclient.protocol.dns.DnsRequest
import it.tornado.multiprotocolclient.protocol.http.HttpHandler
import it.tornado.multiprotocolclient.protocol.http.HttpRequest
import it.tornado.multiprotocolclient.protocol.ntp.NtpHandler
import it.tornado.multiprotocolclient.protocol.ntp.NtpRequest
import it.tornado.multiprotocolclient.protocol.diagnostics.PingHandler
import it.tornado.multiprotocolclient.protocol.diagnostics.TracerouteHandler
import it.tornado.multiprotocolclient.protocol.mail.SmtpHandler
import it.tornado.multiprotocolclient.protocol.mail.Pop3Handler
import it.tornado.multiprotocolclient.protocol.mail.ImapHandler
import it.tornado.multiprotocolclient.protocol.ssh.InteractiveSshHandler
import it.tornado.multiprotocolclient.protocol.telnet.InteractiveTelnetHandler
import it.tornado.multiprotocolclient.protocol.wol.WolHandler
import it.tornado.multiprotocolclient.protocol.whois.WhoisHandler
import it.tornado.multiprotocolclient.protocol.discovery.DiscoveryHandler
import it.tornado.multiprotocolclient.protocol.ftp.FtpHandler
import it.tornado.multiprotocolclient.protocol.snmp.SnmpHandler
import it.tornado.multiprotocolclient.protocol.mqtt.MqttHandler
import it.tornado.multiprotocolclient.protocol.tftp.TftpHandler
import it.tornado.multiprotocolclient.protocol.upnp.UpnpHandler
import it.tornado.multiprotocolclient.protocol.iperf.Iperf2Handler
import it.tornado.multiprotocolclient.protocol.iperf.Iperf3Handler
import it.tornado.multiprotocolclient.protocol.mdns.MdnsBonjourHandler
import it.tornado.multiprotocolclient.protocol.portscanner.PortScannerHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ClientViewModel(application: Application) : AndroidViewModel(application) {
    private val _response = MutableStateFlow<List<String>>(emptyList())
    val response: StateFlow<List<String>> = _response.asStateFlow()
    private var activeProtocol: String = "General"
    private val _activeProtocol = MutableStateFlow(activeProtocol)
    val activeProtocolFlow: StateFlow<String> = _activeProtocol.asStateFlow()
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val dnsHandler = DnsHandler()
    private val httpHandler = HttpHandler()
    private val ntpHandler = NtpHandler()
    private val customHandler = CustomHandler()
    private val pingHandler = PingHandler()
    private val tracerouteHandler = TracerouteHandler()
    private val smtpHandler = SmtpHandler()
    private val pop3Handler = Pop3Handler()
    private val imapHandler = ImapHandler()
    private val wolHandler = WolHandler()
    private val whoisHandler = WhoisHandler()
    private val discoveryHandler = DiscoveryHandler(application.applicationContext)
    private val ftpHandler = FtpHandler()
    private val tftpHandler = TftpHandler()
    private val upnpHandler = UpnpHandler()
    private val snmpHandler = SnmpHandler()
    private val mqttHandler = MqttHandler()
    private val iperf3Handler = Iperf3Handler(application.applicationContext)
    private val iperf2Handler = Iperf2Handler(application.applicationContext)
    private val mdnsBonjourHandler = MdnsBonjourHandler(application.applicationContext)
    private val portScannerHandler = PortScannerHandler()

    init {
        // Register BouncyCastle provider for advanced cryptography (needed for SSH/SFTP x25519)
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())

        // Initialize Cronet engine for HTTP/3 support
        CronetHelper.initialize(application.applicationContext)
    }

    // Interactive Sessions
    val isInteractiveSessionActive = MutableStateFlow(false)
    private var interactiveTelnetHandler: InteractiveTelnetHandler? = null
    private var interactiveSshHandler: InteractiveSshHandler? = null

    private fun appendLines(chunk: String) {
        val lines = chunk.split("\n").map { it.trimEnd('\r') }.filter { it.isNotEmpty() }
        if (lines.isNotEmpty()) {
            appendProtocolChunk(lines)
        }
    }

    private fun beginProtocolOutput(protocol: String, requestSummary: List<String>) {
        activeProtocol = protocol
        _activeProtocol.value = protocol
        _response.value = buildList {
            add("=== $protocol ===")
            add("SECTION: SUMMARY")
            add("• Protocol: $protocol")
            add("• Started at: ${LocalDateTime.now().format(timeFormatter)}")
            add("SECTION: REQUEST")
            requestSummary.forEach { add("• $it") }
            add("SECTION: RESPONSE")
        }
    }

    private fun finishProtocolOutput() {
        _response.update { it + listOf("SECTION: END", "• Completed: $activeProtocol", "") }
    }

    private fun appendProtocolChunk(chunk: String) {
        val lines = chunk.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isNotEmpty()) {
            appendProtocolChunk(lines)
        }
    }

    private fun appendProtocolChunk(chunk: List<String>) {
        val lines = chunk.flatMap { formatProtocolLine(activeProtocol, it) }
        if (lines.isNotEmpty()) {
            _response.update { it + lines }
        }
    }

    private fun formatProtocolLine(protocol: String, raw: String): List<String> {
        val line = normalizeConsoleLine(raw)
        if (line.isBlank()) return emptyList()

        if (line.startsWith("Error", ignoreCase = true)) {
            return listOf("ERROR: ${line.removePrefix("Error:").trim()}")
        }

        if (line.startsWith("C:")) {
            return listOf("Client: ${line.removePrefix("C:").trim()}")
        }
        if (line.startsWith("S:")) {
            return listOf("Server: ${line.removePrefix("S:").trim()}")
        }

        return when (protocol) {
            "HTTP" -> {
                when {
                    line.startsWith("Status Code", ignoreCase = true) -> listOf("Status: ${line.removePrefix("Status Code:").trim()}")
                    line.startsWith("Body:", ignoreCase = true) -> listOf("Body")
                    line.startsWith("Headers:", ignoreCase = true) -> listOf("Headers")
                    else -> listOf("• $line")
                }
            }
            "DNS" -> {
                when {
                    line.startsWith("Processing DNS query", ignoreCase = true) -> listOf("Progress", "• $line")
                    line.startsWith("Using DNS over", ignoreCase = true) -> listOf("• $line")
                    line.startsWith("Establishing", ignoreCase = true) -> listOf("• $line")
                    line.startsWith("Resolved ", ignoreCase = true) -> listOf("• $line")
                    line.contains("connection established", ignoreCase = true) -> listOf("• $line")
                    line.startsWith("Response received", ignoreCase = true) -> listOf("• $line")
                    line.equals("Answer Section:", ignoreCase = true) -> listOf("Answer records")
                    line.equals("Authority Section:", ignoreCase = true) -> listOf("Authority records")
                    "\tIN\t" in line -> listOf("• ${formatDnsRecord(line)}")
                    else -> listOf("• $line")
                }
            }
            "NTP" -> {
                when {
                    line.contains("time", ignoreCase = true) || line.contains("timezone", ignoreCase = true) -> listOf("Time data: $line")
                    else -> listOf("• $line")
                }
            }
            "Custom" -> {
                when {
                    line.contains("connecting", ignoreCase = true) || line.contains("connection", ignoreCase = true) -> listOf("Connection: $line")
                    else -> listOf("• $line")
                }
            }
            "Ping", "Traceroute" -> {
                when {
                    line.contains("bytes from", ignoreCase = true) || line.contains("icmp_seq", ignoreCase = true) -> listOf("Reply: $line")
                    line.contains("packet loss", ignoreCase = true) || line.contains("rtt", ignoreCase = true) -> listOf("Statistics: $line")
                    else -> listOf("• $line")
                }
            }
            "Port Scanner" -> {
                when {
                    line.startsWith("OPEN ") -> listOf("Open port: ${line.removePrefix("OPEN ").trim()}")
                    line.startsWith("Progress:", ignoreCase = true) -> listOf("Progress: ${line.removePrefix("Progress:").trim()}")
                    line.contains("scan complete", ignoreCase = true) -> listOf("Summary: $line")
                    else -> listOf("• $line")
                }
            }
            "Discovery", "mDNS / Bonjour", "UPnP" -> {
                when {
                    line.contains("found", ignoreCase = true) || line.contains("service", ignoreCase = true) || line.contains("device", ignoreCase = true) -> listOf("Discovery: $line")
                    else -> listOf("• $line")
                }
            }
            "FTP", "TFTP" -> {
                when {
                    line.contains("connected", ignoreCase = true) || line.contains("login", ignoreCase = true) -> listOf("Connection: $line")
                    line.contains("file", ignoreCase = true) || line.contains("dir", ignoreCase = true) -> listOf("Data: $line")
                    else -> listOf("• $line")
                }
            }
            "SNMP" -> {
                if (line.contains("=") || line.contains("oid", ignoreCase = true)) {
                    listOf("SNMP value: $line")
                } else {
                    listOf("• $line")
                }
            }
            "MQTT" -> {
                when {
                    line.contains("connected", ignoreCase = true) || line.contains("subscribed", ignoreCase = true) -> listOf("Session: $line")
                    line.contains("[") && line.contains("]") -> listOf("Message: $line")
                    else -> listOf("• $line")
                }
            }
            "iPerf3", "iPerf2" -> {
                if (line.contains("receiver", ignoreCase = true) || line.contains("sender", ignoreCase = true)) {
                    listOf("Result: $line")
                } else {
                    listOf("• $line")
                }
            }
            "SMTP", "POP3", "IMAP", "Telnet", "SSH" -> {
                when {
                    line.startsWith("Client:", ignoreCase = true) -> listOf(line)
                    line.startsWith("Server:", ignoreCase = true) -> listOf(line)
                    else -> listOf("• $line")
                }
            }
            "WoL" -> {
                when {
                    line.contains("magic packet", ignoreCase = true) || line.contains("sent", ignoreCase = true) -> listOf("Action: $line")
                    else -> listOf("• $line")
                }
            }
            "WHOIS" -> {
                when {
                    ":" in line -> listOf("• $line")
                    else -> listOf("• $line")
                }
            }
            else -> listOf("• $line")
        }
    }

    private fun normalizeConsoleLine(raw: String): String {
        return raw
            .trim()
            .removePrefix("Response | ")
            .removePrefix("Request | ")
            .removePrefix("Summary | ")
            .removePrefix("Transcript | ")
            .removePrefix("Discovery | ")
            .removePrefix("Raw output | ")
            .removePrefix("Result | ")
            .removePrefix("Open port | ")
            .removePrefix("Progress | ")
            .trim()
    }

    private fun formatDnsRecord(line: String): String {
        val tokens = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size < 5) return line
        val name = tokens[0]
        val ttl = tokens[1]
        val type = tokens[3]
        val value = tokens.drop(4).joinToString(" ")
        return "$name  [type=$type, ttl=$ttl]  ->  $value"
    }

    fun connectTelnet(host: String, port: String) {
        viewModelScope.launch {
            beginProtocolOutput("Telnet", listOf("Host: ${host.trim()}", "Port: ${port.toIntOrNull() ?: 23}"))
            val p = port.toIntOrNull() ?: 23
            interactiveTelnetHandler = InteractiveTelnetHandler()
            isInteractiveSessionActive.value = true
            interactiveTelnetHandler?.connect(host.trim(), p) { chunk ->
                appendLines(chunk)
            }
            isInteractiveSessionActive.value = false
            interactiveTelnetHandler = null
            finishProtocolOutput()
        }
    }

    fun connectSsh(host: String, port: String, user: String, pass: String) {
        viewModelScope.launch {
            beginProtocolOutput(
                "SSH",
                listOf("Host: ${host.trim()}", "Port: ${port.toIntOrNull() ?: 22}", "Username: $user")
            )
            val p = port.toIntOrNull() ?: 22
            interactiveSshHandler = InteractiveSshHandler()
            isInteractiveSessionActive.value = true
            interactiveSshHandler?.connect(host.trim(), p, user, pass) { chunk ->
                appendLines(chunk)
            }
            isInteractiveSessionActive.value = false
            interactiveSshHandler = null
            finishProtocolOutput()
        }
    }

    fun sendInteractiveCommand(protocol: String, command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (protocol == "Telnet") {
                interactiveTelnetHandler?.sendCommand(command) { chunk ->
                     appendLines(chunk)
                }
            } else if (protocol == "SSH") {
                interactiveSshHandler?.sendCommand(command) { chunk ->
                     appendLines(chunk)
                }
            }
        }
    }

    fun disconnectInteractiveSession(protocol: String) {
        if (protocol == "Telnet") {
            interactiveTelnetHandler?.disconnect()
        } else if (protocol == "SSH") {
            interactiveSshHandler?.disconnect()
        }
    }

    //HTTP Section
    fun sendHttpRequest(
        protocol: String,
        ip: String,
        port: String,
        useSSL: Boolean,
        seeOnlyStatusCode: Boolean,
        trustSelfSigned: Boolean = false
    ) {
        viewModelScope.launch {
            beginProtocolOutput(
                "HTTP",
                listOf(
                    "Host: ${ip.trim()}",
                    "Port: ${port.trim()}",
                    "SSL: $useSSL",
                    "Status only: $seeOnlyStatusCode"
                )
            )
            //creating the request object
            val request = HttpRequest(
                protocol = protocol,
                ip = ip.trim(),
                port = port.trim(),
                useSSL = useSSL,
                seeOnlyStatusCode = seeOnlyStatusCode,
                trustSelfSigned = trustSelfSigned
            )

            httpHandler.processHttpRequest(request).collect { chunk ->
                appendProtocolChunk(chunk)
            }
            finishProtocolOutput()
        }
    }

    //DNS Section
    fun sendDnsRequest(
        domain: String,
        queryType: String,
        useHttps: Boolean,
        useTls: Boolean,
        useQuic: Boolean,
        selectedResolver: String,
        useRecursion: Boolean,
        useTcp4Dns: Boolean,
        forceHttp3: Boolean,
        useCustomResolver: Boolean = false,
        customResolverHost: String = "",
        customResolverPort: Int = 53
    ) {
        viewModelScope.launch {
            beginProtocolOutput(
                "DNS",
                listOf(
                    "Target: ${domain.trim()}",
                    "Query: $queryType",
                    "DoH: $useHttps, DoT: $useTls, DoQ: $useQuic",
                    "Resolver: $selectedResolver"
                )
            )
            // Get the actual resolver if DoH is selected
            val actualResolver = if (useHttps && selectedResolver.contains("DoH")) {
                selectedResolver.replace(" DoH", "")
            } else {
                selectedResolver
            }

            //creating the request object
            val request = DnsRequest(
                domain = domain.trim(),
                queryType = queryType,
                useHttps = useHttps,
                useTls = useTls,
                useQuic = useQuic,
                selectedResolver = actualResolver,
                useRecursion = useRecursion,
                useTcp = useTcp4Dns,
                forceHttp3 = forceHttp3,
                useCustomResolver = useCustomResolver,
                customResolverHost = customResolverHost,
                customResolverPort = customResolverPort
            )

            try {
                dnsHandler.processDnsRequest(request).collect { chunk ->
                    appendProtocolChunk(chunk)
                }
            } catch (e: Exception) {
                appendProtocolChunk("Error processing DNS request: ${e.message}")
            }
            finishProtocolOutput()
        }
    }

    //NTP Section
    fun sendNtpRequest(
        ip: String,
        timezone: String
    ) {
        viewModelScope.launch {
            beginProtocolOutput("NTP", listOf("Host: ${ip.trim()}", "Timezone: $timezone"))
            val request = NtpRequest(
                ip = ip.trim(),
                timezone = timezone
            )

            ntpHandler.processNtpRequest(request).collect { chunk ->
                appendProtocolChunk(chunk)
            }
            finishProtocolOutput()
        }
    }

    //Custom Section
    fun sendCustomRequest(
        ip: String,
        port: String,
        message: String,
        useTcp: Boolean
    ) {
        viewModelScope.launch {
            beginProtocolOutput(
                "Custom",
                listOf(
                    "Host: ${ip.trim()}",
                    "Port: ${port.trim()}",
                    "Transport: ${if (useTcp) "TCP" else "UDP"}"
                )
            )
            val request = CustomRequest(
                ip = ip.trim(),
                port = port.trim(),
                message = message,
                useTcp = useTcp
            )

            customHandler.processCustomRequest(request).collect { chunk ->
                appendProtocolChunk(chunk)
            }
            finishProtocolOutput()
        }
    }

    // Ping Section
    fun sendPingRequest(host: String) {
        viewModelScope.launch {
            beginProtocolOutput("Ping", listOf("Host: $host"))
            val result = pingHandler.executePing(host)
            appendProtocolChunk(result)
            finishProtocolOutput()
        }
    }

    // Traceroute Section
    fun sendTracerouteRequest(host: String) {
        viewModelScope.launch {
            beginProtocolOutput("Traceroute", listOf("Host: $host"))
            val result = tracerouteHandler.executeTraceroute(host)
            appendProtocolChunk(result)
            finishProtocolOutput()
        }
    }

    // SMTP Section
    fun sendSmtpRequest(host: String, port: String, useSsl: Boolean, useStartTls: Boolean) {
        viewModelScope.launch {
            beginProtocolOutput(
                "SMTP",
                listOf("Host: $host", "Port: $port", "SSL: $useSsl", "STARTTLS: $useStartTls")
            )
            try {
                 val portInt = port.toInt()
                 smtpHandler.testSmtp(host, portInt, useSsl, useStartTls).collect { chunk ->
                    appendProtocolChunk(chunk)
                }
            } catch (e: Exception) {
                 appendProtocolChunk("Invalid port or error: ${e.message}")
            }
            finishProtocolOutput()
        }
    }

    // POP3 Section
    fun sendPop3Request(host: String, port: String, useSsl: Boolean) {
        viewModelScope.launch {
             beginProtocolOutput("POP3", listOf("Host: $host", "Port: $port", "SSL: $useSsl"))
             try {
                 val portInt = port.toInt()
                 pop3Handler.testPop3(host, portInt, useSsl).collect { chunk ->
                    appendProtocolChunk(chunk)
                }
            } catch (e: Exception) {
                 appendProtocolChunk("Invalid port or error: ${e.message}")
            }
            finishProtocolOutput()
        }
    }

    // IMAP Section
    fun sendImapRequest(host: String, port: String, useSsl: Boolean) {
        viewModelScope.launch {
             beginProtocolOutput("IMAP", listOf("Host: $host", "Port: $port", "SSL: $useSsl"))
             try {
                 val portInt = port.toInt()
                 imapHandler.testImap(host, portInt, useSsl).collect { chunk ->
                    appendProtocolChunk(chunk)
                }
            } catch (e: Exception) {
                 appendProtocolChunk("Invalid port or error: ${e.message}")
            }
            finishProtocolOutput()
        }
    }

    // WoL Section
    fun sendWolRequest(macAddress: String, broadcastAddress: String, port: Int = 9) {
        viewModelScope.launch {
            beginProtocolOutput("WoL", listOf("MAC: $macAddress", "Broadcast: $broadcastAddress", "Port: $port"))
            wolHandler.sendWakeOnLan(macAddress, broadcastAddress, port).collect { chunk ->
                appendProtocolChunk(chunk)
            }
            finishProtocolOutput()
        }
    }

    // WHOIS Section
    fun sendWhoisRequest(domain: String) {
        viewModelScope.launch {
            beginProtocolOutput("WHOIS", listOf("Domain: $domain"))
            whoisHandler.queryWhois(domain).collect { chunk ->
                appendProtocolChunk(chunk)
            }
            finishProtocolOutput()
        }
    }

    // Discovery Section
    fun sendDiscoveryRequest(timeoutSeconds: Int = 10) {
        viewModelScope.launch {
            beginProtocolOutput("Discovery", listOf("Timeout: ${timeoutSeconds}s"))
            discoveryHandler.scanNetwork(timeoutSeconds).collect { chunk ->
                appendProtocolChunk(chunk)
            }
            finishProtocolOutput()
        }
    }

    // FTP Section
    fun sendFtpRequest(host: String, port: String, user: String, pass: String, useSftp: Boolean) {
        viewModelScope.launch {
            beginProtocolOutput(
                "FTP",
                listOf("Host: $host", "Port: $port", "Transport: ${if (useSftp) "SFTP" else "FTP"}", "User: $user")
            )
            val p = port.toIntOrNull() ?: if (useSftp) 22 else 21
            if (useSftp) {
                ftpHandler.listFilesSftp(host, p, user, pass).collect { chunk ->
                    appendProtocolChunk(chunk)
                }
            } else {
                ftpHandler.listFilesFtp(host, p, user, pass).collect { chunk ->
                    appendProtocolChunk(chunk)
                }
            }
            finishProtocolOutput()
        }
    }

    // TFTP Section
    fun sendTftpRequest(host: String, port: String, filename: String) {
        viewModelScope.launch {
            beginProtocolOutput(
                "TFTP",
                listOf("Host: $host", "Port: $port", "File: ${if (filename.isEmpty()) "startup-config" else filename}")
            )
            val p = port.toIntOrNull() ?: 69
            val requestedFile = if (filename.isEmpty()) "startup-config" else filename
            tftpHandler.downloadFile(host, p, requestedFile).collect { chunk ->
                appendProtocolChunk(chunk)
            }
            finishProtocolOutput()
        }
    }

    // UPnP Section
    fun sendUpnpRequest() {
        viewModelScope.launch {
            beginProtocolOutput("UPnP", listOf("Operation: IGD discovery + external IP query"))
            upnpHandler.queryUpnpIgd().collect { chunk ->
                appendProtocolChunk(chunk)
            }
            finishProtocolOutput()
        }
    }

    // SNMP Section
    fun sendSnmpRequest(ipAddress: String, port: String, community: String, oid: String) {
        viewModelScope.launch {
            beginProtocolOutput(
                "SNMP",
                listOf("Host: $ipAddress", "Port: $port", "Community: $community", "OID: $oid")
            )
            val p = port.toIntOrNull() ?: 161
            snmpHandler.querySnmp(ipAddress, p, community, oid).collect { chunk ->
                appendProtocolChunk(chunk)
            }
            finishProtocolOutput()
        }
    }

    // MQTT Section
    fun sendMqttSubscribeRequest(host: String, port: String, topic: String, useSsl: Boolean, username: String, pass: String) {
        viewModelScope.launch {
            beginProtocolOutput(
                "MQTT",
                listOf("Broker: $host:$port", "Topic: $topic", "TLS: $useSsl", "User: ${username.ifBlank { "anonymous" }}")
            )
            val p = port.toIntOrNull() ?: if (useSsl) 8883 else 1883
            mqttHandler.subscribeMqtt(host, p, topic, useSsl, username, pass).collect { chunk ->
                appendProtocolChunk(chunk)
            }
            finishProtocolOutput()
        }
    }

    // iPerf3 Section
    fun sendIperf3Request(host: String, port: String, duration: String, useUdp: Boolean, reverse: Boolean) {
        viewModelScope.launch {
            beginProtocolOutput(
                "iPerf3",
                listOf("Server: $host:$port", "Duration: ${duration}s", "UDP: $useUdp", "Reverse: $reverse")
            )
            val p = port.toIntOrNull() ?: 5201
            val d = duration.toIntOrNull() ?: 10
            iperf3Handler.runIperf3Client(host.trim(), p, d, useUdp, reverse).collect { chunk ->
                appendProtocolChunk(chunk)
            }
            finishProtocolOutput()
        }
    }

    // iPerf2 Section
    fun sendIperf2Request(host: String, port: String, duration: String, useUdp: Boolean) {
        viewModelScope.launch {
            beginProtocolOutput(
                "iPerf2",
                listOf("Server: $host:$port", "Duration: ${duration}s", "UDP: $useUdp")
            )
            val p = port.toIntOrNull() ?: 5001
            val d = duration.toIntOrNull() ?: 10
            iperf2Handler.runIperf2Client(host.trim(), p, d, useUdp).collect { chunk ->
                appendProtocolChunk(chunk)
            }
            finishProtocolOutput()
        }
    }

    // mDNS/Bonjour Section
    fun sendMdnsBonjourRequest(serviceType: String, timeoutSeconds: String) {
        viewModelScope.launch {
            beginProtocolOutput(
                "mDNS / Bonjour",
                listOf("Service type: ${serviceType.trim().ifEmpty { "_http._tcp." }}", "Timeout: ${timeoutSeconds}s")
            )
            val timeout = timeoutSeconds.toIntOrNull() ?: 8
            val type = serviceType.trim().ifEmpty { "_http._tcp." }
            mdnsBonjourHandler.discoverServices(timeout, type).collect { chunk ->
                appendProtocolChunk(chunk)
            }
            finishProtocolOutput()
        }
    }

    // Port Scanner Section
    fun sendPortScannerRequest(host: String, startPort: String, endPort: String, timeoutMs: String) {
        viewModelScope.launch {
            beginProtocolOutput(
                "Port Scanner",
                listOf("Host: ${host.trim()}", "Range: $startPort-$endPort", "Timeout: ${timeoutMs}ms")
            )
            val start = startPort.toIntOrNull() ?: 1
            val end = endPort.toIntOrNull() ?: 1024
            val timeout = timeoutMs.toIntOrNull() ?: 250
            portScannerHandler.scanTcpPorts(host.trim(), start, end, timeout).collect { chunk ->
                appendProtocolChunk(chunk)
            }
            finishProtocolOutput()
        }
    }

    fun resetResponse() {
        activeProtocol = "General"
        _activeProtocol.value = activeProtocol
        _response.value = emptyList()
    }
}

package it.tornado.multiprotocolclient.viewmodel

import android.app.Application
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClientViewModel(application: Application) : AndroidViewModel(application) {
    private val _response = MutableStateFlow<List<String>>(emptyList())
    val response: StateFlow<List<String>> = _response.asStateFlow()

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

    init {
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
            _response.update { it + lines }
        }
    }

    fun connectTelnet(host: String, port: String) {
        viewModelScope.launch {
            _response.value = emptyList() // clear previous logs
            val p = port.toIntOrNull() ?: 23
            interactiveTelnetHandler = InteractiveTelnetHandler()
            isInteractiveSessionActive.value = true
            interactiveTelnetHandler?.connect(host.trim(), p) { chunk ->
                appendLines(chunk)
            }
            isInteractiveSessionActive.value = false
            interactiveTelnetHandler = null
        }
    }

    fun connectSsh(host: String, port: String, user: String, pass: String) {
        viewModelScope.launch {
            _response.value = emptyList() // clear previous logs
            val p = port.toIntOrNull() ?: 22
            interactiveSshHandler = InteractiveSshHandler()
            isInteractiveSessionActive.value = true
            interactiveSshHandler?.connect(host.trim(), p, user, pass) { chunk ->
                appendLines(chunk)
            }
            isInteractiveSessionActive.value = false
            interactiveSshHandler = null
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
                _response.value += chunk
            }
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
                    _response.value += chunk
                }
            } catch (e: Exception) {
                _response.value += listOf("Error processing DNS request: ${e.message}")
            }
        }
    }

    //NTP Section
    fun sendNtpRequest(
        ip: String,
        timezone: String
    ) {
        viewModelScope.launch {
            val request = NtpRequest(
                ip = ip.trim(),
                timezone = timezone
            )

            ntpHandler.processNtpRequest(request).collect { chunk ->
                _response.value += chunk
            }
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
            val request = CustomRequest(
                ip = ip.trim(),
                port = port.trim(),
                message = message,
                useTcp = useTcp
            )

            customHandler.processCustomRequest(request).collect { chunk ->
                _response.value += chunk
            }
        }
    }

    // Ping Section
    fun sendPingRequest(host: String) {
        viewModelScope.launch {
            val result = pingHandler.executePing(host)
            _response.value += result
        }
    }

    // Traceroute Section
    fun sendTracerouteRequest(host: String) {
        viewModelScope.launch {
            val result = tracerouteHandler.executeTraceroute(host)
            _response.value += result
        }
    }

    // SMTP Section
    fun sendSmtpRequest(host: String, port: String, useSsl: Boolean, useStartTls: Boolean) {
        viewModelScope.launch {
            try {
                 val portInt = port.toInt()
                 smtpHandler.testSmtp(host, portInt, useSsl, useStartTls).collect { chunk ->
                    _response.value += chunk
                }
            } catch (e: Exception) {
                 _response.value += listOf("Invalid port or error: ${e.message}")
            }
        }
    }

    // POP3 Section
    fun sendPop3Request(host: String, port: String, useSsl: Boolean) {
        viewModelScope.launch {
             try {
                 val portInt = port.toInt()
                 pop3Handler.testPop3(host, portInt, useSsl).collect { chunk ->
                    _response.value += chunk
                }
            } catch (e: Exception) {
                 _response.value += listOf("Invalid port or error: ${e.message}")
            }
        }
    }

    // IMAP Section
    fun sendImapRequest(host: String, port: String, useSsl: Boolean) {
        viewModelScope.launch {
             try {
                 val portInt = port.toInt()
                 imapHandler.testImap(host, portInt, useSsl).collect { chunk ->
                    _response.value += chunk
                }
            } catch (e: Exception) {
                 _response.value += listOf("Invalid port or error: ${e.message}")
            }
        }
    }

    // WoL Section
    fun sendWolRequest(macAddress: String, broadcastAddress: String, port: Int = 9) {
        viewModelScope.launch {
            wolHandler.sendWakeOnLan(macAddress, broadcastAddress, port).collect { chunk ->
                _response.value += chunk
            }
        }
    }

    // WHOIS Section
    fun sendWhoisRequest(domain: String) {
        viewModelScope.launch {
            whoisHandler.queryWhois(domain).collect { chunk ->
                _response.value += chunk
            }
        }
    }

    // Discovery Section
    fun sendDiscoveryRequest(timeoutSeconds: Int = 10) {
        viewModelScope.launch {
            discoveryHandler.scanNetwork(timeoutSeconds).collect { chunk ->
                _response.value += chunk
            }
        }
    }

    // FTP Section
    fun sendFtpRequest(host: String, port: String, user: String, pass: String, useSftp: Boolean) {
        viewModelScope.launch {
            val p = port.toIntOrNull() ?: if (useSftp) 22 else 21
            if (useSftp) {
                ftpHandler.listFilesSftp(host, p, user, pass).collect { chunk ->
                    _response.value += chunk
                }
            } else {
                ftpHandler.listFilesFtp(host, p, user, pass).collect { chunk ->
                    _response.value += chunk
                }
            }
        }
    }

    // TFTP Section
    fun sendTftpRequest(host: String, port: String, filename: String) {
        viewModelScope.launch {
            val p = port.toIntOrNull() ?: 69
            val requestedFile = if (filename.isEmpty()) "startup-config" else filename
            tftpHandler.downloadFile(host, p, requestedFile).collect { chunk ->
                _response.value += chunk
            }
        }
    }

    // UPnP Section
    fun sendUpnpRequest() {
        viewModelScope.launch {
            upnpHandler.queryUpnpIgd().collect { chunk ->
                _response.value += chunk
            }
        }
    }

    // SNMP Section
    fun sendSnmpRequest(ipAddress: String, port: String, community: String, oid: String) {
        viewModelScope.launch {
            val p = port.toIntOrNull() ?: 161
            snmpHandler.querySnmp(ipAddress, p, community, oid).collect { chunk ->
                _response.value += chunk
            }
        }
    }

    // MQTT Section
    fun sendMqttSubscribeRequest(host: String, port: String, topic: String, useSsl: Boolean, username: String, pass: String) {
        viewModelScope.launch {
            val p = port.toIntOrNull() ?: if (useSsl) 8883 else 1883
            mqttHandler.subscribeMqtt(host, p, topic, useSsl, username, pass).collect { chunk ->
                _response.value += chunk
            }
        }
    }

    fun resetResponse() {
        _response.value = emptyList()
    }
}

package it.tornado.multiprotocolclient.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.tornado.multiprotocolclient.protocol.custom.CustomHandler
import it.tornado.multiprotocolclient.protocol.custom.CustomRequest
import it.tornado.multiprotocolclient.protocol.dns.DnsHandler
import it.tornado.multiprotocolclient.protocol.dns.DnsRequest
import it.tornado.multiprotocolclient.protocol.http.HttpHandler
import it.tornado.multiprotocolclient.protocol.http.HttpRequest
import it.tornado.multiprotocolclient.protocol.ntp.NtpHandler
import it.tornado.multiprotocolclient.protocol.ntp.NtpRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClientViewModel : ViewModel() {
    private val _response = MutableStateFlow<List<String>>(emptyList())
    val response: StateFlow<List<String>> = _response.asStateFlow()

    private val dnsHandler = DnsHandler()
    private val httpHandler = HttpHandler()
    private val ntpHandler = NtpHandler()
    private val customHandler = CustomHandler()

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
        selectedResolver: String,
        useRecursion: Boolean,
        useTcp4Dns: Boolean
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
                selectedResolver = actualResolver,
                useRecursion = useRecursion,
                useTcp = useTcp4Dns
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
        useTcp: Boolean
    ) {
        viewModelScope.launch {
            val request = CustomRequest(
                ip = ip.trim(),
                port = port.trim(),
                useTcp = useTcp
            )

            customHandler.processCustomRequest(request).collect { chunk ->
                _response.value += chunk
            }
        }
    }

    fun resetResponse() {
        _response.value = emptyList()
    }
}

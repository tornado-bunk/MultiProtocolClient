package it.tornado.multiprotocolclient.protocol.upnp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

class UpnpHandler {

    fun queryUpnpIgd(): Flow<String> = flow {
        val discoveredLocations = mutableSetOf<String>()
        
        try {
            emit("UPnP Internet Gateway Device Search")
            emit("1) Sending SSDP M-SEARCH for IGD...")

            val ssdpMessage = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 3\r\n" +
                    "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n"

            val socket = DatagramSocket()
            socket.soTimeout = 3000 
            socket.broadcast = true

            val address = InetAddress.getByName("239.255.255.250")
            socket.send(DatagramPacket(ssdpMessage.toByteArray(), ssdpMessage.length, address, 1900))

            val receivePacket = DatagramPacket(ByteArray(4096), 4096)

            try {
                while (true) {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    val location = response.lines()
                        .find { it.startsWith("LOCATION:", ignoreCase = true) }
                        ?.substringAfter(":")?.trim()

                    if (location != null && discoveredLocations.add(location)) {
                        emit("Found Device at: $location")
                        processIgdDescriptor(location, this)
                    }
                }
            } catch (e: SocketTimeoutException) {
                if (discoveredLocations.isEmpty()) {
                    emit("No UPnP IGD found on network.")
                } else {
                    emit("Scan finished. Found ${discoveredLocations.size} device(s).")
                }
            } finally {
                socket.close()
            }

        } catch (e: Exception) {
            emit("UPnP Error: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun processIgdDescriptor(locationUrl: String, flow: kotlinx.coroutines.flow.FlowCollector<String>) {
        try {
            flow.emit("2) Fetching XML descriptor from: $locationUrl")
            val url = URL(locationUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000

            if (connection.responseCode == 200) {
                val xmlStr = connection.inputStream.bufferedReader().readText()
                
                val friendlyName = Regex("<friendlyName>(.*?)</friendlyName>").find(xmlStr)?.groupValues?.get(1) ?: "Unknown"
                flow.emit("Router: $friendlyName")

                val wanIP = "urn:schemas-upnp-org:service:WANIPConnection:1"
                val wanPPP = "urn:schemas-upnp-org:service:WANPPPConnection:1"
                
                val serviceType: String
                val controlPath: String?

                val ipMatch = Regex("<serviceType>$wanIP</serviceType>.*?<controlURL>(.*?)</controlURL>", RegexOption.DOT_MATCHES_ALL).find(xmlStr)
                if (ipMatch != null) {
                    serviceType = wanIP
                    controlPath = ipMatch.groupValues[1]
                } else {
                    val pppMatch = Regex("<serviceType>$wanPPP</serviceType>.*?<controlURL>(.*?)</controlURL>", RegexOption.DOT_MATCHES_ALL).find(xmlStr)
                    serviceType = wanPPP
                    controlPath = pppMatch?.groupValues?.get(1)
                }

                if (controlPath == null) {
                    flow.emit("No WAN IP/PPP service found on this descriptor.")
                    return
                }

                val port = if (url.port == -1) url.defaultPort else url.port
                val hostBaseUrl = "${url.protocol}://${url.host}:$port"
                val fullControlUrl = if (controlPath.startsWith("/")) "$hostBaseUrl$controlPath" else "$hostBaseUrl/$controlPath"

                flow.emit("3) Sending GetExternalIPAddress...")

                val soapBody = """
                    <?xml version="1.0"?>
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                    <u:GetExternalIPAddress xmlns:u="$serviceType"></u:GetExternalIPAddress>
                    </s:Body>
                    </s:Envelope>
                """.trimIndent()

                val postConn = URL(fullControlUrl).openConnection() as HttpURLConnection
                postConn.requestMethod = "POST"
                postConn.doOutput = true
                postConn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                postConn.setRequestProperty("SOAPAction", "\"$serviceType#GetExternalIPAddress\"")
                postConn.outputStream.write(soapBody.toByteArray())

                if (postConn.responseCode == 200) {
                    val respXml = postConn.inputStream.bufferedReader().readText()
                    val extIp = Regex("<NewExternalIPAddress>(.*?)</NewExternalIPAddress>").find(respXml)?.groupValues?.get(1) ?: "Unknown"
                    flow.emit("External IP: $extIp")
                } else {
                    flow.emit("SOAP Failed: HTTP ${postConn.responseCode}")
                }
            }
        } catch (e: Exception) {
            flow.emit("Error processing $locationUrl: ${e.message}")
        }
    }
}
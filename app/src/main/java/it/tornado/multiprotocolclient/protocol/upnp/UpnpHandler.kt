package it.tornado.multiprotocolclient.protocol.upnp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

class UpnpHandler {

    fun queryUpnpIgd(): Flow<String> = flow {
        try {
            emit("═══════════════════════════════════════")
            emit("  UPnP Internet Gateway Device Search")
            emit("═══════════════════════════════════════")
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
            var locationUrl = ""

            try {
                socket.receive(receivePacket)
                val responseLine = String(receivePacket.data, 0, receivePacket.length)
                val lines = responseLine.lines()
                for (line in lines) {
                    if (line.startsWith("LOCATION:", ignoreCase = true)) {
                        locationUrl = line.substringAfter("LOCATION:").trim()
                        break
                    }
                }
            } catch (e: Exception) {
                emit("❌ No UPnP IGD found on network (timeout).")
                socket.close()
                return@flow
            }
            socket.close()

            if (locationUrl.isEmpty()) {
                emit("❌ Device responded but provided no LOCATION URL.")
                return@flow
            }

            emit("✅ Found IGD Device descriptor at: $locationUrl")
            emit("2) Fetching XML descriptor...")

            val url = URL(locationUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val xmlStr = connection.inputStream.bufferedReader().readText()
                val serverIP = locationUrl.substringBefore(":", "http://127.0.0.1")
                
                // Extremely basic parsing to find the friendly name
                val friendlyNameMatch = Regex("<friendlyName>(.*?)</friendlyName>").find(xmlStr)
                val routerName = friendlyNameMatch?.groupValues?.get(1) ?: "Unknown Router"
                
                emit("✅ Connected to: $routerName")
                
                // Extracting Control URL for WANIPConnection
                val controlUrlMatch = Regex("<serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType>.*?<controlURL>(.*?)</controlURL>", RegexOption.DOT_MATCHES_ALL).find(xmlStr)
                val pppControlUrlMatch = Regex("<serviceType>urn:schemas-upnp-org:service:WANPPPConnection:1</serviceType>.*?<controlURL>(.*?)</controlURL>", RegexOption.DOT_MATCHES_ALL).find(xmlStr)
                
                var controlPath = controlUrlMatch?.groupValues?.get(1) ?: pppControlUrlMatch?.groupValues?.get(1)
                
                if (controlPath == null) {
                    emit("❌ Could not find WANIPConnection or WANPPPConnection service for Port Mapping.")
                    return@flow
                }
                
                val hostBaseUrl = "${url.protocol}://${url.host}:${url.port}"
                val fullControlUrl = if (controlPath.startsWith("/")) "$hostBaseUrl$controlPath"
                                       else "$hostBaseUrl/$controlPath"

                emit("3) Found Control URL: $fullControlUrl")
                emit("4) Sending GetExternalIPAddress SOAP request...")

                // Send GetExternalIPAddress
                val soapBody = "<?xml version=\"1.0\"?>\r\n" +
                        "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                        "<s:Body>" +
                        "<u:GetExternalIPAddress xmlns:u=\"urn:schemas-upnp-org:service:WANIPConnection:1\"></u:GetExternalIPAddress>" +
                        "</s:Body>" +
                        "</s:Envelope>"

                val postConn = URL(fullControlUrl).openConnection() as HttpURLConnection
                postConn.requestMethod = "POST"
                postConn.doOutput = true
                postConn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                postConn.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:WANIPConnection:1#GetExternalIPAddress\"")
                postConn.outputStream.write(soapBody.toByteArray())

                if (postConn.responseCode == 200) {
                    val respXml = postConn.inputStream.bufferedReader().readText()
                    val externalIpMatch = Regex("<NewExternalIPAddress>(.*?)</NewExternalIPAddress>").find(respXml)
                    val extIp = externalIpMatch?.groupValues?.get(1) ?: "Unknown"
                    emit("✅ External Public IP: $extIp")
                } else {
                    emit("❌ Failed to get external IP. (Code: ${postConn.responseCode})")
                }

                emit("Note: Full port mapping manipulation (AddPortMapping) is available in advanced mode.")
                
            } else {
                emit("❌ Failed to download IGD XML: HTTP ${connection.responseCode}")
            }

        } catch (e: Exception) {
            emit("❌ UPnP Error: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
}

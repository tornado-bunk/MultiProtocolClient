package it.tornado.multiprotocolclient.protocol.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList

class DiscoveryHandler(private val context: Context) {

    fun scanNetwork(timeoutSeconds: Int = 5): Flow<String> = flow {
        emit("Network Discovery (mDNS & SSDP)")
        emit("Scanning local network for devices...")
        emit("Timeout: $timeoutSeconds seconds")
        emit("")

        val ssdpDevices = CopyOnWriteArrayList<String>()
        val mdnsDevices = CopyOnWriteArrayList<String>()

        val ssdpJob = kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                // SSDP M-SEARCH
                val ssdpMessage = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: ssdp:all\r\n" +
                        "\r\n"
                
                val address = InetAddress.getByName("239.255.255.250")
                val socket = DatagramSocket()
                socket.soTimeout = timeoutSeconds * 1000
                socket.broadcast = true

                val sendPacket = DatagramPacket(ssdpMessage.toByteArray(), ssdpMessage.length, address, 1900)
                socket.send(sendPacket)

                val buffer = ByteArray(4096)
                val receivePacket = DatagramPacket(buffer, buffer.size)

                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
                    try {
                        socket.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length)
                        val ip = receivePacket.address.hostAddress
                        
                        // Parse Server or Location headers to identify the device
                        val lines = response.lines()
                        var serverName = "Unknown UPnP Device"
                        for (line in lines) {
                            if (line.startsWith("SERVER:", ignoreCase = true)) {
                                serverName = line.substringAfter("SERVER:").trim()
                                break
                            } else if (line.startsWith("LOCATION:", ignoreCase = true)) {
                                val loc = line.substringAfter("LOCATION:").trim()
                                if (serverName == "Unknown UPnP Device") serverName = loc
                            }
                        }
                        
                        val deviceStr = "[SSDP] $ip - $serverName"
                        if (!ssdpDevices.contains(deviceStr)) {
                            ssdpDevices.add(deviceStr)
                        }
                    } catch (e: SocketTimeoutException) {
                        break
                    } catch (e: Exception) {
                        break
                    }
                }
                socket.close()
            } catch (e: Exception) {
                // Ignore SSDP errors
            }
        }

        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val discoveredServices = mutableSetOf<String>()
        
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                // We resolve the service to get IP and port
                try {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val ip = serviceInfo.host?.hostAddress ?: "Unknown IP"
                            val port = serviceInfo.port
                            val name = serviceInfo.serviceName
                            val type = serviceInfo.serviceType
                            
                            val serviceStr = "[mDNS] $ip:$port - $name ($type)"
                            if (!mdnsDevices.contains(serviceStr) && !discoveredServices.contains(name)) {
                                mdnsDevices.add(serviceStr)
                                discoveredServices.add(name)
                            }
                        }
                    })
                } catch (e: Exception) {
                    // Ignore resolution errors
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
        }

        // Common service types to scan for
        val serviceTypes = listOf("_http._tcp.", "_ssh._tcp.", "_smb._tcp.", "_ipp._tcp.", "_googlecast._tcp.")
        
        for (type in serviceTypes) {
            try {
                nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            } catch (e: Exception) {}
        }

        // Wait for the timeout
        var elapsed = 0
        while (elapsed < timeoutSeconds) {
            delay(1000)
            elapsed++
            emit("... scanning (${elapsed}s / ${timeoutSeconds}s) ...")
        }

        // Stop mDNS scanning
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {}

        // Wait for SSDP job to gracefully finish
        withTimeoutOrNull<Unit>(2000) {
            ssdpJob.join()
        }

        // Report results
        emit("")
        emit("── Discovery Results ──")
        
        val allDevices = mutableListOf<String>()
        allDevices.addAll(mdnsDevices)
        allDevices.addAll(ssdpDevices)
        
        if (allDevices.isEmpty()) {
            emit("No devices found on the local network.")
        } else {
            allDevices.sorted().forEach {
                emit(it)
            }
            emit("")
            emit("Found ${allDevices.size} devices/services.")
        }

    }.flowOn(Dispatchers.IO)
}

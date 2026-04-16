package it.tornado.multiprotocolclient.protocol.mdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class MdnsBonjourHandler(private val context: Context) {

    fun discoverServices(
        timeoutSeconds: Int = 8,
        serviceType: String = "_http._tcp."
    ): Flow<String> = flow {
        emit("mDNS / Bonjour discovery")
        emit("Service type: $serviceType")
        emit("Timeout: ${timeoutSeconds}s")
        emit("")

        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val discoveredServices = mutableSetOf<String>()
        val discoveredNames = mutableSetOf<String>()
        val lock = Any()

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                try {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            // Ignore single-service resolution errors.
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val ip = info.host?.hostAddress ?: "Unknown IP"
                            val line = "[mDNS] $ip:${info.port} - ${info.serviceName} (${info.serviceType})"
                            synchronized(lock) {
                                if (!discoveredNames.contains(info.serviceName) && discoveredServices.add(line)) {
                                    discoveredNames.add(info.serviceName)
                                }
                            }
                        }
                    })
                } catch (_: Exception) {
                    // Ignore transient NSD errors
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            var elapsed = 0
            while (elapsed < timeoutSeconds) {
                delay(1000)
                elapsed++
                emit("... scanning ($elapsed/$timeoutSeconds)s ...")
            }
        } catch (e: Exception) {
            emit("mDNS discovery error: ${e.message}")
        } finally {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
            delay(300)
        }

        emit("")
        emit("── mDNS Results ──")
        val lines = synchronized(lock) { discoveredServices.toList().sorted() }
        if (lines.isEmpty()) {
            emit("No mDNS/Bonjour services found.")
        } else {
            lines.forEach { emit(it) }
            emit("")
            emit("Found ${lines.size} entries.")
        }
    }.flowOn(Dispatchers.IO)
}

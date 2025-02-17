package it.tornado.multiprotocolclient.protocol.dns

// Map of DNS resolvers
object DnsConstants {
    val RESOLVERS = mapOf(
        "Google DNS (8.8.8.8)" to "8.8.8.8",
        "Cloudflare (1.1.1.1)" to "1.1.1.1",
        "OpenDNS (208.67.222.222)" to "208.67.222.222",
        "Quad9 (9.9.9.9)" to "9.9.9.9",
        "AdGuard DNS (94.140.14.14)" to "94.140.14.14"
    )
}
package it.tornado.multiprotocolclient.protocol.dns

// Map of DNS resolvers
object DnsConstants {
    data class DnsProvider(
        val ip: String,
        val hostname: String,
        val dohUrl: String,
        val supportsDoq: Boolean = false,
        val doqPort: Int = 853
    )

    val RESOLVERS = mapOf(
        "Google DNS (8.8.8.8)" to DnsProvider(
            ip = "8.8.8.8",
            hostname = "dns.google",
            dohUrl = "https://dns.google/dns-query",
            supportsDoq = false
        ),
        "Cloudflare (1.1.1.1)" to DnsProvider(
            ip = "1.1.1.1",
            hostname = "1dot1dot1dot1.cloudflare-dns.com",
            dohUrl = "https://cloudflare-dns.com/dns-query",
            supportsDoq = false
        ),
        "OpenDNS (208.67.222.222)" to DnsProvider(
            ip = "208.67.222.222",
            hostname = "dns.opendns.com",
            dohUrl = "https://doh.opendns.com/dns-query",
            supportsDoq = false
        ),
        "Quad9 (9.9.9.9)" to DnsProvider(
            ip = "9.9.9.9",
            hostname = "dns.quad9.net",
            dohUrl = "https://dns.quad9.net/dns-query",
            supportsDoq = true,
            doqPort = 853
        ),
        "AdGuard DNS (94.140.14.14)" to DnsProvider(
            ip = "94.140.14.14",
            hostname = "dns.adguard-dns.com",
            dohUrl = "https://dns.adguard-dns.com/dns-query",
            supportsDoq = true,
            doqPort = 853
        )
    )

    // Resolvers that support DNS over QUIC
    val DOQ_RESOLVERS = RESOLVERS.filter { it.value.supportsDoq }

    // Fallback in case of lookup failure
    val FALLBACK_PROVIDER = DnsProvider("8.8.8.8", "dns.google", "https://dns.google/dns-query")
}
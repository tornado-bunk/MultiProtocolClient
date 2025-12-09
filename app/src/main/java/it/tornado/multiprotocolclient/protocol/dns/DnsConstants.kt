package it.tornado.multiprotocolclient.protocol.dns

// Map of DNS resolvers
object DnsConstants {
    data class DnsProvider(
        val ip: String,
        val hostname: String,
        val dohUrl: String
    )

    val RESOLVERS = mapOf(
        "Google DNS (8.8.8.8)" to DnsProvider(
            ip = "8.8.8.8",
            hostname = "dns.google",
            dohUrl = "https://dns.google/dns-query"
        ),
        "Cloudflare (1.1.1.1)" to DnsProvider(
            ip = "1.1.1.1",
            hostname = "1dot1dot1dot1.cloudflare-dns.com",
            dohUrl = "https://cloudflare-dns.com/dns-query"
        ),
        "OpenDNS (208.67.222.222)" to DnsProvider(
            ip = "208.67.222.222",
            hostname = "dns.opendns.com",
            dohUrl = "https://doh.opendns.com/dns-query"
        ),
        "Quad9 (9.9.9.9)" to DnsProvider(
            ip = "9.9.9.9",
            hostname = "dns.quad9.net",
            dohUrl = "https://dns.quad9.net/dns-query"
        ),
        "AdGuard DNS (94.140.14.14)" to DnsProvider(
            ip = "94.140.14.14",
            hostname = "dns.adguard.com",
            dohUrl = "https://dns.adguard.com/dns-query"
        )
    )
    
    // Fallback in case of lookup failure
    val FALLBACK_PROVIDER = DnsProvider("8.8.8.8", "dns.google", "https://dns.google/dns-query")
}
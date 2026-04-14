package it.tornado.multiprotocolclient.protocol.dns

data class DnsRequest(
    val domain: String,
    val queryType: String,
    val useHttps: Boolean,
    val useTls: Boolean,
    val useQuic: Boolean,
    val selectedResolver: String,
    val useRecursion: Boolean,
    val useTcp: Boolean,
    val forceHttp3: Boolean,
    val useCustomResolver: Boolean = false,
    val customResolverHost: String = "",
    val customResolverPort: Int = 53
)
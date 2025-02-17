package it.tornado.multiprotocolclient.protocol.dns

data class DnsRequest(
    val domain: String,
    val queryType: String,
    val useHttps: Boolean,
    val useTls: Boolean,
    val selectedResolver: String,
    val useRecursion: Boolean,
    val useTcp: Boolean
)
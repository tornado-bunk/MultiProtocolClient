package it.tornado.multiprotocolclient.protocol.http

data class HttpRequest(
    val protocol: String,
    val ip: String,
    val port: String,
    val useSSL: Boolean,
    val seeOnlyStatusCode: Boolean,
    val trustSelfSigned: Boolean = false
)

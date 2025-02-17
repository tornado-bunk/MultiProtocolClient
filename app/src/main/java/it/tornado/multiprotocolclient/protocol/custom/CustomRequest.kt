package it.tornado.multiprotocolclient.protocol.custom

data class CustomRequest(
    var ip: String,
    var port: String,
    val useTcp: Boolean
)
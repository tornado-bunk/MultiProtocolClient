package it.tornado.multiprotocolclient.protocol.custom

data class CustomRequest(
    var ip: String,
    var port: String,
    var message: String,
    val useTcp: Boolean
)
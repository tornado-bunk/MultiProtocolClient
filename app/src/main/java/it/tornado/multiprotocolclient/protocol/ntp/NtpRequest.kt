package it.tornado.multiprotocolclient.protocol.ntp

data class NtpRequest(
    val ip: String,
    val timezone: String
)
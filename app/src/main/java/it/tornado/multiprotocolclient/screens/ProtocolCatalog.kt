package it.tornado.multiprotocolclient.screens

data class ProtocolSectionUi(
    val title: String,
    val protocols: List<String>
)

val protocolSections = listOf(
    ProtocolSectionUi("HTTP/HTTPS", listOf("HTTP")),
    ProtocolSectionUi("DNS", listOf("DNS")),
    ProtocolSectionUi("NTP", listOf("NTP")),
    ProtocolSectionUi("Security and Troubleshooting", listOf("Port Scanner")),
    ProtocolSectionUi("Custom TCP/UDP", listOf("Custom")),
    ProtocolSectionUi("Network Diagnostics", listOf("Ping", "Traceroute")),
    ProtocolSectionUi("Email Protocols", listOf("SMTP", "POP3", "IMAP")),
    ProtocolSectionUi("Remote Access", listOf("Telnet", "SSH")),
    ProtocolSectionUi("Network Tools", listOf("WoL", "WHOIS")),
    ProtocolSectionUi("Monitoring & IoT", listOf("SNMP", "MQTT")),
    ProtocolSectionUi("Performance Testing", listOf("iPerf2", "iPerf3")),
    ProtocolSectionUi("File Transfer", listOf("FTP", "TFTP")),
    ProtocolSectionUi("Discovery & Router Management", listOf("mDNS / Bonjour", "Discovery", "UPnP")),
)

val allProtocols: List<String> = protocolSections.flatMap { it.protocols }.distinct()

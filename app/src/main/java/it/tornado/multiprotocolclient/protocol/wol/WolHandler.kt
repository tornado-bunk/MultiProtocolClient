package it.tornado.multiprotocolclient.protocol.wol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class WolHandler {

    fun sendWakeOnLan(macAddress: String, broadcastAddress: String = "255.255.255.255", port: Int = 9): Flow<String> = flow {
        try {
            // Parse and validate MAC address
            val cleanMac = macAddress.replace(Regex("[:\\-.]"), "").uppercase()
            if (cleanMac.length != 12 || !cleanMac.matches(Regex("[0-9A-F]+"))) {
                emit("Invalid MAC address: $macAddress")
                emit("Expected format: AA:BB:CC:DD:EE:FF or AA-BB-CC-DD-EE-FF")
                return@flow
            }

            val macBytes = ByteArray(6)
            for (i in 0 until 6) {
                macBytes[i] = cleanMac.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }

            // Build Magic Packet: 6 bytes of 0xFF followed by 16 repetitions of the MAC address
            val magicPacket = ByteArray(6 + 16 * 6)

            // Fill first 6 bytes with 0xFF
            for (i in 0 until 6) {
                magicPacket[i] = 0xFF.toByte()
            }

            // Fill remaining bytes with 16 copies of the MAC address
            for (i in 0 until 16) {
                System.arraycopy(macBytes, 0, magicPacket, 6 + i * 6, 6)
            }

            val formattedMac = cleanMac.chunked(2).joinToString(":")

            emit("Wake-on-LAN Magic Packet")
            emit("")
            emit("Target MAC:      $formattedMac")
            emit("Broadcast Addr:  $broadcastAddress")
            emit("UDP Port:        $port")
            emit("Packet Size:     ${magicPacket.size} bytes")
            emit("")

            // Send the packet via UDP broadcast
            val address = InetAddress.getByName(broadcastAddress)
            val packet = DatagramPacket(magicPacket, magicPacket.size, address, port)

            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.send(packet)
            }

            emit("Magic Packet sent successfully!")
            emit("")
            emit("Note: The target device must have WoL enabled")
            emit("in its BIOS/UEFI and network adapter settings.")

        } catch (e: Exception) {
            emit("Failed to send WoL packet: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
}

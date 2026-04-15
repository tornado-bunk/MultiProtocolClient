package it.tornado.multiprotocolclient.protocol.snmp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.snmp4j.CommunityTarget
import org.snmp4j.PDU
import org.snmp4j.Snmp
import org.snmp4j.TransportMapping
import org.snmp4j.event.ResponseEvent
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.smi.Address
import org.snmp4j.smi.GenericAddress
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.VariableBinding
import org.snmp4j.transport.DefaultUdpTransportMapping

class SnmpHandler {

    fun querySnmp(ipAddress: String, port: Int = 161, community: String = "public", oid: String = "1.3.6.1.2.1.1.1.0"): Flow<String> = flow {
        var transport: TransportMapping<*>? = null
        var snmp: Snmp? = null
        try {
            emit("SNMP Query: $ipAddress:$port")
            emit("Connecting with community '$community'...")

            transport = DefaultUdpTransportMapping()
            snmp = Snmp(transport)
            transport.listen()

            val targetAddress = GenericAddress.parse("udp:$ipAddress/$port")
            val target = CommunityTarget<Address>()
            target.community = OctetString(community)
            target.address = targetAddress
            target.retries = 2
            target.timeout = 3000
            target.version = SnmpConstants.version2c

            val pdu = PDU()
            pdu.add(VariableBinding(OID(oid)))
            pdu.type = PDU.GET
            pdu.requestID = org.snmp4j.smi.Integer32(1)

            emit("Sending GET request for OID: $oid")
            val event: ResponseEvent<Address>? = snmp.send(pdu, target) as? ResponseEvent<Address>
            val responsePdu = event?.response

            if (responsePdu != null) {
                if (responsePdu.errorStatus == PDU.noError) {
                    val variableBinding = responsePdu.variableBindings.firstOrNull()
                    if (variableBinding != null) {
                        emit("Response from $ipAddress:")
                        emit("${variableBinding.oid} = ${variableBinding.variable}")
                    } else {
                        emit("Response received, but no variables found.")
                    }
                } else {
                    emit("SNMP Error: ${responsePdu.errorStatusText} (Status: ${responsePdu.errorStatus})")
                }
            } else {
                emit("No response. Timeout reached.")
            }
        } catch (e: Exception) {
            emit("SNMP Exception: ${e.message}")
        } finally {
            try {
                snmp?.close()
                transport?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }.flowOn(Dispatchers.IO)
}

/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jitsi.videobridge.dtls

import org.jitsi.nlj.PacketInfo
import org.jitsi.nlj.dtls.DtlsClient
import org.jitsi.nlj.dtls.DtlsServer
import org.jitsi.nlj.dtls.DtlsStack
import org.jitsi.nlj.srtp.TlsRole
import org.jitsi.rtp.UnparsedPacket
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger
import org.jitsi.videobridge.aspect.onlyIf
import org.jitsi.xmpp.extensions.jingle.DtlsFingerprintPacketExtension
import org.jitsi.xmpp.extensions.jingle.IceUdpTransportPacketExtension
import java.lang.IllegalStateException
import java.net.DatagramPacket

typealias Offset = Int
typealias Length = Int
typealias IncomingDtlsTransportDataHandler = (ByteArray, Offset, Length) -> Unit
typealias OutgoingDtlsDataSender = (ByteArray, Offset, Length) -> Unit
typealias HashFunc = String
typealias Fingerprint = String

class DtlsTransportStandalone(
    parentLogger: Logger
) {
    private val logger = createChildLogger(parentLogger)

    private val dtlsStack = DtlsStack(logger).apply {
        onOutgoingProtocolData { pkts ->
            pkts.forEach { pkt ->
                dataSender?.invoke(pkt.packet.buffer, pkt.packet.offset, pkt.packet.length)
            }
        }
        onHandshakeComplete { chosenSrtpProfile, tlsRole, keyingMaterial ->
            eventHandler?.dtlsHandshakeCompleted(chosenSrtpProfile, tlsRole, keyingMaterial)
        }
    }

    private var incomingDataHandler: IncomingDtlsTransportDataHandler? = null

    var dataSender: OutgoingDtlsDataSender? = null

    var eventHandler: DtlsTransportEventHandler? = null

    fun setSetupAttribute(setupAttr: String) = onlyIf(dtlsStack.role == null) {
        when (setupAttr.toLowerCase()) {
            "active" -> {
                logger.info("The remote side is acting as DTLS client, we'll act as server")
                dtlsStack.actAsServer()
            }
            "passive" -> {
                logger.info("The remote side is acting as DTLS server, we'll act as client")
                dtlsStack.actAsClient()
            }
            else -> logger.error("The remote side sent an unrecognized DTLS setup value: $setupAttr")
        }
    }

    fun setRemoteFingerprints(remoteFingerprints: Map<HashFunc, Fingerprint>) = onlyIf(remoteFingerprints.isNotEmpty()) {
        dtlsStack.remoteFingerprints = remoteFingerprints
        val hasSha1Hash = remoteFingerprints.keys.any { hashFunc -> hashFunc.equals("sha-1", true) }
        if (dtlsStack.role == null && hasSha1Hash) {
            // hack(george) Jigasi sends a sha-1 dtls fingerprint without a
            // setup attribute and it assumes a server role for the bridge.
            logger.info("Assume that the remote side is Jigasi, we'll act as server")
            dtlsStack.actAsServer()
        }
    }

    //TODO: create a 'DtlsPacket' for some type safety?
    fun dataReceived(data: DatagramPacket) {
        //TODO: change the API of DTLS stack...right now it's oriented around
        // packet info and pipelines (even though it isn't involved in a pipeline
        // directly).  Ideally the stack would look more like the transports:
        // have methods to take data in and callbacks for output data, so we could
        // pass data in here and hook the stack's output callback to our output
        // callback
        val packetInfo = PacketInfo(UnparsedPacket(data.data, data.offset, data.length))
        val dtlsAppPackets = dtlsStack.processIncomingProtocolData(packetInfo)
        dtlsAppPackets.forEach {
            incomingDataHandler?.invoke(it.packet.buffer, it.packet.offset, it.packet.length)
        }
    }

    fun send(data: ByteArray, off: Int, len: Int) {
        //TODO: see comment in #dataReceived about dtlsStack API
        val packetInfo = PacketInfo(UnparsedPacket(data, off, len))
        dtlsStack.sendApplicationData(packetInfo)
    }

    fun describe(pe: IceUdpTransportPacketExtension) {
        val fingerprintPE = pe.getFirstChildOfType(DtlsFingerprintPacketExtension::class.java) ?: DtlsFingerprintPacketExtension().also {
            pe.addChildExtension(it)
        }
        fingerprintPE.fingerprint = dtlsStack.localFingerprint
        fingerprintPE.hash = dtlsStack.localFingerprintHashFunction
        fingerprintPE.setup = when (dtlsStack.role) {
            // We've not chosen a role yet, so we can be either
            null -> "actpass"
            is DtlsServer -> "passive"
            is DtlsClient -> "active"
            else -> throw IllegalStateException("Can not describe role ${dtlsStack.role}")
        }
    }

    interface DtlsTransportEventHandler {
        fun dtlsHandshakeCompleted(chosenSrtpProfile: Int, tlsRole: TlsRole, keyingMaterial: ByteArray)
    }
}
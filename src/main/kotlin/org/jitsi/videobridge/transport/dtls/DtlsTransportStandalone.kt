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

package org.jitsi.videobridge.transport.dtls

import org.jitsi.nlj.PacketInfo
import org.jitsi.nlj.dtls.DtlsClient
import org.jitsi.nlj.dtls.DtlsServer
import org.jitsi.nlj.dtls.DtlsStack
import org.jitsi.nlj.srtp.TlsRole
import org.jitsi.rtp.UnparsedPacket
import org.jitsi.rtp.extensions.unsigned.toPositiveInt
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.cdebug
import org.jitsi.utils.logging2.createChildLogger
import org.jitsi.videobridge.aspect.onlyIf
import org.jitsi.videobridge.transport.Transport
import org.jitsi.videobridge.transport.TransportState
import org.jitsi.videobridge.util.TaskPools
import org.jitsi.xmpp.extensions.jingle.DtlsFingerprintPacketExtension
import org.jitsi.xmpp.extensions.jingle.IceUdpTransportPacketExtension
import java.lang.IllegalStateException
import java.net.DatagramPacket
import java.util.concurrent.atomic.AtomicBoolean

typealias Offset = Int
typealias Length = Int
typealias IncomingDtlsTransportDataHandler = (ByteArray, Offset, Length) -> Unit
typealias OutgoingDtlsDataSender = (ByteArray, Offset, Length) -> Unit
typealias HashFunc = String
typealias Fingerprint = String

class DtlsTransportStandalone(
    parentLogger: Logger
) : Transport {
    private val logger = createChildLogger(parentLogger)

    private val dtlsStack = DtlsStack(logger).apply {
        onOutgoingProtocolData { pkts ->
            pkts.forEach { pkt ->
                dataSender?.let {
                    logger.cdebug { "Sending out DTLS data" }
                    it(pkt.packet.buffer, pkt.packet.offset, pkt.packet.length)
                } ?: run {
                    logger.cdebug { "Data sender null, dropping outgoing DTLS data" }
                }
            }
        }
        onHandshakeComplete { chosenSrtpProfile, tlsRole, keyingMaterial ->
            logger.cdebug { "Handshake completed" }
            state = TransportState.CONNECTED
            eventHandler?.dtlsHandshakeCompleted(chosenSrtpProfile, tlsRole, keyingMaterial) ?: run {
                logger.cdebug { "Event handler null" }
            }
        }
    }

    override var state: TransportState = TransportState.UNITITALIZED
        private set

    @JvmField
    var incomingDataHandler: IncomingDtlsTransportDataHandler? = null

    @JvmField
    var dataSender: OutgoingDtlsDataSender? = null

    @JvmField
    var eventHandler: DtlsTransportEventHandler? = null

    private val closed = AtomicBoolean(false)

    fun setSetupAttribute(setupAttr: String) = onlyIf(dtlsStack.role == null) {
        when (setupAttr.toLowerCase()) {
            "active" -> {
                logger.info("The remote side is acting as DTLS client, we'll act as server")
                dtlsStack.actAsServer()
                logger.info("DONE")
            }
            "passive" -> {
                logger.info("The remote side is acting as DTLS server, we'll act as client")
                dtlsStack.actAsClient()
                logger.info("DONE, dtlsStack (${System.identityHashCode(dtlsStack)}) role set? ${dtlsStack.role}")
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

    fun startHandshake() {
        logger.info("Starting DTLS.")
        logger.info("dtlsStack (${System.identityHashCode(dtlsStack)}) role set? ${dtlsStack.role}")
        if (dtlsStack.role == null) {
            logger.warn("Starting the DTLS stack before it knows its role")
        }
        state = TransportState.PENDING
        dtlsStack.start()
    }

    //TODO: create a 'DtlsPacket' for some type safety?
    fun dataReceived(data: DatagramPacket) {
        logger.cdebug { "Received data" }
        //TODO: change the API of DTLS stack...right now it's oriented around
        // packet info and pipelines (even though it isn't involved in a pipeline
        // directly).  Ideally the stack would look more like the transports:
        // have methods to take data in and callbacks for output data, so we could
        // pass data in here and hook the stack's output callback to our output
        // callback
        val packetInfo = PacketInfo(UnparsedPacket(data.data, data.offset, data.length))
        val dtlsAppPackets = dtlsStack.processIncomingProtocolData(packetInfo)
        dtlsAppPackets.forEach { appData ->
            incomingDataHandler?.let {
                logger.cdebug { "Forwarding data to handler" }
                it(appData.packet.buffer, appData.packet.offset, appData.packet.length)
            } ?: run {
                logger.cdebug { "Handler is null, dropping data" }
            }
        }
    }

    fun send(data: ByteArray, off: Int, len: Int) {
        logger.cdebug { "Sending outgoing DTLS data through DTLS stack" }
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

    fun close() {
        if (closed.compareAndSet(false, true)) {
            state = TransportState.CLOSED
            // TODO: any cleanup to do here?
        }
    }

    interface DtlsTransportEventHandler {
        fun dtlsHandshakeCompleted(chosenSrtpProfile: Int, tlsRole: TlsRole, keyingMaterial: ByteArray)
    }
}

// TODO: move these
private val DTLS_RANGE = 20..63
fun DatagramPacket.looksLikeDtls(): Boolean {
    return if (length < 1) {
        false
    } else {
        data[offset].toPositiveInt() in DTLS_RANGE
    }

}

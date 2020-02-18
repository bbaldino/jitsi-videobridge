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

package org.jitsi.videobridge.transport.ice

import org.ice4j.Transport
import org.ice4j.TransportAddress
import org.ice4j.ice.Agent
import org.ice4j.ice.CandidateType
import org.ice4j.ice.IceMediaStream
import org.ice4j.ice.IceProcessingState
import org.ice4j.ice.LocalCandidate
import org.ice4j.ice.RemoteCandidate
import org.ice4j.socket.SocketClosedException
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.cdebug
import org.jitsi.utils.logging2.createChildLogger
import org.jitsi.videobridge.aspect.requires
import org.jitsi.videobridge.ice.Harvesters
import org.jitsi.videobridge.transport.ice.IceConfig.Config
import org.jitsi.videobridge.ice.TransportUtils
import org.jitsi.videobridge.transport.TransportState
import org.jitsi.videobridge.util.ByteBufferPool
import org.jitsi.videobridge.util.TaskPools
import org.jitsi.xmpp.extensions.jingle.CandidatePacketExtension
import org.jitsi.xmpp.extensions.jingle.IceUdpTransportPacketExtension
import org.jitsi.xmpp.extensions.jingle.RtcpmuxPacketExtension
import java.beans.PropertyChangeEvent
import java.io.IOException
import java.net.DatagramPacket
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

typealias IceTransportDataHandler = (DatagramPacket) -> Unit

class IceTransportStandalone(
    endpointId: String,
    controlling: Boolean,
    parentLogger: Logger
) : org.jitsi.videobridge.transport.Transport {
    /**
     * The handler which will be invoked when data is received.  The handler
     * does *not* own the given DatagramPacket, nor the buffer held within.
     * NOTE: we keep IceTransport on the [DatagramPacket] so logic elsewhere
     * can determine SRTP or DTLS and, in the case of SRTP, copy the data into
     * a buffer with room left before and after the actual data here.
     */
    @JvmField
    var dataHandler: IceTransportDataHandler? = null

    private val closed = AtomicBoolean(false)
    private val iceConnected = AtomicBoolean(false)

    private val logger = createChildLogger(parentLogger)

    override var state: TransportState = TransportState.UNITITALIZED
        private set

    @JvmField
    var eventHandler: IceTransportEventHandler? = null

    private val iceAgent = Agent(Config.ufragPrefix(), logger).apply {
        appendHarvesters(this)
        isControlling = controlling
        performConsentFreshness = true
        addStateChangeListener(this@IceTransportStandalone::iceStateChange)
    }.also {
        logger.addContext("local_ufrag", it.localUfrag)
    }

    //TODO: I think endpointId is no longer needed here since we've got logContext
    private val iceStream = iceAgent.createMediaStream("stream-$endpointId").apply {
        addPairChangeListener(this@IceTransportStandalone::iceStreamPairChange)
    }

    private val iceComponent = iceAgent.createComponent(
        iceStream,
        Transport.UDP,
        -1,
        -1,
        -1,
        Config.keepAliveStrategy(),
        Config.useComponentSocket()
    )

    //TODO: do we need to fire thee EventAdmin transportCreated event?

    fun getIcePassword(): String = requires(!closed.get()) {
        return iceAgent.localPassword
    }

    fun startConnectivityEstablishment(transportPacketExtension: IceUdpTransportPacketExtension) = requires(!closed.get()) {
        logger.cdebug { "Starting ICE connectivity establishment" }
        if (iceAgent.state.isEstablished) {
            logger.cdebug { "Connection already established" }
            return
        }

        // Set the remote ufrag/password
        iceStream.remoteUfrag = transportPacketExtension.ufrag
        iceStream.remotePassword = transportPacketExtension.password

        // If ICE is running already, we try to update the checklists with the
        // candidates. Note that this is a best effort.
        val iceAgentStateIsRunning = IceProcessingState.RUNNING == iceAgent.state

        val remoteCandidates = transportPacketExtension.getChildExtensionsOfType(CandidatePacketExtension::class.java)
        if (iceAgentStateIsRunning && remoteCandidates.isEmpty()) {
            logger.cdebug { "Ignoring transport extensions with no candidates, " +
                    "the Agent is already running." }
            return
        }

        val remoteCandidateCount = addRemoteCandidates(remoteCandidates, iceAgentStateIsRunning)
        if (iceAgentStateIsRunning) {
            when (remoteCandidateCount) {
                0 -> {
                    // XXX Effectively, the check above but realizing that all
                    // candidates were ignored:
                    // iceAgentStateIsRunning && candidates.isEmpty().
                }
                else -> iceComponent.updateRemoteCandidates()
            }
        } else if (remoteCandidateCount != 0) {
            // Once again, because the ICE Agent does not support adding
            // candidates after the connectivity establishment has been started
            // and because multiple transport-info JingleIQs may be used to send
            // the whole set of transport candidates from the remote peer to the
            // local peer, do not really start the connectivity establishment
            // until we have at least one remote candidate per ICE Component.
            if (iceComponent.remoteCandidateCount > 0) {
                logger.info("Starting the agent with remote candidates.")
                state = TransportState.PENDING
                iceAgent.startConnectivityEstablishment()
            }
        } else if (iceStream.remoteUfragAndPasswordKnown()) {
            // We don't have any remote candidates, but we already know the
            // remote ufrag and password, so we can start ICE.
            logger.info("Starting the Agent without remote candidates.")
            state = TransportState.PENDING
            iceAgent.startConnectivityEstablishment()
        } else {
            logger.cdebug { "Not starting ICE, no ufrag and pwd yet. ${transportPacketExtension.toXML()}" }
        }
    }

    fun describe(pe: IceUdpTransportPacketExtension) = requires(!closed.get()) {
        with(pe) {
            password = iceAgent.localPassword
            ufrag = iceAgent.localUfrag
            iceComponent.localCandidates?.forEach { pe.addChildExtension(it.toCandidatePacketExtension()) }
            addChildExtension(RtcpmuxPacketExtension())
            //TODO: caller must add the websocket url
        }
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            iceStream.removePairStateChangeListener(this::iceStreamPairChange)
            iceAgent.removeStateChangeListener(this::iceStateChange)
            iceAgent.free()
            state = TransportState.CLOSED
        }
    }

    fun startReadingData(): Unit = requires(!closed.get()) {
        logger.cdebug { "Starting to read incoming data" }
        val socket = iceComponent.socket
        TaskPools.IO_POOL.submit {
            val receiveBuf = ByteBufferPool.getBuffer(1500)
            val packet = DatagramPacket(receiveBuf, 0, receiveBuf.size)

            while (!closed.get()) {
                try {
                    socket.receive(packet)
                } catch (e: SocketClosedException) {
                    logger.info("Socket closed, stopping reader")
                    break
                } catch (e: IOException) {
                    logger.warn("Stopping reader", e)
                    break
                }
                dataHandler?.let {
                    logger.cdebug { "Forwarding received data to handler" }
                    it.invoke(packet)
                } ?: run {
                    logger.cdebug { "Data handler is null, dropping data" }
                }
            }
        }
    }

    fun send(packet: DatagramPacket) = requires(!closed.get()) {
        logger.cdebug { "Sending ICE data" }
        iceComponent.socket.send(packet)
    }

    private fun iceStateChange(ev: PropertyChangeEvent) {
        val oldState = ev.oldValue as IceProcessingState
        val newState = ev.newValue as IceProcessingState
        val transition = IceProcessingStateTransition(oldState, newState)

        logger.info("ICE state changed old=$oldState new=$newState")

        when {
            transition.completed() ->  {
                if (iceConnected.compareAndSet(false, true)) {
                    state = TransportState.CONNECTED
                    eventHandler?.iceConnected()
                }
            }
            transition.failed() -> {
                state = TransportState.FAILED
                eventHandler?.iceFailed()
            }
        }
    }

    private fun iceStreamPairChange(ev: PropertyChangeEvent) {
        if (IceMediaStream.PROPERTY_PAIR_CONSENT_FRESHNESS_CHANGED == ev.propertyName) {
            /* TODO: Currently ice4j only triggers this event for the selected
             * pair, but should we double-check the pair anyway?
             */
            val time = Instant.ofEpochMilli(ev.newValue as Long)
            eventHandler?.iceConsentUpdated(time)
        }
    }

    private fun addRemoteCandidates(
        candidates: List<CandidatePacketExtension>,
        iceAgentStateIsRunning: Boolean
    ): Int {
        var remoteCandidateCount = 0
        // Sort the remote candidates (host < reflexive < relayed) in order to
        // create first the host, then the reflexive, the relayed candidates and
        // thus be able to set the relative-candidate matching the
        // rel-addr/rel-port attribute.
        candidates.sorted().forEach { candidate ->
            // Is the remote candidate from the current generation of the
            // iceAgent?
            if (candidate.generation != iceAgent.generation) {
                return@forEach
            }

            val component = iceStream.getComponent(candidate.component)
            val relatedCandidate = candidate.getRelatedAddress()?.let {
                component.findRemoteCandidate(it)
            }
            val remoteCandidate = RemoteCandidate(
                TransportAddress(candidate.ip, candidate.port, Transport.parse(candidate.protocol)),
                component,
                CandidateType.parse(candidate.type.toString()),
                candidate.foundation,
                candidate.priority.toLong(),
                relatedCandidate
            )

            // XXX IceTransport harvests host candidates only and the
            // ICE Components utilize the UDP protocol/transport only at the
            // time of this writing. The ice4j library will, of course, check
            // the theoretical reachability between the local and the remote
            // candidates. However, we would like (1) to not mess with a
            // possibly running iceAgent and (2) to return a consistent return
            // value.
            if (!TransportUtils.canReach(component, remoteCandidate)) {
                return@forEach
            }
            when (iceAgentStateIsRunning) {
                true -> component.addUpdateRemoteCandidates(remoteCandidate)
                false -> component.addRemoteCandidate(remoteCandidate)
            }
            remoteCandidateCount++
        }
        return remoteCandidateCount
    }

    private fun generateCandidateId(candidate: LocalCandidate): String = buildString {
        append(java.lang.Long.toHexString(hashCode().toLong()))
        append(java.lang.Long.toHexString(candidate.parentComponent.parentStream.parentAgent.hashCode().toLong()))
        append(java.lang.Long.toHexString(candidate.parentComponent.parentStream.parentAgent.generation.toLong()))
        append(java.lang.Long.toHexString(candidate.hashCode().toLong()))
    }

    companion object {
        fun appendHarvesters(iceAgent: Agent) {
            Harvesters.initializeStaticConfiguration()
            Harvesters.tcpHarvester?.let {
                iceAgent.addCandidateHarvester(it)
            }
            Harvesters.singlePortHarvesters?.forEach(iceAgent::addCandidateHarvester)

            iceAgent.setUseHostHarvester(false)
        }
    }

    private fun CandidatePacketExtension.getRelatedAddress(): TransportAddress? {
        return if (relAddr != null && relPort != -1) {
            TransportAddress(relAddr, relPort, Transport.parse(protocol))
        } else null
    }

    private fun LocalCandidate.toCandidatePacketExtension(): CandidatePacketExtension {
        val cpe = CandidatePacketExtension()
        cpe.component = parentComponent.componentID
        cpe.foundation = foundation
        cpe.generation = parentComponent.parentStream.parentAgent.generation
        cpe.id = generateCandidateId(this)
        cpe.network = 0
        cpe.setPriority(priority)

        // Advertise 'tcp' candidates for which SSL is enabled as 'ssltcp'
        // (although internally their transport protocol remains "tcp")
        cpe.protocol = if (transport == Transport.TCP && isSSL) {
            Transport.SSLTCP.toString()
        } else {
            transport.toString()
        }
        if (transport.isTcpType()) {
            cpe.tcpType = tcpType.toString()
        }
        cpe.type = org.jitsi.xmpp.extensions.jingle.CandidateType.valueOf(type.toString())
        cpe.ip = transportAddress.hostAddress
        cpe.port = transportAddress.port

        relatedAddress?.let {
            cpe.relAddr = it.hostAddress
            cpe.relPort = it.port
        }

        return cpe
    }

    private fun Transport.isTcpType(): Boolean = this == Transport.TCP || this == Transport.SSLTCP

    private fun IceMediaStream.remoteUfragAndPasswordKnown(): Boolean =
        remoteUfrag != null && remotePassword != null


    private data class IceProcessingStateTransition(
        val oldState: IceProcessingState,
        val newState: IceProcessingState
    ) {
        fun completed(): Boolean = newState == IceProcessingState.COMPLETED

        fun failed(): Boolean {
            return newState == IceProcessingState.FAILED ||
                    (oldState == IceProcessingState.RUNNING && newState == IceProcessingState.FAILED)
        }
    }

    class Stats {
        var numPacketsReceived = 0
    }

    interface IceTransportEventHandler {
        fun iceConnected()
        fun iceFailed()
        fun iceConsentUpdated(time: Instant)
    }
}
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

package org.jitsi.videobridge.sctp

import org.jitsi.nlj.PacketInfo
import org.jitsi.rtp.UnparsedPacket
import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.cdebug
import org.jitsi.utils.logging2.createChildLogger
import org.jitsi.videobridge.dtls.Length
import org.jitsi.videobridge.dtls.Offset
import org.jitsi_modified.sctp4j.SctpDataCallback
import org.jitsi_modified.sctp4j.SctpDataSender
import org.jitsi_modified.sctp4j.SctpServerSocket
import org.jitsi_modified.sctp4j.SctpSocket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

typealias OutgoingSctpDataSender = (ByteArray, Offset, Length) -> Unit
typealias Sid = Int
typealias Ssn = Int
typealias Tsn = Int
typealias Ppid = Long
typealias Context = Int
typealias Flags = Int
typealias IncomingSctpTransportDataHandler = (ByteArray, Sid, Ssn, Tsn, Ppid, Context, Flags) -> Unit

//TODO: need to handle caching the incoming sctp packets before sctp
// connection is set up.  Here? Or something separate outside of it?
// Something separate would be best, I think (a wrapper--maybe even one that
// can auto 'remove' itself once the connection is set up?
class SctpTransportStandalone(
    parentLogger: Logger
) {
    private val logger = createChildLogger(parentLogger)

    private val sctpManager = SctpManager(SctpDataSender { data, offset, length ->
        dataSender?.invoke(data, offset, length)
        0
    }, logger)

    private var socket: SctpSocket? = null

    private val closed = AtomicBoolean(false)

    /**
     * The function [SctpTransportStandalone] will use when it wants to send
     * data out on the network
     */
    @JvmField
    var dataSender: OutgoingSctpDataSender? = null

    @JvmField
    var incomingDataHandler: IncomingSctpTransportDataHandler? = null

    @JvmField
    var eventHandler: SctpTransportEventHandler? = null

    fun dataReceived(data: ByteArray, off: Int, len: Int) {
        //TODO: change the API of SctpManager...right now it's oriented around
        // packet info and pipelines (even though it isn't involved in a pipeline
        // directly).  Ideally the stack would look more like the transports:
        // have methods to take data in and callbacks for output data, so we could
        // pass data in here and hook the stack's output callback to our output
        // callback
        val packetInfo = PacketInfo(UnparsedPacket(data, off, len))
        sctpManager.handleIncomingSctp(packetInfo)
    }

    fun actAsSctpServer() {
        //TODO: it's awkward that both we and sctpmanager 'own' the socket.
        // we should remove the ownership from sctpmanager or something.
        socket = sctpManager.createServerSocket().apply {
            eventHandler = object : SctpSocket.SctpSocketEventHandler {
                override fun onReady() {
                    this@SctpTransportStandalone.eventHandler?.connected()
                }

                override fun onDisconnected() {
                    this@SctpTransportStandalone.eventHandler?.disconnected()
                }
            }
            dataCallback = SctpDataCallback { data, sid, ssn, tsn, ppid, context, flags ->
                incomingDataHandler?.invoke(data, sid, ssn, tsn, ppid, context, flags)
            }
            listen()
        }
    }

    fun connect() {
        logger.info("Starting SCTP connection")
        socket?.let { nonNullSocket ->
            when (nonNullSocket) {
                // In the future we may support functioning as the client as
                //  well
                is SctpServerSocket -> {
                    repeat(100) {
                        if (nonNullSocket.accept()) {
                            logger.cdebug { "SCTP transport accepted connection" }
                            return@let
                        }
                        Thread.sleep(100)
                    }
                    logger.error("SCTP transport unable to accept connection")
                    // TODO: fire event?
                }
            }
        } ?: run {
            logger.error("Can't connect SCTP transport before role was set")
        }
    }

    fun send(data: ByteBuffer, ordered: Boolean, sid: Int, ppid: Int) {
        socket?.send(data, ordered, sid, ppid)
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            sctpManager.closeConnection()
            socket = null
        }
    }

    interface SctpTransportEventHandler {
        fun connected()
        fun disconnected()
    }
}
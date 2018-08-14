/*
 * Copyright @ 2018 Atlassian Pty Ltd
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
package org.jitsi.nlj

import org.jitsi.nlj.srtp_og.SinglePacketTransformer
import org.jitsi.nlj.transform.chain
import org.jitsi.nlj.transform.module.Module
import org.jitsi.nlj.transform.module.ModuleChain
import org.jitsi.nlj.transform.module.PacketHandler
import org.jitsi.nlj.transform.module.PacketWriter
import org.jitsi.nlj.transform.module.getMbps
import org.jitsi.nlj.transform.module.incoming.SrtpTransformerWrapperDecrypt
import org.jitsi.nlj.transform.packetPath
import org.jitsi.rtp.Packet
import org.jitsi.rtp.RtpPacket
import org.jitsi.rtp.SrtcpPacket
import org.jitsi.rtp.SrtpPacket
import org.jitsi.rtp.SrtpProtocolPacket
import org.jitsi.rtp.util.RtpProtocol
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

class RtpReceiverImpl @JvmOverloads constructor(
    val id: Long,
    val executor: ExecutorService = Executors.newSingleThreadExecutor(),
    val packetHandler: PacketHandler? = null
) : RtpReceiver() {
    /*private*/ override val moduleChain: ModuleChain
    private val incomingPacketQueue = LinkedBlockingQueue<Packet>()
    var running = true
    private val decryptWrapper = SrtpTransformerWrapperDecrypt()

    var firstPacketWrittenTime: Long = 0
    var lastPacketWrittenTime: Long = 0
    var bytesReceived: Long = 0
    var packetsReceived: Long = 0

    init {
        moduleChain = chain {
            name("SRTP chain")
            addModule(object : Module("SRTP protocol parser") {
                override fun doProcessPackets(p: List<Packet>) {
                    next(p.map(Packet::buf).map(::SrtpProtocolPacket))
                }
            })
            demux {
                name = "SRTP/SRTCP demuxer"
                packetPath {
                    predicate = { pkt -> RtpProtocol.isRtp(pkt.buf) }
                    path = chain {
                        addModule(object : Module("SRTP parser") {
                            override fun doProcessPackets(p: List<Packet>) {
                                next(p.map(Packet::buf).map(::SrtpPacket))
                            }
                        })
                        addModule(decryptWrapper)
                        addModule(object : Module("vp8 filter") {
                            override fun doProcessPackets(p: List<Packet>) {
                                val outpackets = p.map { it as RtpPacket}
                                    .filter { it.header.payloadType == 100}
                                    .toCollection(ArrayList())
                                next(outpackets)
                            }
                        })
                        addModule(PacketWriter())
                    }
                }
                packetPath {
                    predicate = { pkt -> RtpProtocol.isRtcp(pkt.buf) }
                    path = chain {
                        addModule(object : Module("SRTCP parser") {
                            override fun doProcessPackets(p: List<Packet>) {
                                println("BRIAN: got srtcp")
                                next(p.map(Packet::buf).map(::SrtcpPacket))
                            }
                        })
                    }
                }
            }
        }
        scheduleWork()
    }

    private fun scheduleWork() {
        // Rescheduling this job after reading a single packet to allow
        // other threads to run doesn't seem  to scale all that well,
        // but doing this in a while (true) loop
        // holds a single thread exclusively, making it impossible to play
        // with things like sharing threads across tracks.  Processing a
        // max amount of packets at a time seems to work as a nice
        // compromise between the two.  It would be nice to be able to
        // avoid the busy-loop style polling for a new packet though
        //TODO: use drainTo (?)
        executor.execute {
            val packets = mutableListOf<Packet>()
            while (packets.size < 5) {
                val packet = incomingPacketQueue.poll() ?: break
                packets += packet
            }
            if (packets.isNotEmpty()) processPackets(packets)
            if (running) {
                scheduleWork()
            }
        }
    }

    override fun processPackets(pkts: List<Packet>) = moduleChain.processPackets(pkts)

    override fun getStats(): String {
        return with (StringBuffer()) {
            appendln("RTP Receiver $id")
            appendln("queue size: ${incomingPacketQueue.size}")
            appendln("Received $bytesReceived bytes in ${lastPacketWrittenTime - firstPacketWrittenTime}ms (${getMbps(bytesReceived, Duration.ofMillis(lastPacketWrittenTime - firstPacketWrittenTime))} mbps)")
            append(moduleChain.getStats())
            toString()
        }
    }

    override fun enqueuePacket(p: Packet) {
        println("BRIAN: RtpReceiver enqueing packet of size ${p.size}")
        incomingPacketQueue.add(p)
        bytesReceived += p.size
        packetsReceived++
        if (firstPacketWrittenTime == 0L) {
            firstPacketWrittenTime = System.currentTimeMillis()
        }
        lastPacketWrittenTime = System.currentTimeMillis()
        if (packetsReceived % 200 == 0L) {
            println("BRIAN: module chain stats: ${moduleChain.getStats()}")

        }
    }

    override fun setSrtpTransformer(srtpTransformer: SinglePacketTransformer) {
        decryptWrapper.srtpTransformer = srtpTransformer
    }
}

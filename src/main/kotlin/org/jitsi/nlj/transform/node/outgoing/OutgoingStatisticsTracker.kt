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
package org.jitsi.nlj.transform.node.outgoing

import org.jitsi.nlj.PacketInfo
import org.jitsi.nlj.stats.PacketStreamStats
import org.jitsi.nlj.transform.node.ObserverNode
import org.jitsi.rtp.rtp.RtpPacket
import java.util.concurrent.ConcurrentHashMap

class OutgoingStatisticsTracker : ObserverNode("Outgoing statistics tracker") {
    /**
     * Per-SSRC statistics
     */
    private val ssrcStats: MutableMap<Long, OutgoingSsrcStats> = ConcurrentHashMap()
    /**
     * The stats for all of the SSRCs combined.
     */
    private val combinedStats = PacketStreamStats()

    override fun observe(packetInfo: PacketInfo) {
        combinedStats.update(packetInfo.packet.length)

        (packetInfo.packet as? RtpPacket) ?.let { rtpPacket ->
            val stats = ssrcStats.computeIfAbsent(rtpPacket.ssrc) {
                OutgoingSsrcStats(rtpPacket.ssrc)
            }
            stats.packetSent(rtpPacket.length, rtpPacket.timestamp)
        }
    }

    fun getSnapshot(): OutgoingStatisticsSnapshot {
        return OutgoingStatisticsSnapshot(
            combinedStats.snapshot(),
            ssrcStats.map { (ssrc, stats) ->
                Pair(ssrc, stats.getSnapshot())
            }.toMap()
        )
    }

    /**
     * Gets the combined stats (i.e. overall stats for sent packets).
     */
    fun getCombinedStatsSnapshot() = combinedStats.snapshot()

    fun getSsrcSnapshot(ssrc: Long): OutgoingSsrcStats.Snapshot? {
        return ssrcStats[ssrc]?.getSnapshot()
    }
}

class OutgoingStatisticsSnapshot(
    val combinedStats: PacketStreamStats.Snapshot,
    /**
     * Per-ssrc stats.
     */
    val ssrcStats: Map<Long, OutgoingSsrcStats.Snapshot>?
)

class OutgoingSsrcStats(
    private val ssrc: Long
) {
    private var statsLock = Any()
    // Start variables protected by statsLock
    private var packetCount: Int = 0
    private var octetCount: Int = 0
    private var mostRecentRtpTimestamp: Long = 0
    // End variables protected by statsLock

    fun packetSent(packetSizeOctets: Int, rtpTimestamp: Long) {
        synchronized(statsLock) {
            packetCount++
            octetCount += packetSizeOctets
            mostRecentRtpTimestamp = rtpTimestamp
        }
    }

    fun getSnapshot(): Snapshot {
        synchronized(statsLock) {
            return Snapshot(packetCount, octetCount, mostRecentRtpTimestamp)
        }
    }

    data class Snapshot(
        val packetCount: Int,
        val octetCount: Int,
        val mostRecentRtpTimestamp: Long
    )
}

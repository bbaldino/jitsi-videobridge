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

package org.jitsi.nlj.transform.node.outgoing

import io.kotlintest.IsolationMode
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec
import org.jitsi.nlj.resources.srtp_samples.SrtpSample
import org.jitsi.nlj.srtp.SrtpUtil
import org.jitsi.nlj.test_utils.matchers.haveSameContentAs
import org.jitsi.rtp.rtcp.rtcpfb.RtcpFbNackPacket
import org.jitsi.rtp.srtcp.AuthenticatedSrtcpPacket

internal class SrtcpTransformerEncryptNodeTest : ShouldSpec() {
    override fun isolationMode(): IsolationMode? = IsolationMode.InstancePerLeaf

    private val srtcpTransformer = SrtpUtil.initializeTransformer(
            SrtpSample.srtpProfileInformation,
            SrtpSample.keyingMaterial.array(),
            SrtpSample.tlsRole,
            true
    )

    init {
        "encrypting a packet" {
            "created from a buffer" {
                val encryptedPacket = srtcpTransformer.transform(SrtpSample.outgoingUnencryptedRtcpPacket)
                should("encrypt the data correctly") {
                    encryptedPacket.getBuffer() should haveSameContentAs(SrtpSample.expectedEncryptedRtcpData)
                }
            }
            "created from values" {
                val packet =
                    RtcpFbNackPacket.fromValues(mediaSourceSsrc = 123, missingSeqNums = (10..20 step 2).toSortedSet())
                packet.getBuffer()
                val encryptedPacket = srtcpTransformer.transform(packet.clone())
                should("result in all header fields being correct") {
                    encryptedPacket as AuthenticatedSrtcpPacket
                    encryptedPacket.header.length shouldBe packet.header.length
                }
            }
        }
    }
}
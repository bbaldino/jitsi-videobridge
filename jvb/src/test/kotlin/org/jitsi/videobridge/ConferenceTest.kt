/*
 * Copyright @ 2020 - Present, 8x8, Inc.
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
package org.jitsi.videobridge

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.jitsi.ConfigTest
import org.jitsi.videobridge.conference.ConferenceK
import org.jitsi.videobridge.octo.singleton as octoRelayServiceProvider
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.jxmpp.jid.impl.JidCreate
import kotlin.random.Random

/**
 * This is a high-level test for [Conference] and related functionality.
 */
class ConferenceTest : ConfigTest() {
    private val videobridge = mockk<Videobridge>()

    init {
        val name = JidCreate.entityBareFrom("roomName@somedomain.com")
        withNewConfig(newConfigOctoEnabled(), loadDefaults = true) {
            octoRelayServiceProvider().get()?.start()
        }

        context("Adding local endpoints should work") {
            with(ConferenceK(videobridge, "id", name, false, Conference.GID_NOT_SET)) {
                endpointCount shouldBe 0
                createLocalEndpoint("abcdabcd", true)
                endpointCount shouldBe 1
                debugState.shouldBeValidJson()
            }
        }
        context("Enabling octo should fail when the GID is not set") {
            with(ConferenceK(videobridge, "id", name, false, Conference.GID_NOT_SET)) {
                isOctoEnabled shouldBe false
                shouldThrow<IllegalStateException> {
                    getTentacle()
                }
                debugState.shouldBeValidJson()
            }
        }
        context("Enabling octo should work") {
            with(ConferenceK(videobridge, "id", name, false, 1234)) {
                isOctoEnabled shouldBe false
                getTentacle()
                isOctoEnabled shouldBe true
                getTentacle().setRelays(listOf("127.0.0.1:4097"))

                debugState.shouldBeValidJson()
            }
        }
    }
}

private fun newConfigOctoEnabled(port: Int = Random.nextInt(10000, 65535)) = """
    videobridge {
        octo {
            enabled = true
            bind-address = 127.0.0.1
            bind-port = $port
        }
    }
""".trimMargin()

fun JSONObject.shouldBeValidJson() {
    JSONParser().parse(this.toJSONString())
}

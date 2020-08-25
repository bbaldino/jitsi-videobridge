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

package org.jitsi.videobridge

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class VideobridgeTest : ShouldSpec({
    isolationMode = IsolationMode.InstancePerLeaf

    val videobridge = Videobridge()

    context("when an event handler is added") {
        println("a")
        val createdConfs = mutableListOf<Conference>()
        val expiredConfs = mutableListOf<Conference>()
        val handler = object : Videobridge.EventHandler {
            override fun conferenceCreated(conference: Conference) {
                createdConfs += conference
            }

            override fun conferenceExpired(conference: Conference) {
                expiredConfs += conference
            }
        }
        videobridge.addHandler(handler)
        context("and a conference is created") {
            println("b")
            videobridge.createConference("conf_name", true)
            should("fire an event") {
                println("c")
                createdConfs shouldHaveSize 1
                createdConfs.first().name shouldBe "conf_name"
            }
            context("and then expired") {
                println("d")
                videobridge.expireConference(createdConfs.first())
                should("fire an event") {
                    println("e")
                    expiredConfs shouldHaveSize 1
                    expiredConfs.first().name shouldBe "conf_name"
                }
            }
        }
    }
})

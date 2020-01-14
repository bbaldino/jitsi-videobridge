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

package org.jitsi.videobridge.stats.config

import com.typesafe.config.ConfigFactory
import io.kotlintest.inspectors.forOne
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.shouldBe
import org.jitsi.config.MockConfigSource
import org.jitsi.config.TypesafeConfigSource
import org.jitsi.utils.config.ConfigSource
import org.jitsi.videobridge.JitsiConfigTest
import org.jitsi.videobridge.testutils.resetSingleton
import org.jxmpp.jid.impl.JidCreate
import java.util.Properties
import org.jitsi.videobridge.stats.config.StatsManagerBundleActivatorConfig.Config.Companion as Config

class StatsManagerBundleActivatorConfigTest : JitsiConfigTest() {

    init {
        "When only new config contains stats transport config" {
            val legacyConfig = createConfigFrom(Properties().apply {
                setProperty("org.jitsi.videobridge.some_other_prop=", "42")
            })
            withLegacyConfig(legacyConfig)
            "A stats transport config" {
                "with a multiple, valid stats transport configured" {
                    val config = createConfigFrom(
                        """
                        videobridge {
                            stats {
                                enabled=true
                                transports = [
                                    {
                                        type="colibri"
                                    },
                                    {
                                        type="muc"
                                    },
                                    {
                                        type="callstatsio"
                                    },
                                    {
                                        type="pubsub"
                                        service="meet.jit.si"
                                        node="jvb"
                                    }
                                ]
                            }
                        }
                        """.trimIndent()
                    )
                    should("parse the transport correctly") {
                        withNewConfig(config)
                        val cfg = Config.StatsTransportsProperty()

                        cfg.value shouldHaveSize 4
                        cfg.value.forOne { it as StatsTransportConfig.ColibriStatsTransportConfig }
                        cfg.value.forOne { it as StatsTransportConfig.MucStatsTransportConfig }
                        cfg.value.forOne { it as StatsTransportConfig.CallStatsIoStatsTransportConfig }
                        cfg.value.forOne {
                            it as StatsTransportConfig.PubSubStatsTransportConfig
                            it.service shouldBe JidCreate.from("meet.jit.si")
                            it.node shouldBe "jvb"
                        }
                    }
                }
                "with an invalid stats transport configured" {
                    val config = createConfigFrom(
                        """
                        videobridge {
                            stats {
                                enabled=true
                                transports = [
                                    {
                                        type="invalid"
                                    },
                                    {
                                        type="muc"
                                    },
                                ]
                            }
                        }
                        """.trimIndent()
                    )
                    should("ignore the invalid config and parse the valid transport correctly") {
                        withNewConfig(config)
                        resetEnabledProperty()
                        println("Setting new config with invalid transport")
                        val cfg = Config.StatsTransportsProperty()

                        cfg.value shouldHaveSize 1
                        cfg.value.forOne { it as StatsTransportConfig.MucStatsTransportConfig }
                    }
                }
            }
        }
        "When old and new config contain stats transport config" {
            val legacyConfig = createConfigFrom(Properties().apply {
                setProperty("org.jitsi.videobridge.ENABLE_STATISTICS", "true")
                setProperty("org.jitsi.videobridge.STATISTICS_TRANSPORT", "muc,colibri,callstats.io,pubsub")
                setProperty("org.jitsi.videobridge.PUBSUB_SERVICE", "meet.jit.si")
                setProperty("org.jitsi.videobridge.PUBSUB_NODE", "jvb")
            })
            withLegacyConfig(legacyConfig)
            withNewConfig(MockConfigSource("mock", mapOf()))
            val cfg = Config.StatsTransportsProperty()

            cfg.value shouldHaveSize 4
            cfg.value.forOne { it as StatsTransportConfig.ColibriStatsTransportConfig }
            cfg.value.forOne { it as StatsTransportConfig.MucStatsTransportConfig }
            cfg.value.forOne { it as StatsTransportConfig.CallStatsIoStatsTransportConfig }
            cfg.value.forOne {
                it as StatsTransportConfig.PubSubStatsTransportConfig
                it.service shouldBe JidCreate.from("meet.jit.si")
                it.node shouldBe "jvb"
            }
        }
    }

    private fun createConfigFrom(configString: String): ConfigSource =
        TypesafeConfigSource("testConfig") { ConfigFactory.parseString(configString) }

    private fun createConfigFrom(configProps: Properties): ConfigSource =
        TypesafeConfigSource("testConfig") { ConfigFactory.parseProperties(configProps) }

    private fun resetEnabledProperty() {
        resetSingleton(
            "enabledProp",
            Config
        )
    }
}

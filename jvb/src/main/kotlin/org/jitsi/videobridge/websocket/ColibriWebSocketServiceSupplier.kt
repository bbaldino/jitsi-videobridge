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

package org.jitsi.videobridge.websocket

import java.util.concurrent.atomic.AtomicReference

/**
 * This works slightly differently than other service suppliers because we require
 * a parameter to instantiate it, so the set has to be done before it's accessed
 * (just as before, but different than other suppliers)
 */
class ColibriWebSocketServiceSupplier {
    private val colibriWebSocketService = AtomicReference<ColibriWebSocketService?>(null)

    fun setColibriWebsocketService(colibriWebSocketService: ColibriWebSocketService) {
        this.colibriWebSocketService.compareAndSet(null, colibriWebSocketService)
    }

    fun get(): ColibriWebSocketService? = colibriWebSocketService.get()

    companion object {
        @JvmField
        public val singleton = ColibriWebSocketServiceSupplier()
    }
}

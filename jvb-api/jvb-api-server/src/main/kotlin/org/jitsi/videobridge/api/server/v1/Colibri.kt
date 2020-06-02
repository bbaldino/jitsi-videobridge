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

package org.jitsi.videobridge.api.server.v1

import io.ktor.application.call
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import org.jitsi.videobridge.api.server.confManager
import org.jitsi.xmpp.extensions.colibri.ColibriConferenceIQ

/**
 * Handles incoming Colibri messages over HTTP
 */
fun Route.colibriApi() {
    route("colibri") {
        post {
            val req = call.receive<ColibriConferenceIQ>()
            val result = call.confManager.handleColibriConferenceIQ(req)
            call.respond(result)
        }
    }
}

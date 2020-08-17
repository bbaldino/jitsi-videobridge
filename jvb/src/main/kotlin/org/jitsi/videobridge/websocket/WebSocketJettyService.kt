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

import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.HandlerList
import org.eclipse.jetty.servlet.ServletContextHandler
import org.jitsi.rest.AbstractJettyBundleActivator
import org.jitsi.rest.AbstractJettyService

/**
 * Starts a jetty instance used for websockets.
 *
 * NOTE: although this is named around websockets, this is really just the 'public'
 * Jetty instance started by the bridge, and could be used for other things--if/when it is,
 * we should rename this to something more generic.
 */
class WebSocketJettyService : AbstractJettyService(
    JETTY_PROPERTY_PREFIX, "videobridge.http-servers.public"
) {
    override fun initializeHandlerList(server: Server): Handler {
        // XXX ServletContextHandler and/or ServletHandler are not cool because
        // they always mark HTTP Request as handled if it reaches a Servlet
        // regardless of whether the Servlet actually did anything.
        // Consequently, it is advisable to keep Servlets as the last Handler.
        return initializeServletHandler() ?: HandlerList()
    }

    private fun initializeServletHandler(): Handler? {
        val servletContextHandler = ServletContextHandler()

        val websocketService = ColibriWebSocketService(isTls())
        return websocketService.initializeColibriWebSocketServlet(servletContextHandler)?.let {
            ColibriWebSocketServiceSupplier.singleton.setColibriWebsocketService(websocketService)
            servletContextHandler.apply { contextPath = "/" }
        }
    }
}

/**
 * The prefix of the property names for the Jetty instance managed by
 * this [AbstractJettyBundleActivator].
 */
private val JETTY_PROPERTY_PREFIX = "org.jitsi.videobridge.rest"

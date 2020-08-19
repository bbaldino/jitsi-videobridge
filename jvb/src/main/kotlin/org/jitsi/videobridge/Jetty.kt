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

import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.SecureRequestCustomizer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.ssl.SslContextFactory
import java.nio.file.Paths

fun createJettyServer(
    port: Int,
    host: String? = null
): Server {
    val server = Server().apply {
        handler = ServletContextHandler()
    }
    val connector = ServerConnector(server, HttpConnectionFactory(HttpConfiguration())).apply {
        this.port = port
        this.host = host
    }
    server.addConnector(connector)
    return server
}

fun createSecureJettyServer(
    port: Int,
    keyStorePath: String,
    host: String? = null,
    keyStorePassword: String? = null,
    needClientAuth: Boolean = false
): Server {
    val sslContextFactoryKeyStoreFile = Paths.get(keyStorePath).toFile()
    val sslContextFactory = SslContextFactory().apply {
        setIncludeProtocols("TLSv1.2")
        setIncludeCipherSuites(
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
        )
        isRenegotiationAllowed = false
        if (keyStorePassword != null) {
            setKeyStorePassword(keyStorePassword)
        }
        this.keyStorePath = sslContextFactoryKeyStoreFile.path
        this.needClientAuth = needClientAuth
    }
    val config = HttpConfiguration().apply {
        securePort = port
        secureScheme = "https"
        addCustomizer(SecureRequestCustomizer())
    }
    val server = Server().apply {
        handler = ServletContextHandler()
    }
    val connector = ServerConnector(
        server,
        SslConnectionFactory(
            sslContextFactory,
            "http/1.1"
        ),
        HttpConnectionFactory(config)
    ).apply {
        this.host = host
    }
    server.addConnector(connector)
    return server
}

val Server.servletContextHandler: ServletContextHandler?
    get() = handler as? ServletContextHandler

fun Server.addServlet(servlet: ServletHolder, pathSpec: String) {
    (handler as? ServletContextHandler)?.addServlet(servlet, pathSpec)
        ?: throw Exception("Server handler isn't ServletContextHandler, it's $handler")
}

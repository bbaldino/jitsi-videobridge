/*
 * Copyright @ 2015-2017 Atlassian Pty Ltd
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
package org.jitsi.videobridge.octo;

import com.typesafe.config.*;
import org.jitsi.utils.logging.*;
import org.jitsi.videobridge.util.*;
import org.osgi.framework.*;

import java.net.*;

/**
 * A {@link BundleActivator} for a bridge-to-bridge (Octo) relay.
 *
 * @author Boris Grozev
 */
public class OctoRelayService
    implements BundleActivator
{
    /**
     * The {@link Logger} used by the {@link OctoRelay} class and its
     * instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(OctoRelayService.class);

    /**
     * The Octo relay instance used by this {@link OctoRelayService}.
     */
    private OctoRelay relay;

    /**
     * @return the {@link OctoRelay} managed by this
     * {@link OctoRelayService}.
     */
    public OctoRelay getRelay()
    {
        return relay;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(BundleContext bundleContext)
    {
        Config octoConfig = JvbConfig.getConfig().getConfig("videobridge.octo");

        String address;
        String publicAddress;
        UnprivilegedPort port;
        try
        {
            address = octoConfig.getString("bind-address");
            publicAddress = octoConfig.getString("public-address");
            port = new UnprivilegedPort(octoConfig.getInt("port"));
        }
        catch (ConfigException.Missing ex)
        {
            logger.info("Octo relay not configured.");
            return;
        }
        catch (UnprivilegedPort.InvalidUnprivilegedPortException ex)
        {
            logger.error(ex.toString());
            return;
        }

        try
        {
            relay = new OctoRelay(address, port.get());
            relay.setPublicAddress(publicAddress);
            bundleContext
                    .registerService(OctoRelayService.class.getName(), this,
                            null);
        }
        catch (UnknownHostException | SocketException e)
        {
            logger.error("Failed to initialize Octo relay with address "
                             + address + ":" + port.get() + ". ", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop(BundleContext bundleContext) throws Exception
    {
        if (relay != null)
        {
            relay.stop();
        }
    }

}

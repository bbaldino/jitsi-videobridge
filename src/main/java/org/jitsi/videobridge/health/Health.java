/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.jitsi.videobridge.health;

import org.eclipse.jetty.server.*;
import org.ice4j.ice.harvest.*;
import org.jitsi.videobridge.*;
import org.jitsi.videobridge.transport.*;
import org.jitsi.videobridge.xmpp.*;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;

/**
 * Checks the health of {@link Videobridge}.
 *
 * @author Lyubomir Marinov
 */
public class Health
{
    /**
     * The pseudo-random generator used to generate random input for
     * {@link Videobridge} such as {@link Endpoint} IDs.
     */
    private static Random RANDOM = Videobridge.RANDOM;

    /**
     * Checks the health (status) of the {@link Videobridge} associated with a
     * specific {@link Conference}. The specified {@code conference} will be
     * used to perform the check i.e. for testing purposes.
     *
     * @param conferenceShim the {@code Conference} associated with the
     * {@code Videobridge} to check the health (status) of
     * @throws Exception if an error occurs while checking the health (status)
     * of the {@code videobridge} associated with {@code conference} or the
     * check determines that the {@code Videobridge} is not healthy 
     */
    private static void check(ColibriShim.ConferenceShim conferenceShim)
    {
        final int numEndpoints = 2;
        ArrayList<Endpoint> endpoints = new ArrayList<>(numEndpoints);

        for (int i = 0; i < numEndpoints; ++i)
        {
            Endpoint endpoint
                = (Endpoint) conferenceShim.getOrCreateEndpoint(generateEndpointID());

            // Fail as quickly as possible.
            if (endpoint == null)
            {
                throw new NullPointerException("Failed to create an endpoint.");
            }

            // Create and install the transport manager
            conferenceShim.getOrCreateChannelBundle(endpoint.getID());

            endpoints.add(endpoint);
            endpoints.add(endpoint);

            endpoint.createSctpConnection();
        }


        // TODO(brian): The below connection won't work with single port mode.  I think this is because both agent's
        // bind to the single port and we can't demux the ice packets correctly.  Forcing non-single port mode (via
        // hardcoding rtcpmux to false elsewhere) works, but causes other problems since we don't properly support
        // non-rtcpmux.

//        Endpoint ep0 = endpoints.get(0);
//        TransportManager ep0TransportManager =
//                conferenceShim.conference.getTransportManager(ep0.getID(), false, false);
//
//        Endpoint ep1 = endpoints.get(1);
//        TransportManager ep1TransportManager =
//                conferenceShim.conference.getTransportManager(ep1.getID(), false, false);
//
//        // Connect endpoint 0 to endpoint 1
//        ColibriConferenceIQ.ChannelBundle channelBundle0Iq = new ColibriConferenceIQ.ChannelBundle(ep0.getID());
//        ColibriShim.ChannelBundleShim channelBundle0Shim = conferenceShim.getChannelBundle(ep0.getID());
//        channelBundle0Shim.describe(channelBundle0Iq);
//        IceUdpTransportPacketExtension tpe = channelBundle0Iq.getTransport();
//        ep1TransportManager.startConnectivityEstablishment(channelBundle0Iq.getTransport());
//
//        // Connect endpoint 1 to endpoint 0
//        ColibriConferenceIQ.ChannelBundle channelBundle1Iq = new ColibriConferenceIQ.ChannelBundle(ep1.getID());
//        ColibriShim.ChannelBundleShim channelBundle1Shim = conferenceShim.getChannelBundle(ep1.getID());
//        channelBundle1Shim.describe(channelBundle1Iq);
//        ep0TransportManager.startConnectivityEstablishment(channelBundle1Iq.getTransport());
    }

    /**
     * Checks the health (status) of a specific {@link Videobridge}.
     *
     * @param videobridge the {@code Videobridge} to check the health (status)
     * of
     * @throws Exception if an error occurs while checking the health (status)
     * of {@code videobridge} or the check determines that {@code videobridge}
     * is not healthy 
     */
    public static void check(Videobridge videobridge)
        throws Exception
    {
        if (MappingCandidateHarvesters.stunDiscoveryFailed)
        {
            throw new Exception("Address discovery through STUN failed");
        }

        if (!Harvesters.healthy)
        {
            throw new Exception("Failed to bind single-port");
        }

        // Conference
        ColibriShim.ConferenceShim conferenceShim =
                videobridge.getColibriShim().createConference(null, null, null);
        System.out.println("TEMP: created conference " + conferenceShim.getId() + " from health check");

        // Fail as quickly as possible.
        if (conferenceShim == null || conferenceShim.conference == null)
        {
            throw new NullPointerException("Failed to create a conference");
        }
        else
        {
            try
            {
                check(conferenceShim);
            }
            finally
            {
                //TODO(brian)
//                conference.expire();
            }
        }
    }

    /**
     * Checks if given {@link Videobridge} has valid connection to XMPP server.
     *
     * @param videobridge the {@code Videobridge} to check the XMPP connection
     *                    status of
     * @return <tt>true</tt> if given videobridge has valid XMPP connection,
     *         also if it's not using XMPP api at all(does not have
     *         ComponentImpl). Otherwise <tt>false</tt> will be returned.
     */
    private static boolean checkXmppConnection(Videobridge videobridge)
    {
        // If XMPP API was requested, but there isn't any XMPP component
        // registered we shall return false(no valid XMPP connection)
        Collection<ComponentImpl> components = videobridge.getComponents();
        if (videobridge.isXmppApiEnabled() && components.size() == 0)
        {
            return false;
        }

        for(ComponentImpl component : components)
        {
            if(!component.isConnectionAlive())
                return false;
        }
        return true;
    }

    /**
     * Generates a pseudo-random {@code Endpoint} ID which is not guaranteed to
     * be unique.
     *
     * @return a pseudo-random {@code Endpoint} ID which is not guaranteed to be
     * unique
     */
    private static String generateEndpointID()
    {
        return Long.toHexString(System.currentTimeMillis() + RANDOM.nextLong());
    }

    /**
     * Gets a JSON representation of the health (status) of a specific
     * {@link Videobridge}.
     *
     * @param videobridge the {@code Videobridge} to get the health (status) of
     * in the form of a JSON representation
     * @param baseRequest the original unwrapped {@link Request} object
     * @param request the request either as the {@code Request} object or a
     * wrapper of that request
     * @param response the response either as the {@code Response} object or a
     * wrapper of that response
     * @throws IOException
     * @throws ServletException
     */
    public static void getJSON(
        Videobridge videobridge,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
               ServletException
    {
        int status;
        String reason = null;

        try
        {
            // Check XMPP connection status first
            if (checkXmppConnection(videobridge))
            {
                // Check if the videobridge is functional
                check(videobridge);
                status = HttpServletResponse.SC_OK;
            }
            else
            {
                status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                reason = "XMPP component connection failure.";
            }
        }
        catch (Exception ex)
        {
            if (ex instanceof IOException)
                throw (IOException) ex;
            else if (ex instanceof ServletException)
                throw (ServletException) ex;
            else
            {
                status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                reason = ex.getMessage();
            }
        }

        if (reason != null)
        {
            response.getOutputStream().println(reason);
        }
        response.setStatus(status);
    }
}

/*
 * Copyright @ 2015 - Present, 8x8 Inc
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
package org.jitsi.videobridge;

import org.jitsi.utils.concurrent.*;
import org.jitsi.utils.logging.*;
import org.jitsi.videobridge.util.*;
import org.osgi.framework.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Implements a <tt>Thread</tt> which expires the {@link AbstractEndpoint}s and
 * {@link Conference}s of a specific <tt>Videobridge</tt>.
 *
 * @author Lyubomir Marinov
 */
public class VideobridgeExpireThread
{
    /**
     * The <tt>Logger</tt> used by the <tt>VideobridgeExpireThread</tt> class
     * and its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(VideobridgeExpireThread.class);

    /**
     * The executor which periodically calls {@link #expire(Videobridge)} (if
     * this {@link VideobridgeExpireThread} has been started).
     */
    private static final RecurringRunnableExecutor EXECUTOR
        = new RecurringRunnableExecutor(
            VideobridgeExpireThread.class.getSimpleName());

    /**
     * The executor used to expire the individual {@link AbstractEndpoint}s
     * or {@link Conference}s.
     */
    private static final Executor EXPIRE_EXECUTOR
        = ExecutorUtils.newCachedThreadPool(
            true, VideobridgeExpireThread.class.getSimpleName() + "-channel");

    /**
     * The name of the property which specifies the interval in seconds at which
     * a {@link VideobridgeExpireThread} instance should run.
     */
    public static final String EXPIRE_CHECK_SLEEP_SEC
            = "org.jitsi.videobridge.EXPIRE_CHECK_SLEEP_SEC";
    /**
     * The default number of seconds of inactivity after which <tt>Channel</tt>s
     * expire.
     * NOTE(brian): move from Channel
     */
    public static final int DEFAULT_EXPIRE = 60;

    /**
     * The default value of the {@link #EXPIRE_CHECK_SLEEP_SEC} property.
     */
    private static final int EXPIRE_CHECK_SLEEP_SEC_DEFAULT =
            DEFAULT_EXPIRE;

    /**
     * The {@link PeriodicRunnable} registered with {@link #EXECUTOR} which is
     * to run the expire task for this {@link VideobridgeExpireThread} instance.
     */
    private PeriodicRunnable expireRunnable;

    /**
     * The {@link Videobridge} which has its {@link Conference}s expired by this
     * instance.
     */
    private Videobridge videobridge;

    /**
     * Initializes a new {@link VideobridgeExpireThread} instance which is to
     * expire the {@link Conference}s of a specific {@link Videobridge}.
     *
     * @param videobridge the {@link Videobridge} which is to have its
     * {@link Conference}s expired by the new instance.
     */
    public VideobridgeExpireThread(Videobridge videobridge)
    {
        this.videobridge = Objects.requireNonNull(videobridge);
    }

    /**
     * Starts this {@link VideobridgeExpireThread} in a specific
     * {@link BundleContext}.
     * @param bundleContext the <tt>BundleContext</tt> in which this
     * {@link VideobridgeExpireThread} is to start.
     */
    void start(final BundleContext bundleContext)
    {
        int expireCheckSleepSec =
                (int)JvbConfig.getConfig().getDuration("videobridge.expire-thread-interval").getSeconds();
        logger.info(
            "Starting with " + expireCheckSleepSec + " second interval.");

        expireRunnable = new PeriodicRunnable(expireCheckSleepSec * 1000)
        {
            @Override
            public void run()
            {
                super.run();

                Videobridge videobridge
                    = VideobridgeExpireThread.this.videobridge;
                if (videobridge != null)
                {
                    expire(videobridge);
                }

                // The current implementation of the executor fails with a
                // concurrent modification exception if we de-register from
                // the thread running run(). So we can not de-register here
                // if videobridge==null, and we will keep running until we get
                // explicitly stop()ed, which is fine.
            }
        };
        EXECUTOR.registerRecurringRunnable(expireRunnable);
    }

    /**
     * Stops this {@link VideobridgeExpireThread}.
     */
    void stop(final BundleContext bundleContext)
    {
        logger.info("Stopping.");
        if (expireRunnable != null)
        {
            EXECUTOR.deRegisterRecurringRunnable(expireRunnable);
        }
        expireRunnable = null;
        videobridge = null;
    }

    /**
     * Expires the {@link Conference}s and/or {@link Endpoint}s of a specific <tt>Videobridge</tt> if they
     * have been inactive for more than their advertised <tt>expire</tt> number
     * of seconds.
     *
     * @param videobridge the <tt>Videobridge</tt> which is to have its
     * <tt>Channel</tt>s expired if they have been inactive for more than their
     * advertised <tt>expire</tt> number of seconds
     */
    private void expire(Videobridge videobridge)
    {
        logger.info("Running expire()");
        for (Conference conference : videobridge.getConferences())
        {
            // The Conferences will live an iteration more than the Contents.
            if (conference.shouldExpire())
            {
                logger.info("Conference "
                        + conference.getID() + " should expire, expiring it");
                EXPIRE_EXECUTOR.execute(
                        () -> videobridge.expireConference(conference));
            }
            else
            {
                for (AbstractEndpoint endpoint : conference.getEndpoints())
                {
                    if (endpoint.shouldExpire())
                    {
                        logger.info("Expiring endpoint " + endpoint.getID());
                        EXPIRE_EXECUTOR.execute(endpoint::expire);
                    }
                }
            }
        }
    }
}

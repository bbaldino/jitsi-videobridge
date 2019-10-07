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

package org.jitsi.videobridge.health.config;

import org.jitsi.utils.collections.*;
import org.jitsi.videobridge.util.*;
import org.jitsi.videobridge.util.config.*;

import java.util.concurrent.*;

public class HealthIntervalProperty extends ReadOnceProperty<Integer>
{
    protected static final String legacyPropKey = "org.jitsi.videobridge.health.INTERVAL";
    protected static final String propKey = "videobridge.health.interval";

    private static HealthIntervalProperty singleton = new HealthIntervalProperty();

    protected HealthIntervalProperty()
    {
        super(JList.of(
            () -> JvbConfig.getLegacyConfig().getInt(legacyPropKey),
            () -> (int)JvbConfig.getConfig().getDuration(propKey, TimeUnit.MILLISECONDS)
        ));
    }

    public static HealthIntervalProperty getInstance()
    {
        return singleton;
    }
}

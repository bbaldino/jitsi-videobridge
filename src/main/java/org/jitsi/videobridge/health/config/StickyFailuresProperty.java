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

import com.typesafe.config.*;
import org.jitsi.videobridge.util.config.*;
import org.jitsi.videobridge.util.config.retriever.*;

public class StickyFailuresProperty
{
    protected static final String legacyPropKey = "org.jitsi.videobridge.health.STICKY_FAILURES";
    protected static final boolean legacyDefaultValue = false;
    protected static final String propKey = "videobridge.health.sticky-failures";

    private static ConfigProperty<Boolean> singleInstance = new ConfigPropertyBuilder<Boolean>()
            .usingGetter(Config::getBoolean)
            .fromConfigs(
                    new DefaultConfigValueRetrieverBuilder<>(propKey),
                    new DefaultLegacyConfigValueRetrieverBuilder<>(legacyPropKey)
            )
            .withDefault(legacyDefaultValue)
            .readOnce()
            .build();

    public static ConfigProperty<Boolean> getInstance()
    {
        return singleInstance;
    }
}

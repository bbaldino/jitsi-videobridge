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

package org.jitsi.videobridge.octo.config;

import com.typesafe.config.*;
import org.jitsi.testutils.*;
import org.jitsi.videobridge.health.config.*;
import org.junit.*;

import static org.junit.Assert.*;

public class OctoRegionPropertyTest
{
    @Test
    public void whenOnlyOldConfigIsPresentAndValueIsMissing()
    {
        Config legacyConfig = ConfigFactory.parseString("");
        new ConfigSetup()
            .withLegacyConfig(legacyConfig)
            .withNoNewConfig()
            .finishSetup();

        OctoConfig.RegionProperty octoRegionProperty = new OctoConfig.RegionProperty();
        assertNull("We should return null when it isn't set", octoRegionProperty.get());
    }

    @Test
    public void whenOnlyNewConfigIsPresentAndValueIsMissing()
    {
        Config newConfig = ConfigFactory.parseString("");
        new ConfigSetup()
            .withNoLegacyConfig()
            .withNewConfig(newConfig)
            .finishSetup();

        OctoConfig.RegionProperty octoRegionProperty = new OctoConfig.RegionProperty();
        assertNull("We should return null when it isn't set", octoRegionProperty.get());
    }
}
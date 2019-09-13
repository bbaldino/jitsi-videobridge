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

package org.jitsi.videobridge.util;

import com.typesafe.config.*;
import org.jetbrains.annotations.*;

import java.util.function.*;

public class JvbConfig
{
    //TODO: is this code needed?
//    protected static Config config = null;
//    public static Function<String, Config> configSupplier =
//            ConfigFactory::load;
//
//    // Must be called before getConfig (having this done manually allows
//    // time to override the configSupplier to use a mock
//    static void load()
//    {
//        config = configSupplier.apply("defaults");
//    }

    protected static Config config = ConfigFactory.load();

    static
    {
        System.out.println("Loaded config: " + config.root().render());
    }

    public static @NotNull Config getConfig()
    {
        return config;
    }
}

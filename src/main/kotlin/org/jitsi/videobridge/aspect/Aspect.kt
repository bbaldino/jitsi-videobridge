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

package org.jitsi.videobridge.aspect

interface Aspect {
    operator fun <T> invoke(block: () -> T): T
}

operator fun Aspect.plus(other: Aspect) = object : Aspect {
    override fun <T> invoke(block: () -> T): T {
        return this@plus {
            other {
                block()
            }
        }
    }
}

inline fun <T> requires(predicate: Boolean, block: () -> T): T {
    if (!predicate) {
        throw Exception("Predicate not met, not executing block")
    }
    return block()
}

inline fun onlyIf(predicate: Boolean, block: () -> Unit) {
    if (predicate) {
        block()
    }
}


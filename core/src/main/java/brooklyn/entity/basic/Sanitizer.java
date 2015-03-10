/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.basic;

import java.util.Map;
import java.util.Set;

import brooklyn.util.config.ConfigBag;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public final class Sanitizer {

    private Sanitizer() {
    } // not instantiable

    public static Map<String, Object> sanitize(ConfigBag input) {
        return sanitize(input.getAllConfig());
    }

    public static <K> Map<K, Object> sanitize(Map<K, ?> input) {
        return sanitize(input, Sets.newHashSet());
    }

    public static <K> Map<K, Object> sanitize(Map<K, ?> input, Set<Object> visited) {
        Map<K, Object> result = Maps.newLinkedHashMap();
        for (Map.Entry<K, ?> e : input.entrySet()) {
            if (Entities.isSecret("" + e.getKey()))
                result.put(e.getKey(), "xxxxxxxx");
            else if (e.getValue() instanceof Map) {
                if (visited.contains(e.getValue())) {
                    continue;
                }
                visited.add(e.getValue());
                result.put(e.getKey(), sanitize((Map<?, ?>) e.getValue(), visited));
            } else {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }
}

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
package brooklyn.util.internal;

import java.util.Map;

import brooklyn.management.ExecutionContext;

/** Interface for resolving key values; typically implemented by the config key,
 * but discouraged for external usage.
 */
// TODO replace by brooklyn.config.ConfigKey, when we removed the deprecated one
public interface ConfigKeySelfExtracting<T> extends brooklyn.config.ConfigKey<T> {
    /**
     * Extracts the value for this config key from the given map.
     */
    public T extractValue(Map<?,?> configMap, ExecutionContext exec);
    
    /**
     * @return True if there is an entry in the configMap that could be extracted
     */
    public boolean isSet(Map<?,?> configMap);
}

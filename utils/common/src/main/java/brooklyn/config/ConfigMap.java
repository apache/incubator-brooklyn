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
package brooklyn.config;

import java.util.Map;

import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.util.guava.Maybe;

import com.google.common.base.Predicate;

public interface ConfigMap {
    
    /** @see #getConfig(ConfigKey, Object), with default value as per the key, or null */
    public <T> T getConfig(ConfigKey<T> key);
    /** @see #getConfig(ConfigKey, Object), with default value as per the key, or null  */
    public <T> T getConfig(HasConfigKey<T> key);
    /** @see #getConfig(ConfigKey, Object), with provided default value if not set */
    public <T> T getConfig(HasConfigKey<T> key, T defaultValue);
    /** returns value stored against the given key,
     * resolved (if it is a Task, possibly blocking), and coerced to the appropriate type, 
     * or given default value if not set, 
     * unless the default value is null in which case it returns the default*/ 
    public <T> T getConfig(ConfigKey<T> key, T defaultValue);

    /** as {@link #getConfigRaw(ConfigKey)} but returning null if not present 
     * @deprecated since 0.7.0 use {@link #getConfigRaw(ConfigKey)} */
    @Deprecated
    public Object getRawConfig(ConfigKey<?> key);
    
    /** returns the value stored against the given key, 
     * <b>not</b> any default,
     * <b>not</b> resolved (and guaranteed non-blocking)
     * and <b>not</b> type-coerced
     * @param key  key to look up
     * @param includeInherited  for {@link ConfigMap} instances which have an inheritance hierarchy, 
     * whether to traverse it or not; has no effects where there is no inheritance 
     * @return raw, unresolved, uncoerced value of key in map,  
     * but <b>not</b> any default on the key
     */
    public Maybe<Object> getConfigRaw(ConfigKey<?> key, boolean includeInherited);

    /** returns a map of all config keys to their raw (unresolved+uncoerced) contents */
    public Map<ConfigKey<?>,Object> getAllConfig();

    /** returns submap matching the given filter predicate; see ConfigPredicates for common predicates */
    public ConfigMap submap(Predicate<ConfigKey<?>> filter);

    /** returns a read-only map view which has string keys (corresponding to the config key names);
     * callers encouraged to use the typed keys (and so not use this method),
     * but in some compatibility areas having a Properties-like view is useful */
    public Map<String,Object> asMapWithStringKeys();
    
    public int size();
    
    public boolean isEmpty();
    
}

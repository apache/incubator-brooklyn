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
package org.apache.brooklyn.core.config;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
import org.apache.brooklyn.core.config.ConfigUtils;
import org.apache.brooklyn.core.config.WrappedConfigKey;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;

@SuppressWarnings({"unchecked"})
public class ConfigUtils {

    private static final Logger log = LoggerFactory.getLogger(ConfigUtils.class);
    
    public static <T> T getRequiredConfig(Entity entity, ConfigKey<T> key) {
        T result = entity.getConfig(key);
        if (result==null) throw new IllegalStateException("Configuration "+key+" is required");
        return result;
    }
    
    /** prepends the given prefix to the key.  prefix will typically end with a ".".
     * this is useful for configuration purposes when a subsystem uses a short-name config (e.g. "user")
     * but in entity config or at the root (brooklyn.properties) there are longer names (e.g. "brooklyn.ssh.config.user"),
     * and we wish to convert from the shorter names to the longer names. */
    public static <T> ConfigKey<T> prefixedKey(String prefix, ConfigKey<T> key) {
        return ConfigKeys.newConfigKeyWithPrefix(prefix, key);
    }
    
    /** removes the given prefix from the key for configuration purposes; logs warning and does nothing if there is no such prefix.
     * prefix will typically end with a ".".
     * this is useful for configuration purposes when a subsystem uses a short-name config (e.g. "user")
     * but in entity config or at the root (brooklyn.properties) there are longer names (e.g. "brooklyn.ssh.config.user"),
     * and we wish to convert from the longer names to the short-name. */
    public static <T> ConfigKey<T> unprefixedKey(String prefix, ConfigKey<T> key) {
        String newName = key.getName();
        if (newName.startsWith(prefix)) newName = newName.substring(prefix.length());
        else log.warn("Cannot remove prefix "+prefix+" from key "+key+" (ignoring)");
        return new BasicConfigKey<T>(key.getTypeToken(), newName, key.getDescription(), key.getDefaultValue());
    }
    
    
    public static BrooklynProperties loadFromFile(String file) {
        BrooklynProperties result = BrooklynProperties.Factory.newEmpty();
        if (file!=null) result.addFrom(new File(file));
        return result;
    }
    
    public static BrooklynProperties filterFor(BrooklynProperties properties, Predicate<? super String> filter) {
        BrooklynProperties result = BrooklynProperties.Factory.newEmpty();
        for (String k: (Collection<String>)properties.keySet()) {
            if (filter.apply(k)) {
                result.put(k, properties.get(k));
            }
        }
        return result;
    }
    
    public static BrooklynProperties filterForPrefix(BrooklynProperties properties, String prefix) {
        BrooklynProperties result = BrooklynProperties.Factory.newEmpty();
        for (String k: (Collection<String>)properties.keySet()) {
            if (k.startsWith(prefix)) {
                result.put(k, properties.get(k));
            }
        }
        return result;
    }
    
    /** prefix generally ends with a full stop */
    public static BrooklynProperties filterForPrefixAndStrip(Map<String,?> properties, String prefix) {
        BrooklynProperties result = BrooklynProperties.Factory.newEmpty();
        for (String k: properties.keySet()) {
            if (k.startsWith(prefix)) {
                result.put(k.substring(prefix.length()), properties.get(k));
            }
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    public static Set<HasConfigKey<?>> getStaticKeysOnClass(Class<?> type) {
        Set<HasConfigKey<?>> result = new LinkedHashSet<ConfigKey.HasConfigKey<?>>();
        for (Field f: type.getFields()) {
            try {
                if ((f.getModifiers() & Modifier.STATIC)==0)
                    continue;
                if (ConfigKey.class.isAssignableFrom(f.getType()))
                    result.add(new WrappedConfigKey((ConfigKey<?>) f.get(null)));
                else if (HasConfigKey.class.isAssignableFrom(f.getType()))
                    result.add((HasConfigKey<?>) f.get(null));
            } catch (Exception e) {
                log.error("Error retrieving config key for field "+f+" on class "+type+"; rethrowing", e);
                throw Exceptions.propagate(e);
            }
        }
        return Collections.unmodifiableSet(result);
    }
}

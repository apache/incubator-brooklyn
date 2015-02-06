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
package brooklyn.location;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.AbstractBrooklynObjectSpec;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.management.Task;

import com.google.common.collect.Maps;

/**
 * Gives details of a location to be created. It describes the location's configuration, and is
 * reusable to create multiple locations with the same configuration.
 * 
 * To create a LocationSpec, it is strongly encouraged to use {@code create(...)} methods.
 * 
 * @param <T> The type of location to be created
 * 
 * @author aled
 */
public class LocationSpec<T extends Location> extends AbstractBrooklynObjectSpec<T,LocationSpec<T>> {

    // TODO Would like to add `configure(ConfigBag)`, but `ConfigBag` is in core rather than api
    
    private static final Logger log = LoggerFactory.getLogger(LocationSpec.class);

    private final static long serialVersionUID = 1L;

    /**
     * Creates a new {@link LocationSpec} instance for a location of the given type. The returned 
     * {@link LocationSpec} can then be customized.
     * 
     * @param type A {@link Location} class
     */
    public static <T extends Location> LocationSpec<T> create(Class<T> type) {
        return new LocationSpec<T>(type);
    }
    
    /**
     * Creates a new {@link LocationSpec} instance with the given config, for a location of the given type.
     * 
     * This is primarily for groovy code; equivalent to {@code LocationSpec.create(type).configure(config)}.
     * 
     * @param config The spec's configuration (see {@link LocationSpec#configure(Map)}).
     * @param type   A {@link Location} class
     */
    public static <T extends Location> LocationSpec<T> create(Map<?,?> config, Class<T> type) {
        return LocationSpec.create(type).configure(config);
    }
    
    private String id;
    private Location parent;
    private final Map<String, Object> flags = Maps.newLinkedHashMap();
    private final Map<ConfigKey<?>, Object> config = Maps.newLinkedHashMap();
    private final Map<Class<?>, Object> extensions = Maps.newLinkedHashMap();

    protected LocationSpec(Class<T> type) {
        super(type);
    }
     
    protected void checkValidType(Class<? extends T> type) {
        checkIsImplementation(type, Location.class);
        checkIsNewStyleImplementation(type);
    }

    /**
     * @deprecated since 0.7.0; instead let the management context pick a random+unique id
     */
    @Deprecated
    public LocationSpec<T> id(String val) {
        id = val;
        return this;
    }

    public LocationSpec<T> parent(Location val) {
        parent = checkNotNull(val, "parent");
        return this;
    }

    public LocationSpec<T> configure(Map<?,?> val) {
        for (Map.Entry<?, ?> entry: val.entrySet()) {
            if (entry.getKey()==null) throw new NullPointerException("Null key not permitted");
            if (entry.getKey() instanceof CharSequence)
                flags.put(entry.getKey().toString(), entry.getValue());
            else if (entry.getKey() instanceof ConfigKey<?>)
                config.put((ConfigKey<?>)entry.getKey(), entry.getValue());
            else if (entry.getKey() instanceof HasConfigKey<?>)
                config.put(((HasConfigKey<?>)entry.getKey()).getConfigKey(), entry.getValue());
            else {
                log.warn("Spec "+this+" ignoring unknown config key "+entry.getKey());
            }
        }
        return this;
    }
    
    public LocationSpec<T> configure(CharSequence key, Object val) {
        flags.put(checkNotNull(key, "key").toString(), val);
        return this;
    }
    
    public <V> LocationSpec<T> configure(ConfigKey<V> key, V val) {
        config.put(checkNotNull(key, "key"), val);
        return this;
    }

    public <V> LocationSpec<T> configureIfNotNull(ConfigKey<V> key, V val) {
        return (val != null) ? configure(key, val) : this;
    }

    public <V> LocationSpec<T> configure(ConfigKey<V> key, Task<? extends V> val) {
        config.put(checkNotNull(key, "key"), val);
        return this;
    }

    public <V> LocationSpec<T> configure(HasConfigKey<V> key, V val) {
        config.put(checkNotNull(key, "key").getConfigKey(), val);
        return this;
    }

    public <V> LocationSpec<T> configure(HasConfigKey<V> key, Task<? extends V> val) {
        config.put(checkNotNull(key, "key").getConfigKey(), val);
        return this;
    }

    public <V> LocationSpec<T> removeConfig(ConfigKey<V> key) {
        config.remove( checkNotNull(key, "key") );
        return this;
    }

    public <E> LocationSpec<T> extension(Class<E> extensionType, E extension) {
        extensions.put(checkNotNull(extensionType, "extensionType"), checkNotNull(extension, "extension"));
        return this;
    }
    
    /**
     * @return The id of the location to be created, or null if brooklyn can auto-generate an id
     * 
     * @deprecated since 0.7.0; instead let the management context pick a random+unique id
     */
    @Deprecated
    public String getId() {
        return id;
    }
    
    /**
     * @return The location's parent
     */
    public Location getParent() {
        return parent;
    }
    
    /**
     * @return Read-only construction flags
     * @see SetFromFlag declarations on the location type
     */
    public Map<String, ?> getFlags() {
        return Collections.unmodifiableMap(flags);
    }
    
    /**
     * @return Read-only configuration values
     */
    public Map<ConfigKey<?>, Object> getConfig() {
        return Collections.unmodifiableMap(config);
    }
        
    /**
     * @return Read-only extension values
     */
    public Map<Class<?>, Object> getExtensions() {
        return Collections.unmodifiableMap(extensions);
    }

}

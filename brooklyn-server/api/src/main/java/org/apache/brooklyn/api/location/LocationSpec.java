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
package org.apache.brooklyn.api.location;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Map;

import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.config.ConfigKey;

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
    
    /**
     * Copies entity spec so its configuration can be overridden without modifying the 
     * original entity spec.
     */
    public static <T extends Location> LocationSpec<T> create(LocationSpec<T> spec) {
        // need this to get LocationSpec<T> rather than LocationSpec<? extends T>
        @SuppressWarnings("unchecked")
        Class<T> exactType = (Class<T>)spec.getType();
        
        return create(exactType).copyFrom(spec);
    }

    private String id;
    private Location parent;
    private final Map<Class<?>, Object> extensions = Maps.newLinkedHashMap();

    protected LocationSpec(Class<T> type) {
        super(type);
    }
     
    @Override
    protected LocationSpec<T> copyFrom(LocationSpec<T> otherSpec) {
        LocationSpec<T> result = super.copyFrom(otherSpec).extensions(otherSpec.getExtensions());
        if (otherSpec.getParent() != null) result.parent(otherSpec.getParent());
        if (otherSpec.getId() != null) result.id(otherSpec.getId());
        return result;
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

    public <E> LocationSpec<T> extension(Class<E> extensionType, E extension) {
        extensions.put(checkNotNull(extensionType, "extensionType"), checkNotNull(extension, "extension"));
        return this;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <E> LocationSpec<T> extensions(Map<Class<?>, ?> extensions) {
        for (Map.Entry<Class<?>, ?> entry : extensions.entrySet()) {
            extension((Class)entry.getKey(), entry.getValue());
        }
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

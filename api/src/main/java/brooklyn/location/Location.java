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

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import brooklyn.basic.BrooklynObject;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;

/**
 * A location that an entity can be in. Examples of locations include a single machine
 * or a pool of machines, or a region within a given cloud. 
 * 
 * See {@link brooklyn.entity.trait.Startable#start(Collection)}.
 * 
 * Locations may not be {@link Serializable} in subsequent releases!
 */
public interface Location extends Serializable, BrooklynObject {

    /**
     * A unique id for this location.
     */
    @Override
    String getId();

    /**
     * Get the name assigned to this location.
     *
     * @return the name assigned to the location.
     * @since 0.6 (previously getName())
     */
    @Override
    String getDisplayName();

    /**
     * Get the 'parent' of this location. Locations are organized into a tree hierarchy, and this method will return a reference
     * to the parent of this location, or {@code null} if this location is the tree root.
     *
     * @return a reference to the parent of this location, or {@code null} if this location is the tree root.
     * @since 0.6 (previously getParentLocation())
     */
    Location getParent();

    /**
     * Get the 'children' of this location. Locations are organized into a tree hierarchy, and this method will return a
     * collection containing the children of this location. This collection is an unmodifiable view of the data.
     *
     * @return a collection containing the children of this location.
     * @since 0.6 (previously getChildLocations())
     */
    Collection<Location> getChildren();

    /**
     * Set the 'parent' of this location. If this location was previously a child of a different location, it is removed from
     * the other location first. It is valid to pass in {@code null} to indicate that the location should be disconnected
     * from its parent.
     * 
     * Adds this location as a child of the new parent (see {@code getChildLocations()}).
     *
     * @param newParent the new parent location object, or {@code null} to clear the parent reference.
     * @since 0.6 (previously setParentLocation(Location))
     */
    void setParent(Location newParent);

    /**
     * @return meta-data about the location (usually a long line, or a small number of lines).
     * 
     * @since 0.6
     */
    String toVerboseString();
    
    /**
     * Answers true if this location equals or is an ancestor of the given location.
     */
    boolean containsLocation(Location potentialDescendent);

    /** Returns configuration set at this location or inherited or default */
    <T> T getConfig(ConfigKey<T> key);
    
    <T> T getConfig(HasConfigKey<T> key);

    /** True iff the indication config key is set, either inherited (second argument true) or locally-only (second argument false) */
    boolean hasConfig(ConfigKey<?> key, boolean includeInherited);

    /** Returns all config set, either inherited (argument true) or locally-only (argument false) */
    public Map<String,Object> getAllConfig(boolean includeInherited);
    
    /**
     * Whether this location has support for the given extension type.
     * See additional comments in {@link #getExtension(Class)}.
     * 
     * @throws NullPointerException if extensionType is null
     */
    boolean hasExtension(Class<?> extensionType);

    /**
     * Returns an extension of the given type. Note that the type must be an exact match for
     * how the extension was registered (e.g. {@code getExtension(Object.class)} will not match
     * anything, even though registered extension extend {@link Object}.
     * <p>
     * This will not look at extensions of {@link #getParent()}.
     * 
     * @throws IllegalArgumentException if this location does not support the given extension type
     * @throws NullPointerException if extensionType is null
     */
    <T> T getExtension(Class<T> extensionType);
}

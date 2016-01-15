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

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import org.apache.brooklyn.util.guava.Maybe;

import com.google.common.annotations.Beta;

/**
 * The registry of the sorts of locations that brooklyn knows about. Given a
 * {@LocationDefinition} or a {@link String} representation of a spec, this can
 * be used to create a {@link Location} instance.
 */
@SuppressWarnings("rawtypes")
public interface LocationRegistry {

    /** map of ID (possibly randomly generated) to the definition (spec, name, id, and props; 
     * where spec is the spec as defined, for instance possibly another named:xxx location) */
    public Map<String,LocationDefinition> getDefinedLocations();
    
    /** returns a LocationDefinition given its ID (usually a random string), or null if none */
    public LocationDefinition getDefinedLocationById(String id);
    
    /** returns a LocationDefinition given its name (e.g. for named locations, supply the bit after the "named:" prefix), 
     * or null if none */
    public LocationDefinition getDefinedLocationByName(String name);

    /** adds or updates the given defined location */
    public void updateDefinedLocation(LocationDefinition l);

    /** removes the defined location from the registry (applications running there are unaffected) */
    public void removeDefinedLocation(String id);

    /** Returns a fully populated (config etc) location from the given definition, with optional add'l flags.
     * the location will be managed by default, unless the manage parameter is false, 
     * or the manage parameter is null and the CREATE_UNMANAGED flag is set.
     * <p>
     * The manage parameter is {@link Boolean} so that null can be used to say rely on anything in the flags.
     * 
     * @since 0.7.0, but beta and likely to change as the semantics of this class are tuned */
    @Beta
    public Maybe<Location> resolve(LocationDefinition ld, Boolean manage, Map locationFlags);
    
    /** As {@link #resolve(LocationDefinition, Boolean, Map), with the location managed, and no additional flags,
     * unwrapping the result (throwing if not resolvable) */
    public Location resolve(LocationDefinition l);

    /** Returns a location created from the given spec, which might correspond to a definition, or created on-the-fly.
     * Optional flags can be passed through to underlying the location. 
     * @since 0.7.0, but beta and likely to change as the semantics of this class are tuned */
    @Beta
    public Maybe<Location> resolve(String spec, Boolean manage, Map locationFlags);
    
    /** efficiently returns for inspection only a fully populated (config etc) location from the given definition; 
     * the value might be unmanaged so it is not meant for any use other than inspection,
     * but callers should prefer this when they don't wish to create a new location which will be managed in perpetuity!
     * 
     * @deprecated since 0.7.0, use {@link #resolve(LocationDefinition, Boolean, Map)} */
    @Deprecated
    public Location resolveForPeeking(LocationDefinition l);

    /** returns fully populated (config etc) location from the given definition, with overrides;
     * @deprecated since 0.7.0, use {@link #resolve(LocationDefinition, Boolean, Map)} */
    @Deprecated
    public Location resolve(LocationDefinition l, Map<?,?> locationFlags);
    
    /** See {@link #resolve(String, Boolean, Map)}; asks for the location to be managed, and supplies no additional flags,
     * and unwraps the result (throwing if the spec cannot be resolve) */
    public Location resolve(String spec);
    
    /** Returns true/false depending whether spec seems like a valid location,
     * that is it has a chance of being resolved (depending on the spec) but NOT guaranteed,
     * as it is not passed to the spec;
     * see {@link #resolve(String, Boolean, Map)} which has stronger guarantees 
     * @deprecated since 0.7.0, not really needed, and semantics are weak; use {@link #resolve(String, Boolean, Map)} */
    @Deprecated
    public boolean canMaybeResolve(String spec);
    
    /** As {@link #resolve(String, Boolean, Map)}, but unwrapped
     * @throws NoSuchElementException if the spec cannot be resolved */
    public Location resolve(String spec, @Nullable Map locationFlags);
    
    /** as {@link #resolve(String)} but returning null (never throwing)
     * @deprecated since 0.7.0 use {@link #resolve(String, Boolean, Map)} */
    @Deprecated
    public Location resolveIfPossible(String spec);

    /**
     * As {@link #resolve(String)} but takes collections (of strings or locations)
     * <p>
     * Expects a collection of elements being individual location spec strings or locations, 
     * and returns a list of resolved (newly created and managed) locations.
     * <p>
     * From 0.7.0 this no longer flattens lists (nested lists are disallowed) 
     * or parses comma-separated elements (they are resolved as-is)
     */
    public List<Location> resolve(Iterable<?> spec);
    
    /** Takes a string, interpreted as a comma-separated (or JSON style, when you need internal double quotes or commas) list;
     * or a list, passed to {@link #resolve(Iterable)}; or null/empty (empty list),
     * and returns a list of resolved (created and managed) locations */
    public List<Location> resolveList(Object specList);
    
    public Map getProperties();
    
}

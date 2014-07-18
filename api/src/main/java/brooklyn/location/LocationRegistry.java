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

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

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

    /** returns fully populated (config etc) location from the given definition, 
     * currently by creating it but TODO creation can be a leak so all current 'resolve' methods should be carefully used! */
    public Location resolve(LocationDefinition l);

    /** efficiently returns for inspection only a fully populated (config etc) location from the given definition; 
     * the value might be unmanaged so it is not meant for any use other than inspection,
     * but callers should prefer this when they don't wish to create a new location which will be managed in perpetuity!
     * 
     * @since 0.7.0, but beta and likely to change as the semantics of this class are tuned */
    @Beta   // see impl for notes
    public Location resolveForPeeking(LocationDefinition l);

    /** returns fully populated (config etc) location from the given definition */
    public Location resolve(LocationDefinition l, Map<?,?> locationFlags);
    
    /** See {@link #resolve(String, Map)} (with no options) */
    public Location resolve(String spec);
    
    /** Returns true/false depending whether spec seems like a valid location,
     * that is it has a chance of being resolved (depending on the spec) but NOT guaranteed;
     * see {@link #resolveIfPossible(String)} which has stronger guarantees */
    public boolean canMaybeResolve(String spec);
    
    /** Returns a location created from the given spec, which might correspond to a definition, or created on-the-fly.
     * Optional flags can be passed through to underlying the location. 
     * @throws NoSuchElementException if the spec cannot be resolved */
    public Location resolve(String spec, Map locationFlags);
    
    /** as {@link #resolve(String)} but returning null (never throwing) */
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

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
package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigMap;
import brooklyn.config.ConfigPredicates;
import brooklyn.config.ConfigUtils;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.LocationResolver.EnableableLocationResolver;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalLocationManager;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.StringEscapes.JavaStringEscapes;
import brooklyn.util.text.WildcardGlobs;
import brooklyn.util.text.WildcardGlobs.PhraseTreatment;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

@SuppressWarnings({"rawtypes","unchecked"})
public class BasicLocationRegistry implements LocationRegistry {

    public static final Logger log = LoggerFactory.getLogger(BasicLocationRegistry.class);

    /**
     * Splits a comma-separated list of locations (names or specs) into an explicit list.
     * The splitting is very careful to handle commas embedded within specs, to split correctly.
     */
    public static List<String> expandCommaSeparateLocations(String locations) {
        return WildcardGlobs.getGlobsAfterBraceExpansion("{"+locations+"}", false, PhraseTreatment.INTERIOR_NOT_EXPANDABLE, PhraseTreatment.INTERIOR_NOT_EXPANDABLE);
        // don't do this, it tries to expand commas inside parentheses which is not good!
//        QuotedStringTokenizer.builder().addDelimiterChars(",").buildList((String)id);
    }

    private final ManagementContext mgmt;
    /** map of defined locations by their ID */
    private final Map<String,LocationDefinition> definedLocations = new LinkedHashMap<String, LocationDefinition>();

    protected final Map<String,LocationResolver> resolvers = new LinkedHashMap<String, LocationResolver>();

    private final Set<String> specsWarnedOnException = Sets.newConcurrentHashSet();

    public BasicLocationRegistry(ManagementContext mgmt) {
        this.mgmt = checkNotNull(mgmt, "mgmt");
        findServices();
        updateDefinedLocations();
    }

    protected void findServices() {
        ServiceLoader<LocationResolver> loader = ServiceLoader.load(LocationResolver.class, mgmt.getCatalog().getRootClassLoader());
        for (LocationResolver r: loader) {
            registerResolver(r);
        }
        if (log.isDebugEnabled()) log.debug("Location resolvers are: "+resolvers);
        if (resolvers.isEmpty()) log.warn("No location resolvers detected: is src/main/resources correctly included?");
    }

    /** Registers the given resolver, invoking {@link LocationResolver#init(ManagementContext)} on the argument
     * and returning true, unless the argument indicates false for {@link EnableableLocationResolver#isEnabled()} */
    public boolean registerResolver(LocationResolver r) {
        r.init(mgmt);
        if (r instanceof EnableableLocationResolver) {
            if (!((EnableableLocationResolver)r).isEnabled()) {
                return false;
            }
        }
        resolvers.put(r.getPrefix(), r);
        return true;
    }
    
    @Override
    public Map<String,LocationDefinition> getDefinedLocations() {
        synchronized (definedLocations) {
            return ImmutableMap.<String,LocationDefinition>copyOf(definedLocations);
        }
    }
    
    @Override
    public LocationDefinition getDefinedLocationById(String id) {
        return definedLocations.get(id);
    }

    @Override
    public LocationDefinition getDefinedLocationByName(String name) {
        synchronized (definedLocations) {
            for (LocationDefinition l: definedLocations.values()) {
                if (l.getName().equals(name)) return l;
            }
            return null;
        }
    }

    @Override
    public void updateDefinedLocation(LocationDefinition l) {
        synchronized (definedLocations) { 
            definedLocations.put(l.getId(), l); 
        }
    }

    @Override
    public void removeDefinedLocation(String id) {
        LocationDefinition removed;
        synchronized (definedLocations) { 
            removed = definedLocations.remove(id);
        }
        if (removed == null && log.isDebugEnabled()) {
            log.debug("{} was asked to remove location with id {} but no such location was registered", this, id);
        }
    }
    
    public void updateDefinedLocations() {
        synchronized (definedLocations) {
            // first read all properties starting  brooklyn.location.named.xxx
            // (would be nice to move to a better way, then deprecate this approach, but first
            // we need ability/format for persisting named locations, and better support for adding+saving via REST/GUI)
            int count = 0;
            String NAMED_LOCATION_PREFIX = "brooklyn.location.named.";
            ConfigMap namedLocationProps = mgmt.getConfig().submap(ConfigPredicates.startingWith(NAMED_LOCATION_PREFIX));
            for (String k: namedLocationProps.asMapWithStringKeys().keySet()) {
                String name = k.substring(NAMED_LOCATION_PREFIX.length());
                // If has a dot, then is a sub-property of a named location (e.g. brooklyn.location.named.prod1.user=bob)
                if (!name.contains(".")) {
                    // this is a new named location
                    String spec = (String) namedLocationProps.asMapWithStringKeys().get(k);
                    // make up an ID
                    String id = Identifiers.makeRandomId(8);
                    Map<String, Object> config = ConfigUtils.filterForPrefixAndStrip(namedLocationProps.asMapWithStringKeys(), k+".");
                    definedLocations.put(id, new BasicLocationDefinition(id, name, spec, config));
                    count++;
                }
            }
            if (log.isDebugEnabled())
                log.debug("Found "+count+" defined locations from properties (*.named.* syntax): "+definedLocations.values());
            if (getDefinedLocationByName("localhost")==null && !BasicOsDetails.Factory.newLocalhostInstance().isWindows()
                    && LocationConfigUtils.isEnabled(mgmt, "brooklyn.location.localhost")) {
                log.debug("Adding a defined location for localhost");
                // add 'localhost' *first*
                ImmutableMap<String, LocationDefinition> oldDefined = ImmutableMap.copyOf(definedLocations);
                definedLocations.clear();
                String id = Identifiers.makeRandomId(8);
                definedLocations.put(id, localhost(id));
                definedLocations.putAll(oldDefined);
            }
        }
    }
    
    // TODO save / serialize
    
    @VisibleForTesting
    void disablePersistence() {
        // persistence isn't enabled yet anyway (have to manually save things,
        // defining the format and file etc)
    }

    BasicLocationDefinition localhost(String id) {
        return new BasicLocationDefinition(id, "localhost", "localhost", null);
    }
    
    /** to catch circular references */
    protected ThreadLocal<Set<String>> specsSeen = new ThreadLocal<Set<String>>();
    
    @Override
    public boolean canMaybeResolve(String spec) {
        return getSpecResolver(spec) != null;
    }

    @Override
    public final Location resolve(String spec) {
        return resolve(spec, new MutableMap());
    }
    
    @Override
    public final Location resolveIfPossible(String spec) {
        if (!canMaybeResolve(spec)) return null;
        try {
            return resolve(spec);
        } catch (Exception e) {
            if (log.isTraceEnabled())
                log.trace("Unable to resolve "+spec+": "+e, e);
            // can't resolve
            return null;
        }
    }

    @Override
    public Location resolve(String spec, Map locationFlags) {
        try {
            Set<String> seenSoFar = specsSeen.get();
            if (seenSoFar==null) {
                seenSoFar = new LinkedHashSet<String>();
                specsSeen.set(seenSoFar);
            }
            if (seenSoFar.contains(spec))
                throw new IllegalStateException("Circular reference in definition of location '"+spec+"' ("+seenSoFar+")");
            seenSoFar.add(spec);
            
            LocationResolver resolver = getSpecResolver(spec);

            if (resolver != null) {
                return resolver.newLocationFromString(locationFlags, spec, this);
            }

            // problem: but let's ensure that classpath is sane to give better errors in common IDE bogus case;
            // and avoid repeated logging
            String errmsg;
            if (spec == null || specsWarnedOnException.add(spec)) {
                if (resolvers.get("id")==null || resolvers.get("named")==null) {
                    log.error("Standard location resolvers not installed, location resolution will fail shortly. "
                            + "This usually indicates a classpath problem, such as when running from an IDE which "
                            + "has not properly copied META-INF/services from src/main/resources. "
                            + "Known resolvers are: "+resolvers.keySet());
                    errmsg = "Unresolvable location '"+spec+"': "
                            + "Problem detected with location resolver configuration; "
                            + resolvers.keySet()+" are the only available location resolvers. "
                            + "More information can be found in the logs.";
                } else {
                    log.warn("Location resolution failed for '"+spec+"' (will fail shortly): known resolvers are: "+resolvers.keySet());
                    errmsg = "Unknown location '"+spec+"': "
                            + "either this location is not recognised or there is a problem with location resolver configuration.";
                }
            } else {
                // For helpful log message construction: assumes classpath will not suddenly become wrong; might happen with OSGi though!
                if (log.isDebugEnabled()) log.debug("Location resolution failed again for '"+spec+"' (throwing)");
                errmsg = "Unknown location '"+spec+"': "
                        + "either this location is not recognised or there is a problem with location resolver configuration.";
            }

            throw new NoSuchElementException(errmsg);

        } finally {
            specsSeen.remove();
        }
    }

    protected LocationResolver getSpecResolver(String spec) {
        int colonIndex = spec.indexOf(':');
        int bracketIndex = spec.indexOf("(");
        int dividerIndex = (colonIndex < 0) ? bracketIndex : (bracketIndex < 0 ? colonIndex : Math.min(bracketIndex, colonIndex));
        String prefix = dividerIndex >= 0 ? spec.substring(0, dividerIndex) : spec;
        LocationResolver resolver = resolvers.get(prefix);
       
        if (resolver == null)
            resolver = getSpecDefaultResolver(spec);
        
        return resolver;
    }
    
    protected LocationResolver getSpecDefaultResolver(String spec) {
        return getSpecFirstResolver(spec, "id", "named", "jclouds");
    }
    protected LocationResolver getSpecFirstResolver(String spec, String ...resolversToCheck) {
        for (String resolverId: resolversToCheck) {
            LocationResolver resolver = resolvers.get(resolverId);
            if (resolver!=null && resolver.accepts(spec, this))
                return resolver;
        }
        return null;
    }

    /** providers default impl for RegistryLocationResolver.accepts */
    public static boolean isResolverPrefixForSpec(LocationResolver resolver, String spec, boolean argumentRequired) {
        if (spec==null) return false;
        if (spec.startsWith(resolver.getPrefix()+":")) return true;
        if (!argumentRequired && spec.equals(resolver.getPrefix())) return true;
        return false;
    }

    @Override
    public List<Location> resolve(Iterable<?> spec) {
        List<Location> result = new ArrayList<Location>();
        for (Object id : spec) {
            if (id instanceof String) {
                result.add(resolve((String) id));
            } else if (id instanceof Location) {
                result.add((Location) id);
            } else {
                if (id instanceof Iterable)
                    throw new IllegalArgumentException("Cannot resolve '"+id+"' to a location; collections of collections not allowed"); 
                throw new IllegalArgumentException("Cannot resolve '"+id+"' to a location; unsupported type "+
                        (id == null ? "null" : id.getClass().getName())); 
            }
        }
        return result;
    }
    
    public List<Location> resolveList(Object l) {
        if (l==null) l = Collections.emptyList();
        if (l instanceof String) l = JavaStringEscapes.unwrapJsonishListIfPossible((String)l);
        if (l instanceof Iterable) return resolve((Iterable<?>)l);
        throw new IllegalArgumentException("Location list must be supplied as a collection or a string, not "+
            JavaClassNames.simpleClassName(l)+"/"+l);
    }
    
    @Override
    public Location resolve(LocationDefinition ld) {
        return resolve(ld, Collections.emptyMap());
    }

    @Override
    public Location resolveForPeeking(LocationDefinition ld) {
        // TODO should clean up how locations are stored, figuring out whether they are shared or not;
        // or maybe better, the API calls to this might just want to get the LocationSpec objects back
        
        // for now we use a 'CREATE_UNMANGED' flag to prevent management (leaks and logging)
        return resolve(ld, ConfigBag.newInstance().configure(LocalLocationManager.CREATE_UNMANAGED, true).getAllConfig());
    }

    @Override
    public Location resolve(LocationDefinition ld, Map<?,?> flags) {
        return resolveLocationDefinition(ld, flags, null);
    }
    
    public Location resolveLocationDefinition(LocationDefinition ld, Map locationFlags, String optionalName) {
        ConfigBag newLocationFlags = ConfigBag.newInstance(ld.getConfig())
            .putAll(locationFlags)
            .putIfAbsentAndNotNull(LocationInternal.NAMED_SPEC_NAME, ld.getName())
            .putIfAbsentAndNotNull(LocationInternal.ORIGINAL_SPEC, ld.getName());
        try {
            return resolve(ld.getSpec(), newLocationFlags.getAllConfig());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot instantiate location '"+
                (optionalName!=null ? optionalName : ld)+"' pointing at "+ld.getSpec()+": "+e, e);
        }
    }

    @Override
    public Map getProperties() {
        return mgmt.getConfig().asMapWithStringKeys();
    }

    @VisibleForTesting
    public static void setupLocationRegistryForTesting(ManagementContext mgmt) {
        // ensure localhost is added (even on windows)
        LocationDefinition l = mgmt.getLocationRegistry().getDefinedLocationByName("localhost");
        if (l==null) mgmt.getLocationRegistry().updateDefinedLocation(
                ((BasicLocationRegistry)mgmt.getLocationRegistry()).localhost(Identifiers.makeRandomId(8)));
        
        ((BasicLocationRegistry)mgmt.getLocationRegistry()).disablePersistence();
    }

}

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
import org.apache.brooklyn.catalog.BrooklynCatalog;
import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.management.ManagementContext;

import brooklyn.catalog.CatalogPredicates;
import brooklyn.config.ConfigMap;
import brooklyn.config.ConfigPredicates;
import brooklyn.config.ConfigUtils;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.LocationResolver.EnableableLocationResolver;
import brooklyn.location.LocationSpec;
import brooklyn.management.internal.LocalLocationManager;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.guava.Maybe.Absent;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.StringEscapes.JavaStringEscapes;
import brooklyn.util.text.WildcardGlobs;
import brooklyn.util.text.WildcardGlobs.PhraseTreatment;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

/**
 * See {@link LocationRegistry} for general description.
 * <p>
 * TODO The relationship between the catalog and the location registry is a bit messy.
 * For all existing code, the location registry is the definitive way to resolve
 * locations. 
 * <p>
 * Any location item added to the catalog must therefore be registered here.
 * Whenever an item is added to the catalog, it will automatically call 
 * {@link #updateDefinedLocation(CatalogItem)}. Similarly, when a location
 * is deleted from the catalog it will call {@link #removeDefinedLocation(CatalogItem)}.
 * <p>
 * However, the location item in the catalog has an unparsed blob of YAML, which contains
 * important things like the type and the config of the location. This is only parsed when 
 * {@link BrooklynCatalog#createSpec(CatalogItem)} is called. We therefore jump through 
 * some hoops to wire together the catalog and the registry.
 * <p>
 * To add a location to the catalog, and then to resolve a location that is in the catalog, 
 * it goes through the following steps:
 * 
 * <ol>
 *   <li>Call {@link BrooklynCatalog#addItems(String)}
 *     <ol>
 *       <li>This automatically calls {@link #updateDefinedLocation(CatalogItem)}
 *       <li>A LocationDefinition is creating, using as its id the {@link CatalogItem#getSymbolicName()}.
 *           The definition's spec is {@code brooklyn.catalog:<symbolicName>:<version>},
 *     </ol>
 *   <li>A blueprint can reference the catalog item using its symbolic name, 
 *       such as the YAML {@code location: my-new-location}.
 *       (this feels similar to the "named locations").
 *     <ol>
 *       <li>This automatically calls {@link #resolve(String)}.
 *       <li>The LocationDefinition is found by lookig up this name.
 *       <li>The {@link LocationDefiniton.getSpec()} is retrieved; the right {@link LocationResolver} is 
 *           found for it.
 *       <li>This uses the {@link CatalogLocationResolver}, because the spec starts with {@code brooklyn.catalog:}.
 *       <li>This resolver extracts from the spec the <symobolicName>:<version>, and looks up the 
 *           catalog item using {@link BrooklynCatalog#getCatalogItem(String, String)}.
 *       <li>It then creates a {@link LocationSpec} by calling {@link BrooklynCatalog#createSpec(CatalogItem)}.
 *         <ol>
 *           <li>This first tries to use the type (that is in the YAML) as a simple Java class.
 *           <li>If that fails, it will resolve the type using {@link #resolve(String, Boolean, Map)}, which
 *               returns an actual location object.
 *           <li>It extracts from that location object the appropriate metadata to create a {@link LocationSpec},
 *               returns the spec and discards the location object.
 *         </ol>
 *       <li>The resolver creates the {@link Location} from the {@link LocationSpec}
 *     </ol>
 * </ol>
 * 
 * There is no concept of a location version in this registry. The version
 * in the catalog is generally ignored.
 */
@SuppressWarnings({"rawtypes","unchecked"})
public class BasicLocationRegistry implements LocationRegistry {

    // TODO save / serialize
    // (we persist live locations, ie those in the LocationManager, but not "catalog" locations, ie those in this Registry)
    
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
        ServiceLoader<LocationResolver> loader = ServiceLoader.load(LocationResolver.class, mgmt.getCatalogClassLoader());
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

    /**
     * Converts the given item from the catalog into a LocationDefinition, and adds it
     * to the registry (overwriting anything already registered with the id
     * {@link CatalogItem#getCatalogItemId()}.
     */
    public void updateDefinedLocation(CatalogItem<Location, LocationSpec<?>> item) {
        String id = item.getCatalogItemId();
        String symbolicName = item.getSymbolicName();
        String spec = CatalogLocationResolver.NAME + ":" + id;
        Map<String, Object> config = ImmutableMap.<String, Object>of();
        BasicLocationDefinition locDefinition = new BasicLocationDefinition(symbolicName, symbolicName, spec, config);
        
        updateDefinedLocation(locDefinition);
    }

    public void removeDefinedLocation(CatalogItem<Location, LocationSpec<?>> item) {
        removeDefinedLocation(item.getSymbolicName());
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
            // (would be nice to move to a better way, e.g. yaml, then deprecate this approach, but first
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
            
            for (CatalogItem<Location, LocationSpec<?>> item : mgmt.getCatalog().getCatalogItems(CatalogPredicates.IS_LOCATION)) {
                updateDefinedLocation(item);
                count++;
            }
        }
    }
    
    @VisibleForTesting
    void disablePersistence() {
        // persistence isn't enabled yet anyway (have to manually save things,
        // defining the format and file etc)
    }

    protected static BasicLocationDefinition localhost(String id) {
        return new BasicLocationDefinition(id, "localhost", "localhost", null);
    }
    
    /** to catch circular references */
    protected ThreadLocal<Set<String>> specsSeen = new ThreadLocal<Set<String>>();
    
    @Override @Deprecated
    public boolean canMaybeResolve(String spec) {
        return getSpecResolver(spec) != null;
    }

    @Override
    public final Location resolve(String spec) {
        return resolve(spec, true, null).get();
    }
    
    @Override @Deprecated
    public final Location resolveIfPossible(String spec) {
        if (!canMaybeResolve(spec)) return null;
        return resolve(spec, null, null).orNull();
    }
    
    @Deprecated /** since 0.7.0 not used */
    public final Maybe<Location> resolve(String spec, boolean manage) {
        return resolve(spec, manage, null);
    }
    
    public Maybe<Location> resolve(String spec, Boolean manage, Map locationFlags) {
        try {
            locationFlags = MutableMap.copyOf(locationFlags);
            if (manage!=null) {
                locationFlags.put(LocalLocationManager.CREATE_UNMANAGED, !manage);
            }
            
            Set<String> seenSoFar = specsSeen.get();
            if (seenSoFar==null) {
                seenSoFar = new LinkedHashSet<String>();
                specsSeen.set(seenSoFar);
            }
            if (seenSoFar.contains(spec))
                return Maybe.absent(Suppliers.ofInstance(new IllegalStateException("Circular reference in definition of location '"+spec+"' ("+seenSoFar+")")));
            seenSoFar.add(spec);
            
            LocationResolver resolver = getSpecResolver(spec);

            if (resolver != null) {
                try {
                    return Maybe.of(resolver.newLocationFromString(locationFlags, spec, this));
                } catch (RuntimeException e) {
                    return Maybe.absent(Suppliers.ofInstance(e));
                }
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
                    log.debug("Location resolution failed for '"+spec+"' (if this is being loaded it will fail shortly): known resolvers are: "+resolvers.keySet());
                    errmsg = "Unknown location '"+spec+"': "
                            + "either this location is not recognised or there is a problem with location resolver configuration.";
                }
            } else {
                // For helpful log message construction: assumes classpath will not suddenly become wrong; might happen with OSGi though!
                if (log.isDebugEnabled()) log.debug("Location resolution failed again for '"+spec+"' (throwing)");
                errmsg = "Unknown location '"+spec+"': "
                        + "either this location is not recognised or there is a problem with location resolver configuration.";
            }

            return Maybe.absent(Suppliers.ofInstance(new NoSuchElementException(errmsg)));

        } finally {
            specsSeen.remove();
        }
    }

    @Override
    public final Location resolve(String spec, Map locationFlags) {
        return resolve(spec, null, locationFlags).get();
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
        return resolve(ld, null, null).get();
    }

    @Override @Deprecated
    public Location resolveForPeeking(LocationDefinition ld) {
        // TODO should clean up how locations are stored, figuring out whether they are shared or not;
        // or maybe better, the API calls to this might just want to get the LocationSpec objects back
        
        // for now we use a 'CREATE_UNMANGED' flag to prevent management (leaks and logging)
        return resolve(ld, ConfigBag.newInstance().configure(LocalLocationManager.CREATE_UNMANAGED, true).getAllConfig());
    }

    @Override @Deprecated
    public Location resolve(LocationDefinition ld, Map<?,?> flags) {
        return resolveLocationDefinition(ld, flags, null);
    }
    
    /** @deprecated since 0.7.0 not used (and optionalName was ignored anyway) */
    @Deprecated
    public Location resolveLocationDefinition(LocationDefinition ld, Map locationFlags, String optionalName) {
        return resolve(ld, null, locationFlags).get();
    }
    
    public Maybe<Location> resolve(LocationDefinition ld, Boolean manage, Map locationFlags) {
        ConfigBag newLocationFlags = ConfigBag.newInstance(ld.getConfig())
            .putAll(locationFlags)
            .putIfAbsentAndNotNull(LocationInternal.NAMED_SPEC_NAME, ld.getName())
            .putIfAbsentAndNotNull(LocationInternal.ORIGINAL_SPEC, ld.getName());
        Maybe<Location> result = resolve(ld.getSpec(), manage, newLocationFlags.getAllConfigRaw());
        if (result.isPresent()) 
            return result;
        throw new IllegalStateException("Cannot instantiate location '"+ld+"' pointing at "+ld.getSpec()+": "+
            Exceptions.collapseText( ((Absent<?>)result).getException() ));
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
                BasicLocationRegistry.localhost(Identifiers.makeRandomId(8)) );
        
        ((BasicLocationRegistry)mgmt.getLocationRegistry()).disablePersistence();
    }

}

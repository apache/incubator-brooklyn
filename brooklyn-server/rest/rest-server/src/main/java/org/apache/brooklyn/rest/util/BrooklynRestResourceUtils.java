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
package org.apache.brooklyn.rest.util;

import static com.google.common.collect.Iterables.transform;
import static org.apache.brooklyn.rest.util.WebResourceUtils.notFound;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Future;

import javax.ws.rs.core.MediaType;

import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationRegistry;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.methods.DslComponent;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.methods.DslComponent.Scope;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.catalog.CatalogPredicates;
import org.apache.brooklyn.core.catalog.internal.CatalogItemComparator;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.entitlement.Entitlements;
import org.apache.brooklyn.core.mgmt.entitlement.Entitlements.StringAndArgument;
import org.apache.brooklyn.core.objs.BrooklynTypes;
import org.apache.brooklyn.core.typereg.RegisteredTypes;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.rest.domain.ApplicationSpec;
import org.apache.brooklyn.rest.domain.EntitySpec;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.javalang.Reflections;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

public class BrooklynRestResourceUtils {

    private static final Logger log = LoggerFactory.getLogger(BrooklynRestResourceUtils.class);

    private final ManagementContext mgmt;
    
    public BrooklynRestResourceUtils(ManagementContext mgmt) {
        Preconditions.checkNotNull(mgmt, "mgmt");
        this.mgmt = mgmt;
    }

    public BrooklynCatalog getCatalog() {
        return mgmt.getCatalog();
    }
    
    public ClassLoader getCatalogClassLoader() {
        return mgmt.getCatalogClassLoader();
    }
    
    public LocationRegistry getLocationRegistry() {
        return mgmt.getLocationRegistry();
    }

    /** finds the policy indicated by the given ID or name.
     * @see {@link #getEntity(String,String)}; it then searches the policies of that
     * entity for one whose ID or name matches that given.
     * <p>
     * 
     * @throws 404 or 412 (unless input is null in which case output is null) */
    public Policy getPolicy(String application, String entity, String policy) {
        return getPolicy(getEntity(application, entity), policy);
    }

    /** finds the policy indicated by the given ID or name.
     * @see {@link #getPolicy(String,String,String)}.
     * <p>
     * 
     * @throws 404 or 412 (unless input is null in which case output is null) */
    public Policy getPolicy(Entity entity, String policy) {
        if (policy==null) return null;

        for (Policy p: entity.policies()) {
            if (policy.equals(p.getId())) return p;
        }
        for (Policy p: entity.policies()) {
            if (policy.equals(p.getDisplayName())) return p;
        }
        
        throw WebResourceUtils.notFound("Cannot find policy '%s' in entity '%s'", policy, entity);
    }

    /** finds the entity indicated by the given ID or name
     * <p>
     * prefers ID based lookup in which case appId is optional, and if supplied will be enforced.
     * optionally the name can be supplied, for cases when paths should work across versions,
     * in which case names will be searched recursively (and the application is required). 
     * 
     * @throws 404 or 412 (unless input is null in which case output is null) */
    public Entity getEntity(String application, String entity) {
        if (entity==null) return null;
        Application app = application!=null ? getApplication(application) : null;
        Entity e = mgmt.getEntityManager().getEntity(entity);
        
        if (e!=null) {
            if (!Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_ENTITY, e)) {
                throw WebResourceUtils.notFound("Cannot find entity '%s': no known ID and application not supplied for searching", entity);
            }
            
            if (app==null || app.equals(findTopLevelApplication(e))) return e;
            throw WebResourceUtils.preconditionFailed("Application '%s' specified does not match application '%s' to which entity '%s' (%s) is associated", 
                    application, e.getApplication()==null ? null : e.getApplication().getId(), entity, e);
        }
        if (application==null)
            throw WebResourceUtils.notFound("Cannot find entity '%s': no known ID and application not supplied for searching", entity);
        
        assert app!=null : "null app should not be returned from getApplication";
        e = searchForEntityNamed(app, entity);
        if (e!=null) return e;
        throw WebResourceUtils.notFound("Cannot find entity '%s' in application '%s' (%s)", entity, application, app);
    }
    
    private Application findTopLevelApplication(Entity e) {
        // For nested apps, e.getApplication() can return its direct parent-app rather than the root app
        // (particularly if e.getApplication() was called before the parent-app was wired up to its parent,
        // because that call causes the application to be cached).
        // Therefore we continue to walk the hierarchy until we find an "orphaned" application at the top.
        
        Application app = e.getApplication();
        while (app != null && !app.equals(app.getApplication())) {
            app = app.getApplication();
        }
        return app;
    }

    /** looks for the given application instance, first by ID then by name
     * 
     * @throws 404 if not found, or not entitled
     */
    public Application getApplication(String application) {
        Entity e = mgmt.getEntityManager().getEntity(application);
        if (!Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_ENTITY, e)) {
            throw notFound("Application '%s' not found", application);
        }
        
        if (e != null && e instanceof Application) return (Application) e;
        for (Application app : mgmt.getApplications()) {
            if (app.getId().equals(application)) return app;
            if (application.equalsIgnoreCase(app.getDisplayName())) return app;
        }
        
        throw notFound("Application '%s' not found", application);
    }

    /** walks the hierarchy (depth-first) at root (often an Application) looking for
     * an entity matching the given ID or name; returns the first such entity, or null if none found
     **/
    public Entity searchForEntityNamed(Entity root, String entity) {
        if (root.getId().equals(entity) || entity.equals(root.getDisplayName())) return root;
        for (Entity child: root.getChildren()) {
            Entity result = searchForEntityNamed(child, entity);
            if (result!=null) return result;
        }
        return null;
    }

    private class FindItemAndClass {
        String catalogItemId;
        Class<? extends Entity> clazz;
        
        @SuppressWarnings({ "unchecked" })
        private FindItemAndClass inferFrom(String type) {
            RegisteredType item = mgmt.getTypeRegistry().get(type);
            if (item==null) {
                // deprecated attempt to load an item not in the type registry
                
                // although the method called was deprecated in 0.7.0, its use here was not warned until 0.9.0;
                // therefore this behaviour should not be changed until after 0.9.0;
                // at which point it should try a pojo load (see below)
                item = getCatalogItemForType(type);
                if (item!=null) {
                    log.warn("Creating application for requested type `"+type+" using item "+item+"; "
                        + "the registered type name ("+item.getSymbolicName()+") should be used from the spec instead, "
                        + "or the type registered under its own name. "
                        + "Future versions will likely change semantics to attempt a POJO load of the type instead.");
                }
            }
            
            if (item != null) {
                return setAs(
                    mgmt.getTypeRegistry().createSpec(item, null, org.apache.brooklyn.api.entity.EntitySpec.class).getType(),
                    item.getId());
            } else {
                try {
                    setAs(
                        (Class<? extends Entity>) getCatalog().getRootClassLoader().loadClass(type),
                        null);
                    log.info("Catalog does not contain item for type {}; loaded class directly instead", type);
                    return this;
                } catch (ClassNotFoundException e2) {
                    log.warn("No catalog item for type {}, and could not load class directly; rethrowing", type);
                    throw new NoSuchElementException("Unable to find catalog item for type "+type);
                }
            }
        }

        private FindItemAndClass setAs(Class<? extends Entity> clazz, String catalogItemId) {
            this.clazz = clazz;
            this.catalogItemId = catalogItemId;
            return this;
        }
        
        @Deprecated // see caller
        private RegisteredType getCatalogItemForType(String typeName) {
            final RegisteredType resultI;
            if (CatalogUtils.looksLikeVersionedId(typeName)) {
                //All catalog identifiers of the form xxxx:yyyy are composed of symbolicName+version.
                //No javaType is allowed as part of the identifier.
                resultI = mgmt.getTypeRegistry().get(typeName);
            } else {
                //Usually for catalog items with javaType (that is items from catalog.xml)
                //the symbolicName and javaType match because symbolicName (was ID)
                //is not specified explicitly. But could be the case that there is an item
                //whose symbolicName is explicitly set to be different from the javaType.
                //Note that in the XML the attribute is called registeredTypeName.
                Iterable<CatalogItem<Object,Object>> resultL = mgmt.getCatalog().getCatalogItems(CatalogPredicates.javaType(Predicates.equalTo(typeName)));
                if (!Iterables.isEmpty(resultL)) {
                    //Push newer versions in front of the list (not that there should
                    //be more than one considering the items are coming from catalog.xml).
                    resultI = RegisteredTypes.of(sortVersionsDesc(resultL).iterator().next());
                    if (log.isDebugEnabled() && Iterables.size(resultL)>1) {
                        log.debug("Found "+Iterables.size(resultL)+" matches in catalog for type "+typeName+"; returning the result with preferred version, "+resultI);
                    }
                } else {
                    //As a last resort try searching for items with the same symbolicName supposedly
                    //different from the javaType.
                    resultI = mgmt.getTypeRegistry().get(typeName, BrooklynCatalog.DEFAULT_VERSION);
                    if (resultI != null) {
                        if (resultI.getSuperTypes().isEmpty()) {
                            //Catalog items scanned from the classpath (using reflection and annotations) now
                            //get yaml spec rather than a java type. Can't use those when creating apps from
                            //the legacy app spec format.
                            log.warn("Unable to find catalog item for type "+typeName +
                                    ". There is an existing catalog item with ID " + resultI.getId() +
                                    " but it doesn't define a class type.");
                            return null;
                        }
                    }
                }
            }
            return resultI;
        }
        private <T,SpecT> Collection<CatalogItem<T,SpecT>> sortVersionsDesc(Iterable<CatalogItem<T,SpecT>> versions) {
            return ImmutableSortedSet.orderedBy(CatalogItemComparator.<T,SpecT>getInstance()).addAll(versions).build();
        }
    }
    
    @SuppressWarnings({ "deprecation" })
    public Application create(ApplicationSpec spec) {
        log.warn("Using deprecated functionality (as of 0.9.0), ApplicationSpec style (pre CAMP plans). " +
                    "Transition to actively supported spec plans.");
        log.debug("REST creating application instance for {}", spec);
        
        if (!Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.DEPLOY_APPLICATION, spec)) {
            throw WebResourceUtils.unauthorized("User '%s' is not authorized to deploy application %s",
                Entitlements.getEntitlementContext().user(), spec);
        }
        
        final String type = spec.getType();
        final String name = spec.getName();
        final Map<String,String> configO = spec.getConfig();
        final Set<EntitySpec> entities = (spec.getEntities() == null) ? ImmutableSet.<EntitySpec>of() : spec.getEntities();
        
        final Application instance;

        // Load the class; first try to use the appropriate catalog item; but then allow anything that is on the classpath
        FindItemAndClass itemAndClass;
        if (Strings.isEmpty(type)) {
            itemAndClass = new FindItemAndClass().setAs(BasicApplication.class, null);
        } else {
            itemAndClass = new FindItemAndClass().inferFrom(type);
        }
        
        if (!Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.INVOKE_EFFECTOR, null)) {
            throw WebResourceUtils.unauthorized("User '%s' is not authorized to create application from applicationSpec %s",
                Entitlements.getEntitlementContext().user(), spec);
        }

        try {
            if (org.apache.brooklyn.core.entity.factory.ApplicationBuilder.class.isAssignableFrom(itemAndClass.clazz)) {
                // warning only added in 0.9.0
                log.warn("Using deprecated ApplicationBuilder "+itemAndClass.clazz+"; callers must migrate to use of Application");
                Constructor<?> constructor = itemAndClass.clazz.getConstructor();
                org.apache.brooklyn.core.entity.factory.ApplicationBuilder appBuilder = (org.apache.brooklyn.core.entity.factory.ApplicationBuilder) constructor.newInstance();
                if (!Strings.isEmpty(name)) appBuilder.appDisplayName(name);
                if (entities.size() > 0)
                    log.warn("Cannot supply additional entities when using an ApplicationBuilder; ignoring in spec {}", spec);

                log.info("REST placing '{}' under management", spec.getName());
                appBuilder.configure(convertFlagsToKeys(appBuilder.getType(), configO));
                configureRenderingMetadata(spec, appBuilder);
                instance = appBuilder.manage(mgmt);

            } else if (Application.class.isAssignableFrom(itemAndClass.clazz)) {
                org.apache.brooklyn.api.entity.EntitySpec<?> coreSpec = toCoreEntitySpec(itemAndClass.clazz, name, configO, itemAndClass.catalogItemId);
                configureRenderingMetadata(spec, coreSpec);
                for (EntitySpec entitySpec : entities) {
                    log.info("REST creating instance for entity {}", entitySpec.getType());
                    coreSpec.child(toCoreEntitySpec(entitySpec));
                }

                log.info("REST placing '{}' under management", spec.getName() != null ? spec.getName() : spec);
                instance = (Application) mgmt.getEntityManager().createEntity(coreSpec);

            } else if (Entity.class.isAssignableFrom(itemAndClass.clazz)) {
                if (entities.size() > 0)
                    log.warn("Cannot supply additional entities when using a non-application entity; ignoring in spec {}", spec);

                org.apache.brooklyn.api.entity.EntitySpec<?> coreSpec = toCoreEntitySpec(BasicApplication.class, name, configO, itemAndClass.catalogItemId);
                configureRenderingMetadata(spec, coreSpec);

                coreSpec.child(toCoreEntitySpec(itemAndClass.clazz, name, configO, itemAndClass.catalogItemId)
                    .configure(BrooklynCampConstants.PLAN_ID, "soleChildId"));
                coreSpec.enricher(Enrichers.builder()
                    .propagatingAllBut(Attributes.SERVICE_UP, Attributes.SERVICE_NOT_UP_INDICATORS, 
                        Attributes.SERVICE_STATE_ACTUAL, Attributes.SERVICE_STATE_EXPECTED, 
                        Attributes.SERVICE_PROBLEMS)
                        .from(new DslComponent(Scope.CHILD, "soleChildId").newTask())
                        .build());

                log.info("REST placing '{}' under management", spec.getName());
                instance = (Application) mgmt.getEntityManager().createEntity(coreSpec);

            } else {
                throw new IllegalArgumentException("Class " + itemAndClass.clazz + " must extend one of ApplicationBuilder, Application or Entity");
            }

            return instance;

        } catch (Exception e) {
            log.error("REST failed to create application: " + e, e);
            throw Exceptions.propagate(e);
        }
    }
    
    public Task<?> start(Application app, ApplicationSpec spec) {
        return start(app, getLocations(spec));
    }

    public Task<?> start(Application app, List<? extends Location> locations) {
        return Entities.invokeEffector(app, app, Startable.START,
                MutableMap.of("locations", locations));
    }

    public List<Location> getLocations(ApplicationSpec spec) {
        // Start all the managed entities by asking the app instance to start in background
        Function<String, Location> buildLocationFromId = new Function<String, Location>() {
            @Override
            public Location apply(String id) {
                id = fixLocation(id);
                return getLocationRegistry().resolve(id);
            }
        };

        ArrayList<Location> locations = Lists.newArrayList(transform(spec.getLocations(), buildLocationFromId));
        return locations;
    }

    private org.apache.brooklyn.api.entity.EntitySpec<? extends Entity> toCoreEntitySpec(org.apache.brooklyn.rest.domain.EntitySpec spec) {
        String type = spec.getType();
        String name = spec.getName();
        Map<String, String> config = (spec.getConfig() == null) ? Maps.<String,String>newLinkedHashMap() : Maps.newLinkedHashMap(spec.getConfig());

        FindItemAndClass itemAndClass = new FindItemAndClass().inferFrom(type);
        
        final Class<? extends Entity> clazz = itemAndClass.clazz;
        org.apache.brooklyn.api.entity.EntitySpec<? extends Entity> result;
        if (clazz.isInterface()) {
            result = org.apache.brooklyn.api.entity.EntitySpec.create(clazz);
        } else {
            result = org.apache.brooklyn.api.entity.EntitySpec.create(Entity.class).impl(clazz).additionalInterfaces(Reflections.getAllInterfaces(clazz));
        }
        result.catalogItemId(itemAndClass.catalogItemId);
        if (!Strings.isEmpty(name)) result.displayName(name);
        result.configure( convertFlagsToKeys(result.getType(), config) );
        configureRenderingMetadata(spec, result);
        return result;
    }
    
    @SuppressWarnings("deprecation")
    protected void configureRenderingMetadata(ApplicationSpec spec, org.apache.brooklyn.core.entity.factory.ApplicationBuilder appBuilder) {
        appBuilder.configure(getRenderingConfigurationFor(spec.getType()));
    }

    protected void configureRenderingMetadata(ApplicationSpec input, org.apache.brooklyn.api.entity.EntitySpec<?> entity) {
        entity.configure(getRenderingConfigurationFor(input.getType()));
    }

    protected void configureRenderingMetadata(EntitySpec input, org.apache.brooklyn.api.entity.EntitySpec<?> entity) {
        entity.configure(getRenderingConfigurationFor(input.getType()));
    }

    protected Map<?, ?> getRenderingConfigurationFor(String catalogId) {
        MutableMap<Object, Object> result = MutableMap.of();
        RegisteredType item = mgmt.getTypeRegistry().get(catalogId);
        if (item==null) return result;
        
        result.addIfNotNull("iconUrl", item.getIconUrl());
        return result;
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private <T extends Entity> org.apache.brooklyn.api.entity.EntitySpec<?> toCoreEntitySpec(Class<T> clazz, String name, Map<String,String> configO, String catalogItemId) {
        Map<String, String> config = (configO == null) ? Maps.<String,String>newLinkedHashMap() : Maps.newLinkedHashMap(configO);
        
        org.apache.brooklyn.api.entity.EntitySpec<? extends Entity> result;
        if (clazz.isInterface()) {
            result = org.apache.brooklyn.api.entity.EntitySpec.create(clazz);
        } else {
            // If this is a concrete class, particularly for an Application class, we want the proxy
            // to expose all interfaces it implements.
            Class interfaceclazz = (Application.class.isAssignableFrom(clazz)) ? Application.class : Entity.class;
            Set<Class<?>> additionalInterfaceClazzes = Reflections.getInterfacesIncludingClassAncestors(clazz);
            result = org.apache.brooklyn.api.entity.EntitySpec.create(interfaceclazz).impl(clazz).additionalInterfaces(additionalInterfaceClazzes);
        }
        
        result.catalogItemId(catalogItemId);
        if (!Strings.isEmpty(name)) result.displayName(name);
        result.configure( convertFlagsToKeys(result.getImplementation(), config) );
        return result;
    }

    private Map<?,?> convertFlagsToKeys(Class<? extends Entity> javaType, Map<?, ?> config) {
        if (config==null || config.isEmpty() || javaType==null) return config;
        
        Map<String, ConfigKey<?>> configKeys = BrooklynTypes.getDefinedConfigKeys(javaType);
        Map<Object,Object> result = new LinkedHashMap<Object,Object>();
        for (Map.Entry<?,?> entry: config.entrySet()) {
            log.debug("Setting key {} to {} for REST creation of {}", new Object[] { entry.getKey(), entry.getValue(), javaType});
            Object key = configKeys.get(entry.getKey());
            if (key==null) {
                log.warn("Unrecognised config key {} passed to {}; will be treated as flag (and likely ignored)", entry.getKey(), javaType);
                key = entry.getKey();
            }
            result.put(key, entry.getValue());
        }
        return result;
    }
    
    public Task<?> destroy(final Application application) {
        return mgmt.getExecutionManager().submit(
                MutableMap.of("displayName", "destroying "+application,
                        "description", "REST call to destroy application "+application.getDisplayName()+" ("+application+")"),
                new Runnable() {
            @Override
            public void run() {
                ((EntityInternal)application).destroy();
                mgmt.getEntityManager().unmanage(application);
            }
        });
    }
    
    public Task<?> expunge(final Entity entity, final boolean release) {
        if (mgmt.getEntitlementManager().isEntitled(Entitlements.getEntitlementContext(),
                Entitlements.INVOKE_EFFECTOR, Entitlements.EntityAndItem.of(entity, 
                    StringAndArgument.of("expunge", MutableMap.of("release", release))))) {
            Map<String, Object> flags = MutableMap.<String, Object>of("displayName", "expunging " + entity, "description", "REST call to expunge entity "
                    + entity.getDisplayName() + " (" + entity + ")");
            if (Entitlements.getEntitlementContext() != null) {
                flags.put("tags", MutableSet.of(BrooklynTaskTags.tagForEntitlement(Entitlements.getEntitlementContext())));
            }
            return mgmt.getExecutionManager().submit(
                    flags, new Runnable() {
                        @Override
                        public void run() {
                            if (release)
                                Entities.destroyCatching(entity);
                            else
                                mgmt.getEntityManager().unmanage(entity);
                        }
                    });
        }
        throw WebResourceUtils.unauthorized("User '%s' is not authorized to expunge entity %s",
                    Entitlements.getEntitlementContext().user(), entity);
    }

    @Deprecated
    public static String fixLocation(String locationId) {
        if (locationId.startsWith("/v1/locations/")) {
            log.warn("REST API using legacy URI syntax for location: "+locationId);
            locationId = Strings.removeFromStart(locationId, "/v1/locations/");
        }
        return locationId;
    }

    public Object getObjectValueForDisplay(Object value) {
        if (value==null) return null;
        // currently everything converted to string, expanded if it is a "done" future
        if (value instanceof Future) {
            if (((Future<?>)value).isDone()) {
                try {
                    value = ((Future<?>)value).get();
                } catch (Exception e) {
                    value = ""+value+" (error evaluating: "+e+")";
                }
            }
        }
        
        if (TypeCoercions.isPrimitiveOrBoxer(value.getClass())) return value;
        return value.toString();
    }

    // currently everything converted to string, expanded if it is a "done" future
    public String getStringValueForDisplay(Object value) {
        if (value==null) return null;
        return ""+getObjectValueForDisplay(value);
    }

    /** true if the URL points to content which must be resolved on the server-side (i.e. classpath)
     *  and which is safe to do so (currently just images, though in future perhaps also javascript and html plugins)
     *  <p>
     *  note we do not let caller access classpath through this mechanism, 
     *  just those which are supplied by the platform administrator e.g. as an icon url */
    public boolean isUrlServerSideAndSafe(String url) {
        if (Strings.isEmpty(url)) return false;
        String ext = Files.getFileExtension(url);
        if (Strings.isEmpty(ext)) return false;
        MediaType mime = WebResourceUtils.getImageMediaTypeFromExtension(ext);
        if (mime==null) return false;
        
        return !Urls.isUrlWithProtocol(url) || url.startsWith("classpath:");
    }

    
    public Iterable<Entity> descendantsOfAnyType(String application, String entity) {
        List<Entity> result = Lists.newArrayList();
        Entity e = getEntity(application, entity);
        gatherAllDescendants(e, result);
        return result;
    }
    
    private static void gatherAllDescendants(Entity e, List<Entity> result) {
        if (result.add(e)) {
            for (Entity ee: e.getChildren())
                gatherAllDescendants(ee, result);
        }
    }

    public Iterable<Entity> descendantsOfType(String application, String entity, final String typeRegex) {
        Iterable<Entity> result = descendantsOfAnyType(application, entity);
        return Iterables.filter(result, new Predicate<Entity>() {
            @Override
            public boolean apply(Entity entity) {
                if (entity==null) return false;
                return (entity.getEntityType().getName().matches(typeRegex));
            }
        });
    }

    public void reloadBrooklynProperties() {
        mgmt.reloadBrooklynProperties();
    }
}

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
package brooklyn.rest.util;

import static brooklyn.rest.util.WebResourceUtils.notFound;
import static com.google.common.collect.Iterables.transform;
import groovy.lang.GroovyClassLoader;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Future;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.config.ConfigKey;
import brooklyn.enricher.Enrichers;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.EntityTypes;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.EntitySpec;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.net.Urls;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
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
    
    public LocationRegistry getLocationRegistry() {
        return mgmt.getLocationRegistry();
    }

    /** finds the policy indicated by the given ID or name.
     * @see {@link getEntity(String,String)}; it then searches the policies of that
     * entity for one whose ID or name matches that given.
     * <p>
     * 
     * @throws 404 or 412 (unless input is null in which case output is null) */
    public Policy getPolicy(String application, String entity, String policy) {
        return getPolicy(getEntity(application, entity), policy);
    }

    /** finds the policy indicated by the given ID or name.
     * @see {@link getPolicy(String,String,String)}.
     * <p>
     * 
     * @throws 404 or 412 (unless input is null in which case output is null) */
    public Policy getPolicy(Entity entity, String policy) {
        if (policy==null) return null;

        for (Policy p: entity.getPolicies()) {
            if (policy.equals(p.getId())) return p;
        }
        for (Policy p: entity.getPolicies()) {
            if (policy.equals(p.getName())) return p;
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
    public EntityLocal getEntity(String application, String entity) {
        if (entity==null) return null;
        Application app = application!=null ? getApplication(application) : null;
        EntityLocal e = (EntityLocal) mgmt.getEntityManager().getEntity(entity);
        
        if (e!=null) {
            if (!Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.SEE_ENTITY, e)) {
                throw WebResourceUtils.notFound("Cannot find entity '%s': no known ID and application not supplied for searching", entity);
            }
            
            if (app==null || app.equals(findTopLevelApplication(e))) return e;
            throw WebResourceUtils.preconditionFailed("Application '%s' specified does not match application '%s' to which entity '%s' (%s) is associated", 
                    application, e.getApplication().getId(), entity, e);
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
    public EntityLocal searchForEntityNamed(Entity root, String entity) {
        if (root.getId().equals(entity) || entity.equals(root.getDisplayName())) return (EntityLocal) root;
        for (Entity child: root.getChildren()) {
            Entity result = searchForEntityNamed(child, entity);
            if (result!=null) return (EntityLocal) result;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Application create(ApplicationSpec spec) {
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
        final Class<? extends Entity> clazz;
        if (Strings.isEmpty(type)) {
            clazz = BasicApplication.class;
        } else {
            Class<? extends Entity> tempclazz;
            try {
                tempclazz = getCatalog().loadClassByType(type, Entity.class);
            } catch (NoSuchElementException e) {
                try {
                    tempclazz = (Class<? extends Entity>) getCatalog().getRootClassLoader().loadClass(type);
                    log.info("Catalog does not contain item for type {}; loaded class directly instead", type);
                } catch (ClassNotFoundException e2) {
                    log.warn("No catalog item for type {}, and could not load class directly; rethrowing", type);
                    throw e;
                }
            }
            clazz = tempclazz;
        }
        if (Entitlements.isEntitled(mgmt.getEntitlementManager(), Entitlements.INVOKE_EFFECTOR, null)) {

            try {
                if (ApplicationBuilder.class.isAssignableFrom(clazz)) {
                    Constructor<?> constructor = clazz.getConstructor();
                    ApplicationBuilder appBuilder = (ApplicationBuilder) constructor.newInstance();
                    if (!Strings.isEmpty(name)) appBuilder.appDisplayName(name);
                    if (entities.size() > 0)
                        log.warn("Cannot supply additional entities when using an ApplicationBuilder; ignoring in spec {}", spec);

                    log.info("REST placing '{}' under management", spec.getName());
                    appBuilder.configure(convertFlagsToKeys(appBuilder.getType(), configO));
                    configureRenderingMetadata(spec, appBuilder);
                    instance = appBuilder.manage(mgmt);

                } else if (Application.class.isAssignableFrom(clazz)) {
                    brooklyn.entity.proxying.EntitySpec<?> coreSpec = toCoreEntitySpec(clazz, name, configO);
                    configureRenderingMetadata(spec, coreSpec);
                    instance = (Application) mgmt.getEntityManager().createEntity(coreSpec);
                    for (EntitySpec entitySpec : entities) {
                        log.info("REST creating instance for entity {}", entitySpec.getType());
                        instance.addChild(mgmt.getEntityManager().createEntity(toCoreEntitySpec(entitySpec)));
                    }

                    log.info("REST placing '{}' under management", spec.getName() != null ? spec.getName() : spec);
                    Entities.startManagement(instance, mgmt);

                } else if (Entity.class.isAssignableFrom(clazz)) {
                    if (entities.size() > 0)
                        log.warn("Cannot supply additional entities when using a non-application entity; ignoring in spec {}", spec);

                    brooklyn.entity.proxying.EntitySpec<?> coreSpec = toCoreEntitySpec(BasicApplication.class, name, configO);
                    configureRenderingMetadata(spec, coreSpec);

                    instance = (Application) mgmt.getEntityManager().createEntity(coreSpec);

                    final Class<? extends Entity> eclazz = getCatalog().loadClassByType(spec.getType(), Entity.class);
                    Entity soleChild = mgmt.getEntityManager().createEntity(toCoreEntitySpec(eclazz, name, configO));
                    instance.addChild(soleChild);
                    instance.addEnricher(Enrichers.builder()
                            .propagatingAll()
                            .from(soleChild)
                            .build());

                    log.info("REST placing '{}' under management", spec.getName());
                    Entities.startManagement(instance, mgmt);

                } else {
                    throw new IllegalArgumentException("Class " + clazz + " must extend one of ApplicationBuilder, Application or Entity");
                }

                return instance;

            } catch (Exception e) {
                log.error("REST failed to create application: " + e, e);
                throw Exceptions.propagate(e);
            }
        }
        throw WebResourceUtils.unauthorized("User '%s' is not authorized to create application from applicationSpec %s",
                Entitlements.getEntitlementContext().user(), spec);
    }
    
    public Task<?> start(Application app, ApplicationSpec spec) {
        return start(app, getLocations(spec));
    }

    public Task<?> start(Application app, List<? extends Location> locations) {
        return Entities.invokeEffector((EntityLocal)app, app, Startable.START,
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

    @SuppressWarnings("unchecked")
    private brooklyn.entity.proxying.EntitySpec<? extends Entity> toCoreEntitySpec(brooklyn.rest.domain.EntitySpec spec) {
        String type = spec.getType();
        String name = spec.getName();
        Map<String, String> config = (spec.getConfig() == null) ? Maps.<String,String>newLinkedHashMap() : Maps.newLinkedHashMap(spec.getConfig());

        Class<? extends Entity> tempclazz;
        try {
            tempclazz = getCatalog().loadClassByType(type, Entity.class);
        } catch (NoSuchElementException e) {
            try {
                tempclazz = (Class<? extends Entity>) getCatalog().getRootClassLoader().loadClass(type);
                log.info("Catalog does not contain item for type {}; loaded class directly instead", type);
            } catch (ClassNotFoundException e2) {
                log.warn("No catalog item for type {}, and could not load class directly; rethrowing", type);
                throw e;
            }
        }
        final Class<? extends Entity> clazz = tempclazz;
        brooklyn.entity.proxying.EntitySpec<? extends Entity> result;
        if (clazz.isInterface()) {
            result = brooklyn.entity.proxying.EntitySpec.create(clazz);
        } else {
            result = brooklyn.entity.proxying.EntitySpec.create(Entity.class).impl(clazz);
        }
        if (!Strings.isEmpty(name)) result.displayName(name);
        result.configure( convertFlagsToKeys(result.getType(), config) );
        configureRenderingMetadata(spec, result);
        return result;
    }
    
    protected void configureRenderingMetadata(ApplicationSpec spec, ApplicationBuilder appBuilder) {
        appBuilder.configure(getRenderingConfigurationFor(spec.getType()));
    }

    protected void configureRenderingMetadata(ApplicationSpec input, brooklyn.entity.proxying.EntitySpec<?> entity) {
        entity.configure(getRenderingConfigurationFor(input.getType()));
    }

    protected void configureRenderingMetadata(EntitySpec input, brooklyn.entity.proxying.EntitySpec<?> entity) {
        entity.configure(getRenderingConfigurationFor(input.getType()));
    }

    protected Map<?, ?> getRenderingConfigurationFor(String catalogId) {
        MutableMap<Object, Object> result = MutableMap.of();
        CatalogItem<?,?> item = mgmt.getCatalog().getCatalogItem(catalogId);
        if (item==null) return result;
        
        result.addIfNotNull("iconUrl", item.getIconUrl());
        return result;
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private <T extends Entity> brooklyn.entity.proxying.EntitySpec<?> toCoreEntitySpec(Class<T> clazz, String name, Map<String,String> configO) {
        Map<String, String> config = (configO == null) ? Maps.<String,String>newLinkedHashMap() : Maps.newLinkedHashMap(configO);
        
        brooklyn.entity.proxying.EntitySpec<? extends Entity> result;
        if (clazz.isInterface()) {
            result = brooklyn.entity.proxying.EntitySpec.create(clazz);
        } else {
            // If this is a concrete class, particularly for an Application class, we want the proxy
            // to expose all interfaces it implements.
            Class interfaceclazz = (Application.class.isAssignableFrom(clazz)) ? Application.class : Entity.class;
            Set<Class<?>> additionalInterfaceClazzes = Reflections.getInterfacesIncludingClassAncestors(clazz);
            result = brooklyn.entity.proxying.EntitySpec.create(interfaceclazz).impl(clazz).additionalInterfaces(additionalInterfaceClazzes);
        }
        
        if (!Strings.isEmpty(name)) result.displayName(name);
        result.configure( convertFlagsToKeys(result.getImplementation(), config) );
        return result;
    }

    private Map<?,?> convertFlagsToKeys(Class<? extends Entity> javaType, Map<?, ?> config) {
        if (config==null || config.isEmpty() || javaType==null) return config;
        
        Map<String, ConfigKey<?>> configKeys = EntityTypes.getDefinedConfigKeys(javaType);
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
                Entitlements.INVOKE_EFFECTOR, Entitlements.EntityAndItem.of(entity, "expunge"))) {
            return mgmt.getExecutionManager().submit(
                    MutableMap.of("displayName", "expunging " + entity, "description", "REST call to expunge entity "
                            + entity.getDisplayName() + " (" + entity + ")"), new Runnable() {
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
    @SuppressWarnings({ "rawtypes" })
    public Response createCatalogEntryFromGroovyCode(String groovyCode) {
        ClassLoader parent = getCatalog().getRootClassLoader();
        GroovyClassLoader loader = new GroovyClassLoader(parent);

        Class clazz = loader.parseClass(groovyCode);

        if (AbstractEntity.class.isAssignableFrom(clazz)) {
            CatalogItem<?,?> item = getCatalog().addItem(clazz);
            log.info("REST created "+item);
            return Response.created(URI.create("entities/" + clazz.getName())).build();

        } else if (AbstractPolicy.class.isAssignableFrom(clazz)) {
            CatalogItem<?,?> item = getCatalog().addItem(clazz);
            log.info("REST created "+item);
            return Response.created(URI.create("policies/" + clazz.getName())).build();
        }

        throw WebResourceUtils.preconditionFailed("Unsupported type superclass "+clazz.getSuperclass()+"; expects Entity or Policy");
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
        EntityLocal e = getEntity(application, entity);
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

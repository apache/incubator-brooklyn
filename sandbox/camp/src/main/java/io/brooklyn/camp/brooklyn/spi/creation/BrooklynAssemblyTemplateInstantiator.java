package io.brooklyn.camp.brooklyn.spi.creation;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.brooklyn.BrooklynCampPlatform;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.EntityTypes;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;

import com.google.common.collect.Maps;

public class BrooklynAssemblyTemplateInstantiator implements AssemblyTemplateInstantiator {

    private static final Logger log = LoggerFactory.getLogger(BrooklynAssemblyTemplateInstantiator.class);
    
    @Override
    public Assembly instantiate(AssemblyTemplate template, CampPlatform platform) {
        Application app = create(template, platform);
        Task<?> task = start(app, platform);
        log.info("CAMP created "+app+"; starting in "+task);
        return ((BrooklynCampPlatform)platform).assemblies().get(app.getApplicationId());
    }

    // note: based on BrooklynRestResourceUtils, but modified to not allow child entities (yet)
    // (will want to revise that when building up from a non-brooklyn template)
    @SuppressWarnings("unchecked")
    public Application create(AssemblyTemplate template, CampPlatform platform) {
        log.debug("CAMP creating application instance for {} ({})", template.getId(), template);
        
        ManagementContext mgmt = getBrooklynManagementContext(platform);
        BrooklynCatalog catalog = mgmt.getCatalog();
        CatalogItem<?> item = catalog.getCatalogItem(template.getId());

        if (item==null) {
            // TODO support AssemblyTemplates created via PDP, _specifying_ then entities to put in 
            throw new UnsupportedOperationException("Assembly template "+template+" is not a brooklyn blueprint");
        }
        
        // TODO name (and description) -- not prescribed by camp spec (cf discussion with gil)
        String name = null;
        
        // TODO insertion of config / params
        final Map<String,String> configO = MutableMap.of();
        
        String type = item.getJavaType();
        final Application instance;

        // Load the class; first try to use the appropriate catalog item; but then allow anything that is on the classpath
        final Class<? extends Entity> clazz;
        if (Strings.isEmpty(type)) {
            clazz = BasicApplication.class;
        } else {
            Class<? extends Entity> tempclazz;
            try {
                tempclazz = catalog.loadClassByType(type, Entity.class);
            } catch (NoSuchElementException e) {
                try {
                    tempclazz = (Class<? extends Entity>) catalog.getRootClassLoader().loadClass(type);
                    log.info("Catalog does not contain item for type {}; loaded class directly instead", type);
                } catch (ClassNotFoundException e2) {
                    log.warn("No catalog item for type {}, and could not load class directly; rethrowing", type);
                    throw e;
                }
            }
            clazz = tempclazz;
        }
        
        try {
            if (ApplicationBuilder.class.isAssignableFrom(clazz)) {
                Constructor<?> constructor = clazz.getConstructor();
                ApplicationBuilder appBuilder = (ApplicationBuilder) constructor.newInstance();
                
                if (!Strings.isEmpty(name)) appBuilder.appDisplayName(name);
                
                log.info("REST placing '{}' under management", appBuilder);
                appBuilder.configure( convertFlagsToKeys(appBuilder.getType(), configO) );
                instance = appBuilder.manage(mgmt);
                
            } else if (Application.class.isAssignableFrom(clazz)) {
                brooklyn.entity.proxying.EntitySpec<?> coreSpec = toCoreEntitySpec(clazz, name, configO);
                instance = (Application) mgmt.getEntityManager().createEntity(coreSpec);
                
//                for (EntitySpec entitySpec : entities) {
//                    log.info("REST creating instance for entity {}", entitySpec.getType());
//                    instance.addChild(mgmt.getEntityManager().createEntity(toCoreEntitySpec(entitySpec)));
//                }
                
                log.info("REST placing '{}' under management", instance);
                Entities.startManagement(instance, mgmt);
                
            } else {
                throw new IllegalArgumentException("Class "+clazz+" must extend one of ApplicationBuilder or Application");
            }
            
            return instance;
            
        } catch (Exception e) {
            log.error("CAMP failed to create application: "+e, e);
            throw Exceptions.propagate(e);
        }
    }

    private ManagementContext getBrooklynManagementContext(CampPlatform platform) {
        // TODO if brooklyn is _part_ of the catalog we need a way to get a handle on it from platform
        ManagementContext mgmt = ((BrooklynCampPlatform)platform).getBrooklynManagementContext();
        return mgmt;
    }
    
    public Task<?> start(Application app, CampPlatform platform) {
        // Start all the managed entities by asking the app instance to start in background
//        Function<String, Location> buildLocationFromId = new Function<String, Location>() {
//            @Override
//            public Location apply(String id) {
//                id = fixLocation(id);
//                return getLocationRegistry().resolve(id);
//            }
//        };
//        ArrayList<Location> locations = Lists.newArrayList(transform(spec.getLocations(), buildLocationFromId));

        // TODO support other places besides localhost
        List<Location> locations = 
                getBrooklynManagementContext(platform).getLocationRegistry().resolve(Arrays.asList("localhost"));
        
        return Entities.invokeEffectorWithMap((EntityLocal)app, app, Startable.START,
                MutableMap.of("locations", locations));
    }

    // TODO exact copy of BrooklynRestResoureUtils
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

    // TODO exact copy of BRRU
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private <T extends Entity> brooklyn.entity.proxying.EntitySpec<?> toCoreEntitySpec(Class<T> clazz, String name, Map<String,String> configO) {
        Map<String, String> config = (configO == null) ? Maps.<String,String>newLinkedHashMap() : Maps.newLinkedHashMap(configO);
        
        BasicEntitySpec result;
        if (clazz.isInterface()) {
            result = EntitySpecs.spec(clazz);
        } else {
            // If this is a concrete class, particularly for an Application class, we want the proxy
            // to expose all interfaces it implements.
            Class interfaceclazz = (Application.class.isAssignableFrom(clazz)) ? Application.class : Entity.class;
            Class<?>[] additionalInterfaceClazzes = clazz.getInterfaces();
            result = EntitySpecs.spec(interfaceclazz).impl(clazz).additionalInterfaces(additionalInterfaceClazzes);
        }
        
        if (!Strings.isEmpty(name)) result.displayName(name);
        result.configure( convertFlagsToKeys(result.getImplementation(), config) );
        return result;
    }

}

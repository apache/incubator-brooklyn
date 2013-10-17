package io.brooklyn.camp.brooklyn.spi.creation;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.brooklyn.BrooklynCampConstants;
import io.brooklyn.camp.brooklyn.BrooklynCampPlatform;
import io.brooklyn.camp.brooklyn.spi.platform.HasBrooklynManagementContext;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.collection.ResolvableLink;
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
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.BasicApplicationImpl;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.EntityTypes;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntityInitializer;
import brooklyn.entity.proxying.EntitySpec;
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
    public Application create(AssemblyTemplate template, CampPlatform platform) {
        log.debug("CAMP creating application instance for {} ({})", template.getId(), template);
        
        ManagementContext mgmt = getBrooklynManagementContext(platform);
        BrooklynCatalog catalog = mgmt.getCatalog();
        CatalogItem<?> item = catalog.getCatalogItem(template.getId());

        if (item==null) {
            return createApplicationFromNonCatalogCampTemplate(template, platform);
        } else {
            return createApplicationFromCatalog(platform, item, template);
        }
    }

    protected Application createApplicationFromCatalog(CampPlatform platform, CatalogItem<?> item, AssemblyTemplate template) {
        ManagementContext mgmt = getBrooklynManagementContext(platform);
        BrooklynCatalog catalog = mgmt.getCatalog();

        if (!template.getApplicationComponentTemplates().isEmpty() ||
                !template.getPlatformComponentTemplates().isEmpty())
            log.warn("CAMP AssemblyTemplate was not empty when creating from catalog spec; ignoring templates declared within it " +
                    "("+template+")");

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
            clazz = loadEntityType(catalog, type);
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

    @SuppressWarnings("unchecked")
    protected Class<? extends Entity> loadEntityType(BrooklynCatalog catalog, String type) {
        final Class<? extends Entity> clazz;
        Class<? extends Entity> tempclazz;
        try {
            tempclazz = catalog.loadClassByType(type, Entity.class);
        } catch (NoSuchElementException e) {
            try {
                tempclazz = (Class<? extends Entity>) catalog.getRootClassLoader().loadClass(type);
                log.debug("Catalog does not contain item for type {}; loaded class directly instead", type);
            } catch (ClassNotFoundException e2) {
                log.warn("No catalog item for type {}, and could not load class directly; rethrowing", type);
                throw e;
            }
        }
        clazz = tempclazz;
        return clazz;
    }

    private ManagementContext getBrooklynManagementContext(CampPlatform platform) {
        ManagementContext mgmt = ((HasBrooklynManagementContext)platform).getBrooklynManagementContext();
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
        
        return Entities.invokeEffector((EntityLocal)app, app, Startable.START,
                MutableMap.of("locations", locations));
    }

    // TODO exact copy of BrooklynRestResoureUtils
    private static Map<?,?> convertFlagsToKeys(Class<? extends Entity> javaType, Map<?, ?> config) {
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
        
        EntitySpec result;
        if (clazz.isInterface()) {
            result = EntitySpec.create(clazz);
        } else {
            // If this is a concrete class, particularly for an Application class, we want the proxy
            // to expose all interfaces it implements.
            Class interfaceclazz = (Application.class.isAssignableFrom(clazz)) ? Application.class : Entity.class;
            Class<?>[] additionalInterfaceClazzes = clazz.getInterfaces();
            result = EntitySpec.create(interfaceclazz).impl(clazz).additionalInterfaces(additionalInterfaceClazzes);
        }
        
        if (!Strings.isEmpty(name)) result.displayName(name);
        result.configure( convertFlagsToKeys(result.getImplementation(), config) );
        return result;
    }

    protected Application createApplicationFromNonCatalogCampTemplate(AssemblyTemplate template, CampPlatform platform) {
        // AssemblyTemplates created via PDP, _specifying_ then entities to put in
        ManagementContext mgmt = getBrooklynManagementContext(platform);
        BrooklynCatalog catalog = mgmt.getCatalog();

        EntitySpec<StartableApplication> appSpec = EntitySpec.create(StartableApplication.class, BasicApplicationImpl.class);

        for (ResolvableLink<PlatformComponentTemplate> ctl: template.getPlatformComponentTemplates().links()) {
            final PlatformComponentTemplate ct = ctl.resolve();
            final String bTypeName = Strings.removeFromStart(ct.getType(), "brooklyn:");
            final Class<? extends Entity> bType = loadEntityType(catalog, bTypeName);
            
            appSpec.addInitializer(new EntityInitializer() {
                @SuppressWarnings({ "rawtypes", "unchecked" })
                @Override
                public void apply(EntityLocal entity) {
                    EntitySpec<? extends Entity> child = EntitySpec.create(bType);
                    Map<String, Object> attrs = MutableMap.copyOf(ct.getCustomAttributes());

                    child.configure(BrooklynCampConstants.TEMPLATE_ID, ct.getId());
                    
                    String planId = (String) attrs.remove("planId");
                    if (planId!=null)
                        child.configure(BrooklynCampConstants.PLAN_ID, planId);
                     
                    Map<?,?> config = (Map<?, ?>) attrs.remove("brooklyn.config");
                    if (config!=null) {
                        for (Map.Entry<?,?> entry: config.entrySet()) {
                            // set as config key (rather than flags) so that it is inherited
                            Object key = entry.getKey();
                            if (key instanceof ConfigKey)
                                child.configure( (ConfigKey)key, entry.getValue() );
                            else if (key instanceof HasConfigKey)
                                child.configure( (HasConfigKey)key, entry.getValue() );
                            else
                                child.configure(ConfigKeys.newConfigKey(Object.class, key.toString()), entry.getValue());
                        }
                    }
                   
                    entity.addChild(child);
                }
            });
        }
        
        log.info("REST placing '{}' under management", appSpec);
        StartableApplication app = mgmt.getEntityManager().createEntity(appSpec);
        Entities.startManagement(app, mgmt);
        return app;
    }

}

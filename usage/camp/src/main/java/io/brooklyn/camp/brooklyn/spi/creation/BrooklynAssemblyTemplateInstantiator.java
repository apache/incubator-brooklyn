package io.brooklyn.camp.brooklyn.spi.creation;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.collection.ResolvableLink;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import brooklyn.camp.brooklyn.api.HasBrooklynManagementContext;
import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.BasicApplicationImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.EntityTypes;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.text.Strings;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

public class BrooklynAssemblyTemplateInstantiator implements AssemblyTemplateSpecInstantiator {

    private static final Logger log = LoggerFactory.getLogger(BrooklynAssemblyTemplateInstantiator.class);
    
    public static final String NEVER_UNWRAP_APPS_PROPERTY = "wrappedApp";
    
    @Override
    public Assembly instantiate(AssemblyTemplate template, CampPlatform platform) {
        Application app = create(template, platform);
        Task<?> task = start(app, platform);
        log.info("CAMP created "+app+"; starting in "+task);
        return platform.assemblies().get(app.getApplicationId());
    }

    public EntitySpec<?> createSpec(AssemblyTemplate template, CampPlatform platform) {
        return createAppOrSpec(template, platform, true).getSpec();
    }

    // note: based on BrooklynRestResourceUtils, but modified to not allow child entities (yet)
    // (will want to revise that when building up from a non-brooklyn template)
    public Application create(AssemblyTemplate template, CampPlatform platform) {
        ManagementContext mgmt = getBrooklynManagementContext(platform);
        
        AppOrSpec appOrSpec = createAppOrSpec(template, platform, false);
        
        if (appOrSpec.hasApp())
            return appOrSpec.getApp();
        if (!appOrSpec.hasSpec())
            throw new IllegalStateException("No spec could be produced from "+template);
        
        EntitySpec<? extends Application> spec = appOrSpec.getSpec();
        
        Application instance = (Application) mgmt.getEntityManager().createEntity(spec);
        log.info("CAMP placing '{}' under management", instance);
        Entities.startManagement(instance, mgmt);

        return instance;
    }
    
    protected AppOrSpec createAppOrSpec(AssemblyTemplate template, CampPlatform platform, boolean requireSpec) {
        log.debug("CAMP creating application instance for {} ({})", template.getId(), template);
        
        ManagementContext mgmt = getBrooklynManagementContext(platform);
        BrooklynCatalog catalog = mgmt.getCatalog();
        
        CatalogItem<?,?> item = catalog.getCatalogItem(template.getId());
        if (item!=null) {
            // TODO legacy path - currently item is always null because template.id is a random String,
            // and pretty sure that is the desired behaviour; we now (Jul 2014) automatically promote
            // so fine for users always to put the catalog registeredType in the services block;
            // if we did want to support users supplying an `id' that would be available not via the above
            // but via template.getCustomAttributes().get("id");
            
            return createApplicationFromCatalog(platform, item, template, requireSpec);
        } else {
            return new AppOrSpec(createApplicationFromNonCatalogCampTemplate(template, platform));
        }
    }
    
    private static class AppOrSpec {
        private final Application app;
        private final EntitySpec<? extends Application> spec;
        
        public AppOrSpec(Application app) {
            this.app = app;
            this.spec = null;
        }

        public AppOrSpec(EntitySpec<? extends Application> spec) {
            this.app = null;
            this.spec = spec;
        }

        public boolean hasApp() {
            return app!=null;
        }
        public boolean hasSpec() {
            return spec!=null;
        }
        public Application getApp() {
            return app;
        }
        public EntitySpec<? extends Application> getSpec() {
            return spec;
        }
    }
    
    protected AppOrSpec createApplicationFromCatalog(CampPlatform platform, CatalogItem<?,?> item, AssemblyTemplate template, boolean requireSpec) {
        ManagementContext mgmt = getBrooklynManagementContext(platform);

        if (!template.getApplicationComponentTemplates().isEmpty() ||
                !template.getPlatformComponentTemplates().isEmpty())
            log.warn("CAMP AssemblyTemplate was not empty when creating from catalog spec; ignoring templates declared within it " +
                    "("+template+")");

        // name (and description) -- not prescribed by camp spec (cf discussion with gil)
        String name = template.getName();
                
        String type = item.getJavaType();

        // Load the class; first try to use the appropriate catalog item; but then allow anything that is on the classpath
        final Class<? extends Entity> clazz;
        if (Strings.isEmpty(type)) {
            clazz = BasicApplication.class;
        } else {
            clazz = BrooklynEntityClassResolver.resolveEntity(type, mgmt);
        }
        
        try {
            if (ApplicationBuilder.class.isAssignableFrom(clazz)) {
                if (requireSpec) {
                    // TODO we could enable this, by returning the spec from the ApplicationBuilder;
                    // note that we would have to set it up so that ApplicationBuilder.doBuild is called from an
                    // entity initializer set on the spec, and remove the doBuild call from ApplicationBuilder.manage.
                    // (this could also allow ApplicationBuilder instances to be used from catalog)
                    throw new IllegalStateException("ApplicationBuilder items cannot be used when specs have to be created");
                }
                Constructor<?> constructor = clazz.getConstructor();
                ApplicationBuilder appBuilder = (ApplicationBuilder) constructor.newInstance();
                // for builder, we (1) can't get a spec (so discourage use of Builder?),
                // and (2) we have to manually extract key bits of the template
                
                if (!Strings.isEmpty(name)) appBuilder.appDisplayName(name);
        
                // TODO use resolver's configureEntitySpec instead
                final Map<?,?> configO = (Map<?,?>) template.getCustomAttributes().get("brooklyn.config");

                log.info("CAMP placing '{}' under management", appBuilder);
                appBuilder.configure( convertFlagsToKeys(appBuilder.getType(), configO) );
                Application instance = appBuilder.manage(mgmt);
                
                applyLocations(mgmt, template, instance);

                return new AppOrSpec(instance);
                
            } else if (Application.class.isAssignableFrom(clazz)) {
                // TODO use resolver's configureEntitySpec instead
                final Map<?,?> configO = (Map<?,?>) template.getCustomAttributes().get("brooklyn.config");
                
                @SuppressWarnings("unchecked")
                brooklyn.entity.proxying.EntitySpec<? extends Application> coreSpec = toCoreEntitySpec((Class<? extends Application>)clazz, name, configO);
                applyLocations(mgmt, template, coreSpec);
                
                return new AppOrSpec(coreSpec);
                
            } else {
                throw new IllegalArgumentException("Class "+clazz+" must extend one of ApplicationBuilder or Application");
            }
            
        } catch (Exception e) {
            log.error("CAMP failed to create application: "+e, e);
            throw Exceptions.propagate(e);
        }
    }

    private void applyLocations(ManagementContext mgmt, AssemblyTemplate template, final Application instance) {
        List<Location> locations = new BrooklynYamlLocationResolver(mgmt).resolveLocations(template.getCustomAttributes(), false);
        if (locations!=null)
            ((EntityInternal)instance).addLocations(locations);
    }

    private void applyLocations(ManagementContext mgmt, AssemblyTemplate template, final EntitySpec<?> spec) {
        List<Location> locations = new BrooklynYamlLocationResolver(mgmt).resolveLocations(template.getCustomAttributes(), false);
        if (locations!=null)
            spec.locations(locations);
    }

    private ManagementContext getBrooklynManagementContext(CampPlatform platform) {
        return ((HasBrooklynManagementContext)platform).getBrooklynManagementContext();
    }
    
    public Task<?> start(Application app, CampPlatform platform) {
        return Entities.invokeEffector((EntityLocal)app, app, Startable.START,
            // locations already set in the entities themselves;
            // TODO make it so that this arg does not have to be supplied to START !
            MutableMap.of("locations", MutableList.of()));
    }

    // TODO this is an exact copy of BrooklynRestResoureUtils; make available to both somehow? (or even better, avoid somehow)
    private static Map<?,?> convertFlagsToKeys(Class<? extends Entity> javaType, Map<?, ?> config) {
        if (config==null || config.isEmpty() || javaType==null) return config;

        Map<String, ConfigKey<?>> configKeys = EntityTypes.getDefinedConfigKeys(javaType);
        Map<Object,Object> result = new LinkedHashMap<Object,Object>();
        for (Map.Entry<?,?> entry: config.entrySet()) {
            log.debug("Setting key {} to {} for CAMP creation of {}", new Object[] { entry.getKey(), entry.getValue(), javaType});
            Object key = configKeys.get(entry.getKey());
            if (key==null) {
                log.warn("Unrecognised config key {} passed to {}; will be treated as flag (and likely ignored)", entry.getKey(), javaType);
                key = entry.getKey();
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    // TODO exact copy of BRRU, as above
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private <T extends Entity> EntitySpec<? extends T> toCoreEntitySpec(Class<? extends T> clazz, String name, Map<?,?> configO) {
        Map<?, ?> config = (configO == null) ? Maps.newLinkedHashMap() : Maps.newLinkedHashMap(configO);
        
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

    @SuppressWarnings("unchecked")
    protected EntitySpec<? extends Application> createApplicationFromNonCatalogCampTemplate(AssemblyTemplate template, CampPlatform platform) {
        // AssemblyTemplates created via PDP, _specifying_ then entities to put in
        final ManagementContext mgmt = getBrooklynManagementContext(platform);

        BrooklynComponentTemplateResolver resolver = BrooklynComponentTemplateResolver.Factory.newInstance(mgmt, template);
        EntitySpec<? extends Application> app = resolver.resolveSpec(StartableApplication.class, BasicApplicationImpl.class);
        
        // first build the children into an empty shell app
        buildTemplateServicesAsSpecs(template, app, mgmt);
        
        if (shouldUnwrap(template, app)) {
            EntitySpec<? extends Application> oldApp = app;
            app = (EntitySpec<? extends Application>) Iterables.getOnlyElement( app.getChildren() );
            // if promoted, apply the transformations done to the app
            // (normally this will be done by the resolveSpec call above)
            if (app.getDisplayName()==null) app.displayName(oldApp.getDisplayName());
            app.locations(oldApp.getLocations());
        }
        
        return app;
    }

    protected boolean shouldUnwrap(AssemblyTemplate template, EntitySpec<? extends Application> app) {
        Object leaveWrapped = template.getCustomAttributes().get(NEVER_UNWRAP_APPS_PROPERTY);
        if (leaveWrapped!=null) {
            if (TypeCoercions.coerce(leaveWrapped, Boolean.class))
                return false;
        }
        
        if (app.getChildren().size()!=1) 
            return false;
        
        EntitySpec<?> childSpec = Iterables.getOnlyElement(app.getChildren());
        if (childSpec.getType()==null || !Application.class.isAssignableFrom(childSpec.getType()))
            return false;

        Set<String> rootAttrs = template.getCustomAttributes().keySet();
        for (String rootAttr: rootAttrs) {
            if (rootAttr.equals("brooklyn.catalog")) {
                // this attr does not block promotion
                continue;
            }
            if (rootAttr.startsWith("brooklyn.")) {
                // any others in 'brooklyn' namespace will block promotion
                return false;
            }
            // location is allowed in both, and is copied on promotion
            // (name also copied)
            // others are root currently are ignored on promotion; they are usually metadata
            // TODO might be nice to know what we are excluding
        }
        
        return true;
    }

    private void buildTemplateServicesAsSpecs(AssemblyTemplate template, EntitySpec<? extends Application> root, ManagementContext mgmt) {
        for (ResolvableLink<PlatformComponentTemplate> ctl: template.getPlatformComponentTemplates().links()) {
            PlatformComponentTemplate appChildComponentTemplate = ctl.resolve();
            BrooklynComponentTemplateResolver entityResolver = BrooklynComponentTemplateResolver.Factory.newInstance(mgmt, appChildComponentTemplate);
            
            EntitySpec<? extends Entity> spec = entityResolver.resolveSpec();
            root.child(spec);
            
            buildChildrenEntitySpecs(mgmt, spec, entityResolver.getChildren(appChildComponentTemplate.getCustomAttributes()));
        }
    }

    protected void buildChildrenEntitySpecs(ManagementContext mgmt, EntitySpec<?> parent, List<Map<String, Object>> childConfig) {
        if (childConfig != null) {
            for (Map<String, Object> childAttrs : childConfig) {
                BrooklynComponentTemplateResolver entityResolver = BrooklynComponentTemplateResolver.Factory.newInstance(mgmt, childAttrs);
                EntitySpec<? extends Entity> spec = entityResolver.resolveSpec();
                parent.child(spec);
                
                buildChildrenEntitySpecs(mgmt, spec, entityResolver.getChildren(childAttrs));
            }
        }
    }

}

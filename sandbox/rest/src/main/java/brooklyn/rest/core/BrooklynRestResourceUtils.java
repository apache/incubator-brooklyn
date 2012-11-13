package brooklyn.rest.core;

import static brooklyn.rest.core.WebResourceUtils.notFound;
import static com.google.common.collect.Iterables.transform;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.http.protocol.HTTP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.actors.threadpool.Arrays;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.jclouds.JcloudsLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.api.LocationSpec;
import brooklyn.util.MutableMap;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class BrooklynRestResourceUtils {

    private static final Logger log = LoggerFactory.getLogger(BrooklynRestResourceUtils.class);

    // TODO move these to mgmt context -- so REST API is stateless
    private static LocationStore locationStore = new LocationStore(); 
    private static BrooklynCatalog catalog = new BrooklynCatalog(); 
    public static void changeLocationStore(LocationStore locationStore) {
        BrooklynRestResourceUtils.locationStore = locationStore;
    }
    
    private final ManagementContext mgmt;
    
    public BrooklynRestResourceUtils(ManagementContext mgmt) {
        Preconditions.checkNotNull(mgmt, "mgmt");
        this.mgmt = mgmt;
    }

    public BrooklynCatalog getCatalog() {
        return catalog;
    }
    
    public LocationStore getLocationStore() {
        return locationStore;
    }
    
    /** finds the entity indicated by the given ID or name
     * <p>
     * prefers ID based lookup in which case appId is optional, and if supplied will be enforced.
     * optionally the name can be supplied, for cases when paths should work across versions,
     * in which case names will be searched recursively (and the application is required). 
     * 
     * @throws HTTP 404 or 412 (unless input is null in which case output is null) */
    public EntityLocal getEntity(String application, String entity) {
        if (entity==null) return null;
        Application app = application!=null ? getApplication(application) : null;
        EntityLocal e = (EntityLocal) mgmt.getEntity(entity);
        if (e!=null) {
            if (app==null || app.equals(e.getApplication())) return e;
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

    /** looks for the given application instance, first by ID then by name
     * 
     * @throws HTTP 404 if not found
     */
    public Application getApplication(String application) {
        Entity e = mgmt.getEntity(application);
        if (e!=null && e instanceof Application) return (Application)e;
        for (Application app: mgmt.getApplications()) {
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
        for (Entity child: root.getOwnedChildren()) {
            Entity result = searchForEntityNamed(child, entity);
            if (result!=null) return (EntityLocal) result;
        }
        return null;
    }
    
    // TODO delete
    
//    /**
//     * Find an entity by querying the application tree of entities
//     */
//    public static EntityLocal getEntityOr404(ApplicationSummary application, String entityIdOrName) {
//        EntityLocal result = recursiveGetEntityOrNull(application.getInstance(), entityIdOrName);
//        if (result == null) {
//            throw notFound("Application '%s' has no entity with id or name '%s'",
//                    application.getSpec().getName(), entityIdOrName);
//        }
//        return result;
//    }
//
//    private static EntityLocal recursiveGetEntityOrNull(EntityLocal entity, String entityIdOrName) {
//        // TODO: switch to BFS traversal & add cycle detection
//        if (entity.getId().equals(entityIdOrName) || entity.getDisplayName().equals(entityIdOrName)) {
//            return entity;
//        }
//
//        for (Entity child : entity.getOwnedChildren()) {
//            if (child instanceof EntityLocal) {
//                EntityLocal result = recursiveGetEntityOrNull((EntityLocal) child, entityIdOrName);
//                if (result != null) {
//                    return result;
//                }
//            }
//        }
//
//        return null;
//    }
//
//    @SuppressWarnings("unchecked")
//    public static AttributeSensor<Object> getSensorOr404(EntityLocal entity, String sensorName) {
//        if (!entity.getEntityType().hasSensor(sensorName)) {
//            throw notFound("Entity '%s' has no sensor with name '%s'", entity.getId(), sensorName);
//        }
//
//        Sensor<?> sensor = entity.getEntityType().getSensor(sensorName);
//        if (!(sensor instanceof AttributeSensor)) {
//            throw notFound("Sensor '%s' is not an AttributeSensor", sensorName);
//        }
//
//        return (AttributeSensor<Object>) sensor;
//    }
//
//    public static ApplicationSummary getApplicationOr404(ApplicationManager manager, String applicationIdOrName) {
//        ApplicationSummary app = manager.getApp(applicationIdOrName);
//        if (app==null)
//            throw notFound("Application '%s' not found.", applicationIdOrName);
//        return app;
//    }
//
//    public static ApplicationSummary getApplicationOr404(Map<String, ApplicationSummary> registry, String application) {
//        if (!registry.containsKey(application))
//            throw notFound("Application '%s' not found.", application);
//
//        return registry.get(application);
//    }

    public Application create(ApplicationSpec spec) {
        log.debug("REST creating application instance for {}", spec);

        final AbstractApplication instance;
        
        try {
            if (spec.getType()!=null) {
                instance = (AbstractApplication) newEntityInstance(spec.getType(), null, spec.getConfig());
            } else {
                instance = new AbstractApplication() {};
            }
            if (spec.getName()!=null && !spec.getName().isEmpty()) instance.setDisplayName(spec.getName());

            if (spec.getEntities()!=null) for (EntitySpec entitySpec : spec.getEntities()) {
                log.info("REST creating instance for entity {}", entitySpec.getType());
                AbstractEntity entity = newEntityInstance(entitySpec.getType(), instance, entitySpec.getConfig());
                if (entitySpec.getName()!=null && !spec.getName().isEmpty()) entity.setDisplayName(entitySpec.getName());
            }
        } catch (Exception e) {
            log.error("REST failed to create application: "+e, e);
            throw Throwables.propagate(e);
        }

        log.info("REST placing '{}' under management as {}", spec.getName(), instance);
        mgmt.manage(instance);
        
        return instance;
    }
    
    public Task<?> start(Application app, ApplicationSpec spec) {
        // Start all the managed entities by asking the app instance to start in background
        Function<String, Location> buildLocationFromRef = new Function<String, Location>() {
            @Override
            public Location apply(String ref) {
                LocationSpec locationSpec = locationStore.getByRef(ref);
                if (locationSpec.getProvider().equals("localhost")) {
                    return new LocalhostMachineProvisioningLocation(MutableMap.copyOf(locationSpec.getConfig()));
                }

                Map<String, String> config = Maps.newHashMap();
                config.put("provider", locationSpec.getProvider());
                config.putAll(locationSpec.getConfig());

                return new JcloudsLocation(config);
            }
        };

        ArrayList<Location> locations = Lists.newArrayList(transform(spec.getLocations(), buildLocationFromRef));
        return Entities.invokeEffectorWithMap((EntityLocal)app, app, Startable.START,
                MutableMap.of("locations", locations));
    }

    private AbstractEntity newEntityInstance(String type, Entity owner, Map<String, String> configO) throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<? extends AbstractEntity> clazz = catalog.getEntityClass(type);
        Map<String, String> config = Maps.newHashMap(configO);
        Constructor<?>[] constructors = clazz.getConstructors();
        AbstractEntity result = null;
        if (owner==null) {
            result = tryInstantiateEntity(constructors, new Class[] { Map.class }, new Object[] { config });
            if (result!=null) return result;
        }
        result = tryInstantiateEntity(constructors, new Class[] { Map.class, Entity.class }, new Object[] { config, owner });
        if (result!=null) return result;

        result = tryInstantiateEntity(constructors, new Class[] { Map.class }, new Object[] { config });
        if (result!=null) {
            if (owner!=null) ((AbstractEntity)result).setOwner(owner);
            return result;
        }

        result = tryInstantiateEntity(constructors, new Class[] { Entity.class }, new Object[] { owner });
        if (result!=null) {
            ((AbstractEntity)result).configure(config);
            return result;
        }

        result = tryInstantiateEntity(constructors, new Class[] {}, new Object[] {});
        if (result!=null) {
            if (owner!=null) ((AbstractEntity)result).setOwner(owner);
            ((AbstractEntity)result).configure(config);
            return result;
        }

        throw new IllegalStateException("No suitable constructor for instantiating entity "+type);
    }

    private AbstractEntity tryInstantiateEntity(Constructor<?>[] constructors, Class<?>[] classes, Object[] objects) throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
        for (Constructor<?> c: constructors) {
            if (Arrays.equals(c.getParameterTypes(), classes)) {
                return (AbstractEntity) c.newInstance(objects);
            }
        }
        return null;
    }

    public Task<?> destroy(final Application application) {
        return mgmt.getExecutionManager().submit(new Runnable() {
            @Override
            public void run() {
                ((AbstractApplication)application).destroy();
                mgmt.unmanage(application);
            }
        });
    }

}

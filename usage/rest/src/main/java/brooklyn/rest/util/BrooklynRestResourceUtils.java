package brooklyn.rest.util;

import static brooklyn.rest.util.WebResourceUtils.notFound;
import static com.google.common.collect.Iterables.transform;
import groovy.lang.GroovyClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.EntitySpec;
import brooklyn.util.MutableMap;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

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
     * @throws 404 if not found
     */
    public Application getApplication(String application) {
        Entity e = mgmt.getEntityManager().getEntity(application);
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
        for (Entity child: root.getChildren()) {
            Entity result = searchForEntityNamed(child, entity);
            if (result!=null) return (EntityLocal) result;
        }
        return null;
    }

    @SuppressWarnings("serial")
    public Application create(ApplicationSpec spec) {
        log.debug("REST creating application instance for {}", spec);

        final Application instance;
        
        try {
            if (spec.getType()!=null) {
                instance = (Application) newEntityInstance(spec.getType(), null, spec.getConfig());
            } else {
                instance = new AbstractApplication() {};
            }
            if (spec.getName()!=null && !spec.getName().isEmpty()) ((EntityLocal)instance).setDisplayName(spec.getName());

            if (spec.getEntities()!=null) for (EntitySpec entitySpec : spec.getEntities()) {
                log.info("REST creating instance for entity {}", entitySpec.getType());
                EntityLocal entity = newEntityInstance(entitySpec.getType(), instance, entitySpec.getConfig());
                if (entitySpec.getName()!=null && !spec.getName().isEmpty()) entity.setDisplayName(entitySpec.getName());
            }
        } catch (Exception e) {
            log.error("REST failed to create application: "+e, e);
            throw Throwables.propagate(e);
        }

        log.info("REST placing '{}' under management as {}", spec.getName(), instance);
        Entities.startManagement(instance, mgmt);
        
        return instance;
    }
    
    public Task<?> start(Application app, ApplicationSpec spec) {
        // Start all the managed entities by asking the app instance to start in background
        Function<String, Location> buildLocationFromId = new Function<String, Location>() {
            @Override
            public Location apply(String id) {
                id = fixLocation(id);
                return getLocationRegistry().resolve(id);
            }
        };

        ArrayList<Location> locations = Lists.newArrayList(transform(spec.getLocations(), buildLocationFromId));
        return Entities.invokeEffectorWithMap((EntityLocal)app, app, Startable.START,
                MutableMap.of("locations", locations));
    }

    private EntityLocal newEntityInstance(String type, Entity parent, Map<String, String> configO) throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<? extends Entity> clazz = getCatalog().loadClassByType(type, Entity.class);
        Map<String, String> config = Maps.newHashMap(configO);
        Constructor<?>[] constructors = clazz.getConstructors();
        EntityLocal result = null;
        if (parent==null) {
            result = tryInstantiateEntity(constructors, new Class[] { Map.class }, new Object[] { config });
            if (result!=null) return result;
        }
        result = tryInstantiateEntity(constructors, new Class[] { Map.class, Entity.class }, new Object[] { config, parent });
        if (result!=null) return result;

        result = tryInstantiateEntity(constructors, new Class[] { Map.class }, new Object[] { config });
        if (result!=null) {
            if (parent!=null) result.setParent(parent);
            return result;
        }

        result = tryInstantiateEntity(constructors, new Class[] { Entity.class }, new Object[] { parent });
        if (result!=null) {
            result.configure(config);
            return result;
        }

        result = tryInstantiateEntity(constructors, new Class[] {}, new Object[] {});
        if (result!=null) {
            if (parent!=null) result.setParent(parent);
            result.configure(config);
            return result;
        }

        throw new IllegalStateException("No suitable constructor for instantiating entity "+type);
    }

    private EntityLocal tryInstantiateEntity(Constructor<?>[] constructors, Class<?>[] classes, Object[] objects) throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
        for (Constructor<?> c: constructors) {
            if (Arrays.equals(c.getParameterTypes(), classes)) {
                return (EntityLocal) c.newInstance(objects);
            }
        }
        return null;
    }

    public Task<?> destroy(final Application application) {
        return mgmt.getExecutionManager().submit(new Runnable() {
            @Override
            public void run() {
                ((EntityLocal)application).destroy();
                mgmt.getEntityManager().unmanage(application);
            }
        });
    }

    @SuppressWarnings({ "rawtypes" })
    public Response createCatalogEntryFromGroovyCode(String groovyCode) {
        ClassLoader parent = getCatalog().getRootClassLoader();
        GroovyClassLoader loader = new GroovyClassLoader(parent);

        Class clazz = loader.parseClass(groovyCode);

        if (AbstractEntity.class.isAssignableFrom(clazz)) {
            CatalogItem<?> item = getCatalog().addItem(clazz);
            log.info("REST created "+item);
            return Response.created(URI.create("entities/" + clazz.getName())).build();

        } else if (AbstractPolicy.class.isAssignableFrom(clazz)) {
            CatalogItem<?> item = getCatalog().addItem(clazz);
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

}

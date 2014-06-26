package io.brooklyn.camp.brooklyn.spi.creation;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.OsgiManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.guava.Maybe;

/**
 * Resolves a class name to a <code>Class&lt;? extends Entity&gt;</code> with a given 
 * {@link brooklyn.management.ManagementContext management context}.
 */
public class BrooklynEntityClassResolver {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynEntityClassResolver.class);

    /**
     * Loads the class represented by entityTypeName with the given management context.
     * Tries the context's catalogue first, then from its root classloader.
     * @throws java.lang.IllegalStateException if no class extending {@link Entity} is found
     */
    public static <T extends Entity> Class<T> resolveEntity(String entityTypeName, ManagementContext mgmt) {
        checkNotNull(mgmt, "management context");
        Maybe<Class<T>> entityClazz = tryLoadEntityFromCatalogue(entityTypeName, mgmt);
        if (!entityClazz.isPresent()) entityClazz = tryLoadFromClasspath(entityTypeName, mgmt);
        if (!entityClazz.isPresent()) {
            LOG.warn("No catalog item for {} and could not load class directly; throwing", entityTypeName);
            throw new IllegalStateException("Unable to load class "+ entityTypeName +" (extending Entity) from catalogue or classpath: not found");
        }
        if (!Entity.class.isAssignableFrom(entityClazz.get())) {
            LOG.warn("Found class {} on classpath but it is not assignable to {}", entityTypeName, Entity.class);
            throw new IllegalStateException("Unable to load class "+ entityTypeName +" (extending Entity) from catalogue or classpath: wrong type "+entityClazz.get());
        }
        return entityClazz.get();
    }

    /** Tries to load the entity with the given class name from the given bundle. */
    public static <T extends Entity> Maybe<Class<T>> tryLoadEntityFromBundle(String entityTypeName, String bundleUrl, ManagementContext mgmt) {
        Maybe<OsgiManager> osgiManager = ((ManagementContextInternal) mgmt).getOsgiManager();
        if (!osgiManager.isPresentAndNonNull()) {
            LOG.debug("Asked to resolve class {} from bundle {} but osgi manager is unavailable in context {}",
                    new Object[]{entityTypeName, bundleUrl, mgmt});
            return Maybe.absent();
        }
        Maybe<Class<T>> clazz = osgiManager.get().tryResolveClass(bundleUrl, entityTypeName);
        if (!clazz.isPresent() || !Entity.class.isAssignableFrom(clazz.get())) {
            return Maybe.absent();
        }
        return clazz;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> Maybe<Class<T>> tryLoadEntityFromCatalogue(String entityTypeName, ManagementContext mgmt) {
        try {
            return (Maybe<Class<T>>)(Maybe<?>) Maybe.<Class<? extends Entity>>of(mgmt.getCatalog().loadClassByType(entityTypeName, Entity.class));
        } catch (NoSuchElementException e) {
            LOG.debug("Class {} not found in catalogue classpath", entityTypeName);
            return Maybe.absent();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Maybe<Class<T>> tryLoadFromClasspath(String typeName, ManagementContext mgmt) {
        Class<T> clazz;
        try {
            clazz = (Class<T>) mgmt.getCatalog().getRootClassLoader().loadClass(typeName);
        } catch (ClassNotFoundException e) {
            LOG.debug("Class {} not found on classpath", typeName);
            return Maybe.absent(new Throwable("Could not find "+typeName+" on classpath"));
        }

        return Maybe.of(clazz);
    }
}

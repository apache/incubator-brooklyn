package io.brooklyn.camp.brooklyn.spi.creation;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.management.ManagementContext;

import com.google.common.base.Optional;

/**
 * Resolves a class name to a <code>Class&lt;? extends Entity&gt;</code> with a given 
 * {@link brooklyn.management.ManagementContext management context}.
 */
public class BrooklynEntityClassResolver {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynEntityClassResolver.class);

    /**
     * Loads the class represented by {@link #entityType} with the given management context.
     * Tries the context's catalogue first, then from its root classloader.
     * @throws java.lang.IllegalStateException if no class extending {@link Entity} is found
     */
    public static Class<? extends Entity> resolve(String entityType, ManagementContext mgmt) {
        checkNotNull(mgmt, "management context");
        Optional<Class<? extends Entity>> entityClazz = tryLoadFromCatalogue(entityType, mgmt);
        if (!entityClazz.isPresent()) entityClazz = tryLoadFromClasspath(entityType, mgmt);
        if (!entityClazz.isPresent()) {
            LOG.warn("No catalog item for {} and could not load class directly; throwing", entityType);
            throw new IllegalStateException("Unable to load class "+ entityType +" (extending Entity) from catalogue or classpath");
        }
        return entityClazz.get();
    }

    private static Optional<Class<? extends Entity>> tryLoadFromCatalogue(String entityType, ManagementContext mgmt) {
        try {
            return Optional.<Class<? extends Entity>>of(mgmt.getCatalog().loadClassByType(entityType, Entity.class));
        } catch (NoSuchElementException e) {
            LOG.debug("Class {} not found in catalogue classpath", entityType);
            return Optional.absent();
        }
    }

    private static Optional<Class<? extends Entity>> tryLoadFromClasspath(String entityType, ManagementContext mgmt) {
        Class<?> clazz;
        try {
            clazz = mgmt.getCatalog().getRootClassLoader().loadClass(entityType);
        } catch (ClassNotFoundException e) {
            LOG.debug("Class {} not found on classpath", entityType);
            return Optional.absent();
        }

        if (Entity.class.isAssignableFrom(clazz)) {
            return Optional.<Class<? extends Entity>>of((Class<Entity>) clazz);
        } else {
            LOG.debug("Found class {} on classpath but it is not assignable to {}", entityType, Entity.class);
            return Optional.absent();
        }
    }
}

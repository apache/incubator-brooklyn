package io.brooklyn.camp.brooklyn.spi.creation;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import brooklyn.entity.Entity;
import brooklyn.management.ManagementContext;
import brooklyn.util.text.Strings;

/**
 * Resolves a class name to a <code>Class&lt;? extends Entity&gt;</code> with the
 * {@link brooklyn.management.ManagementContext management context} given to
 * {@link #apply(brooklyn.management.ManagementContext) apply}.
 * <p/>
 * Link the class name to an entity spec's configuration by providing the specConfiguration
 * constructor parameter.
 */
public class BrooklynEntityClassResolver implements Function<ManagementContext, Class<? extends Entity>> {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynEntityClassResolver.class);

    /** The name of a class that extends {@link Entity} */
    private final String entityType;
    private final Map<String, Object> specConfiguration;

    public BrooklynEntityClassResolver(String entityType) {
        this(entityType, ImmutableMap.<String, Object>of());
    }

    public BrooklynEntityClassResolver(Map<String, Object> specConfiguration) {
        this(String.valueOf(checkNotNull(specConfiguration.get("type"), "specConfiguration must have entry with key 'type'")), specConfiguration);
    }

    public BrooklynEntityClassResolver(String entityType, Map<String, Object> specConfiguration) {
        checkArgument(Strings.isNonBlank(entityType), "entityType must not be blank");
        checkNotNull(specConfiguration, "specConfiguration");
        this.entityType = entityType;
        this.specConfiguration = Maps.newHashMap(specConfiguration);
    }

    public Map<String, Object> getSpecConfiguration() {
        return specConfiguration;
    }

    /**
     * Loads the class represented by {@link #entityType} with the given management context.
     * Tries the context's catalogue first, then from its root classloader.
     * @throws java.lang.IllegalStateException if no class extending {@link Entity} is found
     */
    @Override
    public Class<? extends Entity> apply(ManagementContext mgmt) {
        checkNotNull(mgmt, "management context");
        Optional<Class<? extends Entity>> entityClazz = tryLoadFromCatalogue(mgmt).or(tryLoadFromClasspath(mgmt));
        if (!entityClazz.isPresent()) {
            LOG.warn("No catalog item for {} and could not load class directly; throwing", entityType);
            throw new IllegalStateException("Unable to load class "+ entityType +" (extending Entity) from catalogue or classpath");
        }
        return entityClazz.get();
    }

    private Optional<Class<? extends Entity>> tryLoadFromCatalogue(ManagementContext mgmt) {
        try {
            return Optional.<Class<? extends Entity>>of(mgmt.getCatalog().loadClassByType(entityType, Entity.class));
        } catch (NoSuchElementException e) {
            LOG.debug("Class {} not found in catalogue classpath", entityType);
            return Optional.absent();
        }
    }

    private Optional<Class<? extends Entity>> tryLoadFromClasspath(ManagementContext mgmt) {
        Class clazz;
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

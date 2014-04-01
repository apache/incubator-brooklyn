package brooklyn.entity.proxying;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.policy.Enricher;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Gives details of an entity to be created. It describes the entity's configuration, and is
 * reusable to create multiple entities with the same configuration.
 * 
 * To create an EntitySpec, it is strongly encouraged to use {@link #create(Class)} etc.
 * Users who need to implement this are strongly encouraged to extend 
 * {@link brooklyn.entity.proxying.EntitySpec}.
 * 
 * @param <T> The type of entity to be created
 * 
 * @author aled
 */
public class EntitySpec<T extends Entity> implements Serializable {

    private static final long serialVersionUID = -2247153452919128990L;
    
    private static final Logger log = LoggerFactory.getLogger(EntitySpec.class);

    /**
     * Creates a new {@link EntitySpec} instance for an entity of the given type. The returned 
     * {@link EntitySpec} can then be customized.
     * 
     * @param type An {@link Entity} interface
     */
    public static <T extends Entity> EntitySpec<T> create(Class<T> type) {
        return new EntitySpec<T>(type);
    }
    
    /**
     * Creates a new {@link EntitySpec} instance for an entity of the given type. The returned 
     * {@link EntitySpec} can then be customized.
     * 
     * @param type     An {@link Entity} interface
     * @param implType An {@link Entity} implementation, which implements the {@code type} interface
     */
    public static <T extends Entity, U extends T> EntitySpec<T> create(Class<T> type, Class<U> implType) {
        return new EntitySpec<T>(type).impl(implType);
    }
    
    /**
     * Creates a new {@link EntitySpec} instance with the given config, for an entity of the given type.
     * 
     * This is primarily for groovy code; equivalent to {@code EntitySpec.create(type).configure(config)}.
     * 
     * @param config The spec's configuration (see {@link EntitySpec#configure(Map)}).
     * @param type   An {@link Entity} interface
     */
    public static <T extends Entity> EntitySpec<T> create(Map<?,?> config, Class<T> type) {
        return EntitySpec.create(type).configure(config);
    }
    
    /**
     * Wraps an entity spec so its configuration can be overridden without modifying the 
     * original entity spec.
     */
    public static <T extends Entity> EntitySpec<T> create(EntitySpec<T> spec) {
        EntitySpec<T> result = create(spec.getType())
                .displayName(spec.getDisplayName())
                .additionalInterfaces(spec.getAdditionalInterfaces())
                .configure(spec.getConfig())
                .configure(spec.getFlags())
                .policySpecs(spec.getPolicySpecs())
                .policies(spec.getPolicies())
                .enricherSpecs(spec.getEnricherSpecs())
                .enrichers(spec.getEnrichers())
                .addInitializers(spec.getInitializers());
        
        if (spec.getParent() != null) result.parent(spec.getParent());
        if (spec.getImplementation() != null) result.impl(spec.getImplementation());
        
        return result;
    }
    
    public static <T extends Entity> EntitySpec<T> newInstance(Class<T> type) {
        return new EntitySpec<T>(type);
    }

    private final Class<T> type;
    private String id;
    private String displayName;
    private Class<? extends T> impl;
    private Entity parent;
    private final Map<String, Object> flags = Maps.newLinkedHashMap();
    private final Map<ConfigKey<?>, Object> config = Maps.newLinkedHashMap();
    private final List<Policy> policies = Lists.newArrayList();
    private final List<PolicySpec<?>> policySpecs = Lists.newArrayList();
    private final List<Enricher> enrichers = Lists.newArrayList();
    private final List<EnricherSpec<?>> enricherSpecs = Lists.newArrayList();
    private final List<Location> locations = Lists.newArrayList();
    private final Set<Class<?>> additionalInterfaces = Sets.newLinkedHashSet();
    private final List<EntityInitializer> entityInitializers = Lists.newArrayList();
    private volatile boolean immutable;
    
    public EntitySpec(Class<T> type) {
        this.type = type;
    }
    
    /**
     * @return The type of the entity
     */
    public Class<T> getType() {
        return type;
    }
    
    /**
     * @return The id to use when creating the entity, or null if allow brooklyn to generate a unique id.
     */
    public String getId() {
        return id;
    }
    
    /**
     * @return The display name of the entity
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * @return The implementation of the entity; if not null. this overrides any defaults or other configuration
     * 
     * @see ImplementedBy on the entity interface classes for how defaults are defined.
     * @see EntityTypeRegistry for how implementations can be defined globally
     */
    @Nullable
    public Class<? extends T> getImplementation() {
        return impl;
    }
    
    /**
     * @return Additional interfaces (other than just {@link #getType()}) that this entity implements; 
     *         important for when accessing entity through a proxy to determine which interfaces the proxy exposes.
     */
    public Set<Class<?>> getAdditionalInterfaces() {
        return additionalInterfaces;
    }

    /** @return {@link EntityInitializer} objects which customize the entity to be created */
    public List<EntityInitializer> getInitializers() {
        return entityInitializers;
    }
    
    /**
     * @return The entity's parent
     */
    public Entity getParent() {
        return parent;
    }
    
    /**
     * @return Read-only construction flags
     * @see SetFromFlag declarations on the entity type
     */
    public Map<String, ?> getFlags() {
        return Collections.unmodifiableMap(flags);
    }
    
    /**
     * @return Read-only configuration values
     */
    public Map<ConfigKey<?>, Object> getConfig() {
        return Collections.unmodifiableMap(config);
    }
        
    public List<PolicySpec<?>> getPolicySpecs() {
        return policySpecs;
    }
    
    public List<Policy> getPolicies() {
        return policies;
    }
    
    public List<EnricherSpec<?>> getEnricherSpecs() {
        return enricherSpecs;
    }
    
    public List<Enricher> getEnrichers() {
        return enrichers;
    }
    
    public List<Location> getLocations() {
        return locations;
    }

    public EntitySpec<T> id(String val) {
        checkMutable();
        id = val;
        return this;
    }

    public EntitySpec<T> displayName(String val) {
        checkMutable();
        displayName = val;
        return this;
    }

    public EntitySpec<T> impl(Class<? extends T> val) {
        checkMutable();
        checkIsImplementation(checkNotNull(val, "impl"));
        checkIsNewStyleImplementation(val);
        impl = val;
        return this;
    }

    public EntitySpec<T> additionalInterfaces(Class<?>... vals) {
        checkMutable();
        for (Class<?> val : vals) {
            additionalInterfaces.add(val);
        }
        return this;
    }

    public EntitySpec<T> additionalInterfaces(Iterable<Class<?>> val) {
        checkMutable();
        additionalInterfaces.addAll(Sets.newLinkedHashSet(val));
        return this;
    }

    public EntitySpec<T> addInitializer(EntityInitializer initializer) {
        checkMutable();
        entityInitializers.add(initializer);
        return this;
    }
        
    public EntitySpec<T> addInitializers(Collection<EntityInitializer> initializer) {
        checkMutable();
        entityInitializers.addAll(initializer);
        return this;
    }

    /** The supplied class must have a public no-arg constructor. */
    public EntitySpec<T> addInitializer(Class<? extends EntityInitializer> initializerType) {
        checkMutable();
        try {
            entityInitializers.add(initializerType.newInstance());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return this;
    }
        
    public EntitySpec<T> parent(Entity val) {
        checkMutable();
        parent = checkNotNull(val, "parent");
        return this;
    }
    
    public EntitySpec<T> configure(Map<?,?> val) {
        checkMutable();
        for (Map.Entry<?, ?> entry: val.entrySet()) {
            if (entry.getKey()==null) throw new NullPointerException("Null key not permitted");
            if (entry.getKey() instanceof CharSequence)
                flags.put(entry.getKey().toString(), entry.getValue());
            else if (entry.getKey() instanceof ConfigKey<?>)
                config.put((ConfigKey<?>)entry.getKey(), entry.getValue());
            else if (entry.getKey() instanceof HasConfigKey<?>)
                config.put(((HasConfigKey<?>)entry.getKey()).getConfigKey(), entry.getValue());
            else {
                log.warn("Spec "+this+" ignoring unknown config key "+entry.getKey());
            }
        }
        return this;
    }
    
    public EntitySpec<T> configure(CharSequence key, Object val) {
        checkMutable();
        flags.put(checkNotNull(key, "key").toString(), val);
        return this;
    }
    
    public <V> EntitySpec<T> configure(ConfigKey<V> key, V val) {
        checkMutable();
        config.put(checkNotNull(key, "key"), val);
        return this;
    }

    public <V> EntitySpec<T> configure(ConfigKey<V> key, Task<? extends V> val) {
        checkMutable();
        config.put(checkNotNull(key, "key"), val);
        return this;
    }

    public <V> EntitySpec<T> configure(HasConfigKey<V> key, V val) {
        checkMutable();
        config.put(checkNotNull(key, "key").getConfigKey(), val);
        return this;
    }

    public <V> EntitySpec<T> configure(HasConfigKey<V> key, Task<? extends V> val) {
        checkMutable();
        config.put(checkNotNull(key, "key").getConfigKey(), val);
        return this;
    }

    /** adds a policy to the spec */
    public <V> EntitySpec<T> policy(Policy val) {
        checkMutable();
        policies.add(checkNotNull(val, "policy"));
        return this;
    }

    /** adds a policy to the spec */
    public <V> EntitySpec<T> policy(PolicySpec<?> val) {
        checkMutable();
        policySpecs.add(checkNotNull(val, "policySpec"));
        return this;
    }

    /** adds the supplied policies to the spec */
    public <V> EntitySpec<T> policySpecs(Iterable<? extends PolicySpec<?>> val) {
        checkMutable();
        policySpecs.addAll(Sets.newLinkedHashSet(checkNotNull(val, "policySpecs")));
        return this;
    }
    
    /** adds the supplied policies to the spec */
    public <V> EntitySpec<T> policies(Iterable<? extends Policy> val) {
        checkMutable();
        policies.addAll(Sets.newLinkedHashSet(checkNotNull(val, "policies")));
        return this;
    }
    
    /** adds a policy to the spec */
    public <V> EntitySpec<T> enricher(Enricher val) {
        checkMutable();
        enrichers.add(checkNotNull(val, "enricher"));
        return this;
    }

    /** adds a policy to the spec */
    public <V> EntitySpec<T> enricher(EnricherSpec<?> val) {
        checkMutable();
        enricherSpecs.add(checkNotNull(val, "enricherSpec"));
        return this;
    }

    /** adds the supplied policies to the spec */
    public <V> EntitySpec<T> enricherSpecs(Iterable<? extends EnricherSpec<?>> val) {
        checkMutable();
        enricherSpecs.addAll(Sets.newLinkedHashSet(checkNotNull(val, "enricherSpecs")));
        return this;
    }
    
    /** adds the supplied policies to the spec */
    public <V> EntitySpec<T> enrichers(Iterable<? extends Enricher> val) {
        checkMutable();
        enrichers.addAll(Sets.newLinkedHashSet(checkNotNull(val, "enrichers")));
        return this;
    }
    
    /** adds a location to the spec */
    public <V> EntitySpec<T> location(Location val) {
        checkMutable();
        locations.add(checkNotNull(val, "location"));
        return this;
    }
    
    /** adds the supplied locations to the spec */
    public <V> EntitySpec<T> locations(Iterable<? extends Location> val) {
        checkMutable();
        locations.addAll(Sets.newLinkedHashSet(checkNotNull(val, "locations")));
        return this;
    }

    /** "seals" this spec, preventing any future changes */
    public EntitySpec<T> immutable() {
        immutable = true;
        return this;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("type", type).toString();
    }
    
    private void checkMutable() {
        if (immutable) throw new IllegalStateException("Cannot modify immutable entity spec "+this);
    }
    
    // TODO Duplicates method in BasicEntityTypeRegistry
    private void checkIsImplementation(Class<?> val) {
        if (!type.isAssignableFrom(val)) throw new IllegalStateException("Implementation "+val+" does not implement "+type);
        if (val.isInterface()) throw new IllegalStateException("Implementation "+val+" is an interface, but must be a non-abstract class");
        if (Modifier.isAbstract(val.getModifiers())) throw new IllegalStateException("Implementation "+val+" is abstract, but must be a non-abstract class");
    }

    // TODO Duplicates method in BasicEntityTypeRegistry, and InternalEntityFactory.isNewStyleEntity
    private void checkIsNewStyleImplementation(Class<?> implClazz) {
        try {
            implClazz.getConstructor(new Class[0]);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Implementation "+implClazz+" must have a no-argument constructor");
        } catch (SecurityException e) {
            throw Exceptions.propagate(e);
        }
        
        if (implClazz.isInterface()) throw new IllegalStateException("Implementation "+implClazz+" is an interface, but must be a non-abstract class");
        if (Modifier.isAbstract(implClazz.getModifiers())) throw new IllegalStateException("Implementation "+implClazz+" is abstract, but must be a non-abstract class");
    }

}

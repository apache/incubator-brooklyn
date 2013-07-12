package brooklyn.location;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.management.Task;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

/**
 * Gives details of a location to be created. It describes the location's configuration, and is
 * reusable to create multiple locations with the same configuration.
 * 
 * To create a LocationSpec, it is strongly encouraged to use {@code create(...)} methods.
 * 
 * @param <T> The type of location to be created
 * 
 * @author aled
 */
public class LocationSpec<T extends Location> {

    private static final Logger log = LoggerFactory.getLogger(LocationSpec.class);
    
    /**
     * Creates a new {@link LocationSpec} instance for a location of the given type. The returned 
     * {@link LocationSpec} can then be customized.
     * 
     * @param type A {@link Location} class
     */
    public static <T extends Location> LocationSpec<T> spec(Class<T> type) {
        return new LocationSpec<T>(type);
    }
    
    /**
     * Creates a new {@link LocationSpec} instance with the given config, for a location of the given type.
     * 
     * This is primarily for groovy code; equivalent to {@code LocationSpec.spec(type).configure(config)}.
     * 
     * @param config The spec's configuration (see {@link LocationSpec#configure(Map)}).
     * @param type   A {@link Location} class
     */
    public static <T extends Location> LocationSpec<T> spec(Map<?,?> config, Class<T> type) {
        return LocationSpec.spec(type).configure(config);
    }
    
    private final Class<T> type;
    private String displayName;
    private Location parent;
    private final Map<String, Object> flags = Maps.newLinkedHashMap();
    private final Map<ConfigKey<?>, Object> config = Maps.newLinkedHashMap();

    protected LocationSpec(Class<T> type) {
        checkIsImplementation(type);
        checkIsNewStyleImplementation(type);
        this.type = type;
    }
    
    public LocationSpec<T> displayName(String val) {
        displayName = val;
        return this;
    }

    public LocationSpec<T> parent(Location val) {
        parent = checkNotNull(val, "parent");
        return this;
    }
    
    public LocationSpec<T> configure(Map<?,?> val) {
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
    
    public LocationSpec<T> configure(CharSequence key, Object val) {
        flags.put(checkNotNull(key, "key").toString(), val);
        return this;
    }
    
    public <V> LocationSpec<T> configure(ConfigKey<V> key, V val) {
        config.put(checkNotNull(key, "key"), val);
        return this;
    }

    public <V> LocationSpec<T> configure(ConfigKey<V> key, Task<? extends V> val) {
        config.put(checkNotNull(key, "key"), val);
        return this;
    }

    public <V> LocationSpec<T> configure(HasConfigKey<V> key, V val) {
        config.put(checkNotNull(key, "key").getConfigKey(), val);
        return this;
    }

    public <V> LocationSpec<T> configure(HasConfigKey<V> key, Task<? extends V> val) {
        config.put(checkNotNull(key, "key").getConfigKey(), val);
        return this;
    }

    /**
     * @return The type of the location
     */
    public Class<T> getType() {
        return type;
    }
    
    /**
     * @return The display name of the location
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * @return The location's parent
     */
    public Location getParent() {
        return parent;
    }
    
    /**
     * @return Read-only construction flags
     * @see SetFromFlag declarations on the location type
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
        
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("type", type).toString();
    }
    
    // TODO Duplicates method in BasicEntitySpec and BasicEntityTypeRegistry
    private void checkIsImplementation(Class<?> val) {
        if (!Location.class.isAssignableFrom(val)) throw new IllegalStateException("Implementation "+val+" does not implement "+Location.class.getName());
        if (val.isInterface()) throw new IllegalStateException("Implementation "+val+" is an interface, but must be a non-abstract class");
        if (Modifier.isAbstract(val.getModifiers())) throw new IllegalStateException("Implementation "+val+" is abstract, but must be a non-abstract class");
    }

    // TODO Duplicates method in BasicEntitySpec, BasicEntityTypeRegistry, and InternalEntityFactory.isNewStyleEntity
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

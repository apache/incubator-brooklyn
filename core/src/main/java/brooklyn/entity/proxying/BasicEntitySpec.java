package brooklyn.entity.proxying;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Entity;
import brooklyn.management.Task;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

public class BasicEntitySpec<T extends Entity> implements EntitySpec<T> {

    private final Class<T> type;
    private Class<? extends T> impl;
    private Entity parent;
    private final Map<String, Object> flags = Maps.newLinkedHashMap();
    private final Map<ConfigKey<?>, Object> config = Maps.newLinkedHashMap();
    private final Map<HasConfigKey<?>, Object> config2 = Maps.newLinkedHashMap();

    public static <T extends Entity> BasicEntitySpec<T> newInstance(Class<T> type) {
        return new BasicEntitySpec<T>(type);
    }

    public BasicEntitySpec(Class<T> type) {
        this.type = type;
    }
    
    public BasicEntitySpec<T> impl(Class<? extends T> val) {
        checkIsImplementation(checkNotNull(val, "impl"));
        checkIsNewStyleImplementation(val);
        impl = val;
        return this;
    }

    public BasicEntitySpec<T> parent(Entity val) {
        parent = checkNotNull(val, "parent");
        return this;
    }
    
    public BasicEntitySpec<T> configure(Map<String,?> val) {
        flags.putAll(checkNotNull(val, "key"));
        return this;
    }
    
    public BasicEntitySpec<T> configure(String key, Object val) {
        flags.put(checkNotNull(key, "key"), val);
        return this;
    }
    
    public <V> BasicEntitySpec<T> configure(ConfigKey<V> key, V val) {
        config.put(checkNotNull(key, "key"), val);
        return this;
    }

    public <V> BasicEntitySpec<T> configure(ConfigKey<V> key, Task<? extends V> val) {
        config.put(checkNotNull(key, "key"), val);
        return this;
    }

    public <V> BasicEntitySpec<T> configure(HasConfigKey<V> key, V val) {
        config2.put(checkNotNull(key, "key"), val);
        return this;
    }

    public <V> BasicEntitySpec<T> configure(HasConfigKey<V> key, Task<? extends V> val) {
        config2.put(checkNotNull(key, "key"), val);
        return this;
    }

    @Override
    public Class<T> getType() {
        return type;
    }
    
    @Override
    public Class<? extends T> getImplementation() {
        return impl;
    }
    
    @Override
    public Entity getParent() {
        return parent;
    }
    
    @Override
    public Map<String, ?> getFlags() {
        return Collections.unmodifiableMap(flags);
    }
    
    public Map<ConfigKey<?>, Object> getConfig() {
        return Collections.unmodifiableMap(config);
    }
    
    public Map<HasConfigKey<?>, Object> getConfig2() {
        return Collections.unmodifiableMap(config2);
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("type", type).toString();
    }
    
    // TODO Duplicates method in BasicEntityTypeRegistry
    private void checkIsImplementation(Class<?> val) {
        if (!type.isAssignableFrom(val)) throw new IllegalStateException("Implementation "+val+" does not implement "+type);
        if (val.isInterface()) throw new IllegalStateException("Implementation "+val+" is an interface, but must be a non-abstract class");
        if (Modifier.isAbstract(val.getModifiers())) throw new IllegalStateException("Implementation "+val+" is abstract, but must be a non-abstract class");
    }

    // TODO Duplicates method in BasicEntityTypeRegistry
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

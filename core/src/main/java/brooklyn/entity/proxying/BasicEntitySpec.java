package brooklyn.entity.proxying;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Entity;
import brooklyn.management.Task;
import brooklyn.policy.Policy;

/**
 * @deprecated since 0.6 use EntitySpec.create(...)
 */
public class BasicEntitySpec<T extends Entity, S extends BasicEntitySpec<T,S>> extends EntitySpec<T> {

    private static final Logger log = LoggerFactory.getLogger(BasicEntitySpec.class);
    
    private static class ConcreteEntitySpec<T extends Entity> extends BasicEntitySpec<T, ConcreteEntitySpec<T>> {
        ConcreteEntitySpec(Class<T> type) {
            super(type);
        }
    }

    public static <T extends Entity> BasicEntitySpec<T, ?> newInstance(Class<T> type) {
        return new ConcreteEntitySpec<T>(type);
    }

    public static <T extends Entity, U extends T> BasicEntitySpec<T, ?> newInstance(Class<T> type, Class<U> implType) {
        return new ConcreteEntitySpec<T>(type).impl(implType);
    }

    public BasicEntitySpec(Class<T> type) {
        super(type);
    }
    
    @SuppressWarnings("unchecked")
    protected S self() {
       return (S) this;
    }

    @Override
    public S displayName(String val) {
        super.displayName(val);
        return self();
    }

    @Override
    public S impl(Class<? extends T> val) {
        super.impl(val);
        return self();
    }

    @Override
    public S additionalInterfaces(Class<?>... vals) {
        super.additionalInterfaces(vals);
        return self();
    }

    @Override
    public S additionalInterfaces(Iterable<Class<?>> val) {
        super.additionalInterfaces(val);
        return self();
    }

    @Override
    public S parent(Entity val) {
        super.parent(val);
        return self();
    }
    
    @Override
    public S configure(Map<?,?> val) {
        super.configure(val);
        return self();
    }
    
    @Override
    public S configure(CharSequence key, Object val) {
        super.configure(key, val);
        return self();
    }
    
    @Override
    public <V> S configure(ConfigKey<V> key, V val) {
        super.configure(key, val);
        return self();
    }

    @Override
    public <V> S configure(ConfigKey<V> key, Task<? extends V> val) {
        super.configure(key, val);
        return self();
    }

    @Override
    public <V> S configure(HasConfigKey<V> key, V val) {
        super.configure(key, val);
        return self();
    }

    @Override
    public <V> S configure(HasConfigKey<V> key, Task<? extends V> val) {
        super.configure(key, val);
        return self();
    }

    @Override
    public <V> S policy(Policy val) {
        super.policy(val);
        return self();
    }
}

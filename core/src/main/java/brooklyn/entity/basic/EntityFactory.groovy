package brooklyn.entity.basic;

import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.ConfigKey.HasConfigKey
import brooklyn.location.Location

public interface EntityFactory<T extends Entity> {
    T newEntity(Map flags, Entity owner);
}

public interface ConfigurableEntityFactory<T extends Entity> extends EntityFactory<T> {
    public ConfigurableEntityFactory<T> configure(Map flags);
    public ConfigurableEntityFactory<T> setConfig(ConfigKey key, Object value);
    public ConfigurableEntityFactory<T> setConfig(HasConfigKey key, Object value);
}

public abstract class AbstractConfigurableEntityFactory<T extends Entity> implements ConfigurableEntityFactory<T> {
    protected final Map config = [:];
    public AbstractConfigurableEntityFactory(Map flags=[:]) { 
        this.config << flags;
    }
    public AbstractConfigurableEntityFactory<T> configure(Map flags) {
        config.putAll(flags);
        this
    }
    public AbstractConfigurableEntityFactory<T> setConfig(ConfigKey key, Object value) {
        config.put(key, value);
        this
    }
    public AbstractConfigurableEntityFactory<T> setConfig(HasConfigKey key, Object value) {
        setConfig(key.getConfigKey(), value)
    }
    public T newEntity(Map flags=[:], Entity owner) {
        Map flags2 = [:]
        flags2 << config;
        flags2 << flags;
        newEntity2(flags2, owner);
    }
    public abstract T newEntity2(Map flags, Entity owner);
}

public class BasicConfigurableEntityFactory<T extends Entity> extends AbstractConfigurableEntityFactory<T> {
    private final Class<T> clazz;
    public BasicConfigurableEntityFactory(Map flags=[:], Class<T> clazz) {
        super(flags);
        this.clazz = clazz;
    }
    public T newEntity2(Map flags, Entity owner) {
        T result = clazz.getConstructor(Map, Entity).newInstance(flags, owner);
        return result
    }
}

public class ClosureEntityFactory<T extends Entity> extends AbstractConfigurableEntityFactory<T> {
    private final Closure closure;
    public ClosureEntityFactory(Map flags=[:], Closure closure) {
        super(flags);
        this.closure = closure;
    }
    public T newEntity2(Map flags, Entity owner) {
        if (closure.maximumNumberOfParameters>1)
            closure.call(flags, owner)
        else {
            //leaving out the owner is discouraged 
            AbstractEntity entity = closure.call(flags)
            if (owner && !entity.owner) entity.setOwner(owner)
            entity
        }
    }
}

public class ConfigurableEntityFactoryFromEntityFactory<T extends Entity> extends ClosureEntityFactory<T> {
    public ConfigurableEntityFactoryFromEntityFactory(Map flags=[:], EntityFactory factory) {
        super(flags, factory.&newEntity);
    }
}

/** dispatch interface to allow an EntityFactory to indicate it might be able to discover
 *  other factories for specific locations (e.g. if the location implements a custom entity-aware interface) */
public interface EntityFactoryForLocation<T extends Entity> {
    ConfigurableEntityFactory<T> newFactoryForLocation(Location l);
}

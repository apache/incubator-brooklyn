package brooklyn.entity.basic;

import brooklyn.entity.ConfigKey;
import brooklyn.entity.Entity;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractConfigurableEntityFactory<T extends Entity> implements ConfigurableEntityFactory<T> {
    protected final Map config = new HashMap();

    public AbstractConfigurableEntityFactory(){
        this(new HashMap());
    }

    public AbstractConfigurableEntityFactory(Map flags) {
        this.config.putAll(flags);

    }
    public AbstractConfigurableEntityFactory<T> configure(Map flags) {
        config.putAll(flags);
        return this;
    }

    public AbstractConfigurableEntityFactory<T> setConfig(ConfigKey key, Object value) {
        config.put(key, value);
        return this;
    }

    public AbstractConfigurableEntityFactory<T> setConfig(ConfigKey.HasConfigKey key, Object value) {
        return setConfig(key.getConfigKey(), value);
    }

    public T newEntity(Entity owner){
        return newEntity(new HashMap(),owner);
    }

    public T newEntity(Map flags, Entity owner) {
        Map flags2 = new HashMap();
        flags2.putAll(config);
        flags2.putAll(flags);
        return newEntity2(flags2, owner);
    }

    public abstract T newEntity2(Map flags, Entity owner);
}


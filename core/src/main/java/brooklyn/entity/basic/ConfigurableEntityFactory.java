package brooklyn.entity.basic;

import brooklyn.entity.ConfigKey;
import brooklyn.entity.Entity;

import java.util.Map;

public interface ConfigurableEntityFactory<T extends Entity> extends EntityFactory<T> {
   ConfigurableEntityFactory<T> configure(Map flags);
   ConfigurableEntityFactory<T> setConfig(ConfigKey key, Object value);
   ConfigurableEntityFactory<T> setConfig(ConfigKey.HasConfigKey key, Object value);
}
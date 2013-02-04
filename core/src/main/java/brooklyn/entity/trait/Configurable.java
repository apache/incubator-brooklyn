package brooklyn.entity.trait;

import brooklyn.config.ConfigKey;

public interface Configurable {

    public <T> T setConfig(ConfigKey<T> key, T val);

}

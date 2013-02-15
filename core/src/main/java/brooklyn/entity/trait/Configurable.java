package brooklyn.entity.trait;

import brooklyn.config.ConfigKey;

public interface Configurable {

    /** returns the old value, or null if there was not one */
    public <T> T setConfig(ConfigKey<T> key, T val);

}

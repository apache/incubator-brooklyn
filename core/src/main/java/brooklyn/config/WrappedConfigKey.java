package brooklyn.config;

import com.google.common.base.Preconditions;

import brooklyn.config.ConfigKey.HasConfigKey;

public class WrappedConfigKey<T> implements HasConfigKey<T> {

    private final ConfigKey<T> key;
    
    public WrappedConfigKey(ConfigKey<T> key) {
        this.key = Preconditions.checkNotNull(key);
    }

    @Override
    public ConfigKey<T> getConfigKey() {
        return key;
    }

    @Override
    public String toString() {
        return key.toString()+"(wrapped)";
    }
    
}

package brooklyn.event.basic;

import java.util.Map;

import brooklyn.entity.ConfigKey;
import brooklyn.management.ExecutionContext;

public class SubElementConfigKey<T> extends BasicConfigKey<T> {
    public final ConfigKey parent;

    public SubElementConfigKey(ConfigKey parent, Class<T> type, String name) {
        this(parent, type, name, name, null);
    }
    public SubElementConfigKey(ConfigKey parent, Class<T> type, String name, String description) {
        this(parent, type, name, description, null);
    }
    public SubElementConfigKey(ConfigKey parent, Class<T> type, String name, String description, T defaultValue) {
        super(type, name, description, defaultValue);
        this.parent = parent;
    }
    
    @Override
    public T extractValue(Map vals, ExecutionContext exec) {
        return super.extractValue(vals, exec);
    }
    
    @Override
    public boolean isSet(Map<?,?> vals) {
        return super.isSet(vals);
    }
}

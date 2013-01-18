package brooklyn.entity.proxying;

import java.util.Collections;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Entity;
import brooklyn.util.MutableMap;

public class WrappingEntitySpec<T extends Entity> extends BasicEntitySpec<T, WrappingEntitySpec<T>> {

    private final EntitySpec<T> delegate;

    public static <T extends Entity> WrappingEntitySpec<T> newInstance(EntitySpec<T> delegate) {
        return new WrappingEntitySpec<T>(delegate);
    }

    public WrappingEntitySpec(EntitySpec<T> delegate) {
        super(delegate.getType());
        this.delegate = delegate;
    }
    
    @Override
    public Class<? extends T> getImplementation() {
        return (super.getImplementation() == null) ? delegate.getImplementation() : super.getImplementation();
    }
    
    @Override
    public Entity getParent() {
        return (super.getParent() == null) ? delegate.getParent() : super.getParent();
    }
    
    @Override
    public Map<String, ?> getFlags() {
        return Collections.unmodifiableMap(MutableMap.<String,Object>builder()
                .putAll(delegate.getFlags())
                .putAll(super.getFlags())
                .build());
    }
    
    public Map<ConfigKey<?>, Object> getConfig() {
        return Collections.unmodifiableMap(MutableMap.<ConfigKey<?>,Object>builder()
                .putAll(delegate.getConfig())
                .putAll(super.getConfig())
                .build());
    }
    
    public Map<HasConfigKey<?>, Object> getConfig2() {
        return Collections.unmodifiableMap(MutableMap.<HasConfigKey<?>,Object>builder()
                .putAll(delegate.getConfig2())
                .putAll(super.getConfig2())
                .build());
    }
}

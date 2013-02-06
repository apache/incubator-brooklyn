package brooklyn.entity.proxying;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.policy.Policy;
import brooklyn.util.MutableMap;

import com.google.common.collect.ImmutableList;

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
    public String getDisplayName() {
        return (super.getDisplayName() == null) ? delegate.getDisplayName() : super.getDisplayName();
    }

    @Override
    public List<Policy> getPolicies() {
        return ImmutableList.<Policy>builder()
                .addAll(delegate.getPolicies())
                .addAll(super.getPolicies())
                .build();
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
    
}

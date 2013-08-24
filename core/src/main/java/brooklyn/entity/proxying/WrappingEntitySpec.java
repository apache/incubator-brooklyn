package brooklyn.entity.proxying;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.policy.Policy;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;

import com.google.common.collect.ImmutableList;

/**
 * @deprecated since 0.6; use {@link EntitySpec#create(EntitySpec)}
 * 
 * @author aled
 */
public class WrappingEntitySpec<T extends Entity> extends BasicEntitySpec<T, WrappingEntitySpec<T>> {

    private final EntitySpec<? extends T> delegate;

    public static <T extends Entity> WrappingEntitySpec<T> newInstance(EntitySpec<? extends T> delegate) {
        return new WrappingEntitySpec<T>(delegate);
    }

    public WrappingEntitySpec(EntitySpec<? extends T> delegate) {
        super((Class<T>)delegate.getType());
        this.delegate = delegate;
    }
    
    @Override
    public Class<? extends T> getImplementation() {
        return (super.getImplementation() == null) ? delegate.getImplementation() : super.getImplementation();
    }
    
    @Override
    public Set<Class<?>> getAdditionalInterfaces() {
        return Collections.unmodifiableSet(MutableSet.<Class<?>>builder()
                .addAll(super.getAdditionalInterfaces())
                .addAll(delegate.getAdditionalInterfaces())
                .build());
    }
    
    @Override
    public List<EntityInitializer> getInitializers() {
        return ImmutableList.<EntityInitializer>builder()
                .addAll(super.getInitializers())
                .addAll(delegate.getInitializers())
                .build();
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

package brooklyn.policy.basic;

import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.policy.EnricherType;

import com.google.common.base.Objects;

/**
 * This is the actual type of an enricher instance.
 */
public class EnricherTypeImpl implements EnricherType {
    private static final long serialVersionUID = 668629178669109738L;
    
    private final AdjunctType delegate;

    public EnricherTypeImpl(AdjunctType delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
    
    @Override
    public Set<ConfigKey<?>> getConfigKeys() {
        return delegate.getConfigKeys();
    }
    
    @Override
    public ConfigKey<?> getConfigKey(String name) {
        return delegate.getConfigKey(name);
    }
    
    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EnricherType)) return false;
        EnricherType o = (EnricherType) obj;
        
        return Objects.equal(getName(), o.getName()) && Objects.equal(getConfigKeys(), o.getConfigKeys());
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(getName())
                .add("configKeys", getConfigKeys())
                .toString();
    }
}

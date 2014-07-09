package brooklyn.policy.basic;

import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.policy.PolicyType;

import com.google.common.base.Objects;

/**
 * This is the actual type of a policy instance at runtime.
 */
public class PolicyTypeImpl implements PolicyType {
    private static final long serialVersionUID = -7370390838599315481L;
    
    private final AdjunctType delegate;

    public PolicyTypeImpl(AdjunctType delegate) {
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
        if (!(obj instanceof PolicyType)) return false;
        PolicyType o = (PolicyType) obj;
        
        return Objects.equal(getName(), o.getName()) && Objects.equal(getConfigKeys(), o.getConfigKeys());
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(getName())
                .add("configKeys", getConfigKeys())
                .toString();
    }
}

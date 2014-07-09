package brooklyn.entity.basic;

import brooklyn.policy.Policy;

import com.google.common.base.Objects;

public class PolicyDescriptor {

    private final String id;
    private final String type;
    private final String name;

    public PolicyDescriptor(Policy policy) {
        this.id = policy.getId();
        this.type = policy.getPolicyType().getName();
        this.name = policy.getName();
    }
    public String getId() {
        return id;
    }
    
    public String getPolicyType() {
        return type;
    }
    
    public String getName() {
        return name;
    }
    
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PolicyDescriptor)) {
            return false;
        }
        PolicyDescriptor o = (PolicyDescriptor) other;
        return Objects.equal(id, o.id) && Objects.equal(type, o.type) && Objects.equal(name, o.name);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("id", id).add("type", type).add("name",  name).omitNullValues().toString();
    }
}

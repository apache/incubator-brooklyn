package brooklyn.management.internal;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.event.AttributeSensor;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

public interface EntityChangeListener {

    public static final EntityChangeListener NOOP = new EntityChangeListener() {
        @Override public void onChanged() {}
        @Override public void onAttributeChanged(AttributeSensor<?> attribute) {}
        @Override public void onConfigChanged(ConfigKey<?> key) {}
        @Override public void onLocationsChanged() {}
        @Override public void onMembersChanged() {}
        @Override public void onChildrenChanged() {}
        @Override public void onPolicyAdded(Policy policy) {}
        @Override public void onPolicyRemoved(Policy policy) {}
        @Override public void onEnricherAdded(Enricher enricher) {}
        @Override public void onEnricherRemoved(Enricher enricher) {}
        @Override public void onEffectorStarting(Effector<?> effector) {}
        @Override public void onEffectorCompleted(Effector<?> effector) {}
    };
    
    void onChanged();

    void onAttributeChanged(AttributeSensor<?> attribute);

    void onConfigChanged(ConfigKey<?> key);

    void onLocationsChanged();

    void onMembersChanged();

    void onChildrenChanged();

    void onPolicyAdded(Policy policy);

    void onPolicyRemoved(Policy policy);

    void onEnricherAdded(Enricher enricher);

    void onEnricherRemoved(Enricher enricher);

    void onEffectorStarting(Effector<?> effector);
    
    void onEffectorCompleted(Effector<?> effector);
}

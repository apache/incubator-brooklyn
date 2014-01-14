package brooklyn.management.internal;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.event.AttributeSensor;

public interface EntityChangeListener {

    public static final EntityChangeListener NOOP = new EntityChangeListener() {
        @Override public void onAttributeChanged(AttributeSensor<?> attribute) {}
        @Override public void onConfigChanged(ConfigKey<?> key) {}
        @Override public void onLocationsChanged() {}
        @Override public void onMembersChanged() {}
        @Override public void onChildrenChanged() {}
        @Override public void onPoliciesChanged() {}
        @Override public void onEffectorStarting(Effector<?> effector) {}
        @Override public void onEffectorCompleted(Effector<?> effector) {}
    };
    
    void onAttributeChanged(AttributeSensor<?> attribute);

    void onConfigChanged(ConfigKey<?> key);

    void onLocationsChanged();

    void onMembersChanged();

    void onChildrenChanged();

    void onPoliciesChanged();

    void onEffectorStarting(Effector<?> effector);
    
    void onEffectorCompleted(Effector<?> effector);
}

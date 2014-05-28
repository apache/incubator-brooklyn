package brooklyn.management.internal;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.event.AttributeSensor;

public interface EntityChangeListener {

    public static final EntityChangeListener NOOP = new EntityChangeListener() {
        @Override public void onChanged() {}
        @Override public void onAttributeChanged(AttributeSensor<?> attribute) {}
        @Override public void onConfigChanged(ConfigKey<?> key) {}
        @Override public void onLocationsChanged() {}
        @Override public void onMembersChanged() {}
        @Override public void onChildrenChanged() {}
        @Override public void onPoliciesChanged() {}
        @Override public void onEnrichersChanged() {}
        @Override public void onEffectorStarting(Effector<?> effector) {}
        @Override public void onEffectorCompleted(Effector<?> effector) {}
    };
    
    void onChanged();

    void onAttributeChanged(AttributeSensor<?> attribute);

    void onConfigChanged(ConfigKey<?> key);

    void onLocationsChanged();

    void onMembersChanged();

    void onChildrenChanged();

    // FIXME Also want something to be notified when policy's state/config changes.
    //       Do we want a separate PolicyChangeListener instead? Or everything through EntityChangeListener?
    void onPoliciesChanged();

    void onEnrichersChanged();

    void onEffectorStarting(Effector<?> effector);
    
    void onEffectorCompleted(Effector<?> effector);
}

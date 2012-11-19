package brooklyn.policy.ha;

import brooklyn.event.basic.BasicNotificationSensor;

public class HASensors {

    public static final BasicNotificationSensor<FailureDescriptor> ENTITY_FAILED = new BasicNotificationSensor<FailureDescriptor>(
            FailureDescriptor.class, "ha.entityFailed", "Indicates that an entity has failed");
    
    public static final BasicNotificationSensor<FailureDescriptor> ENTITY_RECOVERED = new BasicNotificationSensor<FailureDescriptor>(
            FailureDescriptor.class, "ha.entityRecovered", "Indicates that a previously failed entity has recovered");
    
    // TODO How to make this serializable with the entity reference
    public static class FailureDescriptor {
        private final Object component;
        private final String description;
        
        public FailureDescriptor(Object component, String description) {
            this.component = component;
            this.description = description;
        }
        
        public Object getComponent() {
            return component;
        }
        
        public String getDescription() {
            return description;
        }
    }
}

package brooklyn.event.feed.jmx;

import javax.management.ObjectName;

public class JmxFeedBuilderWithObjectName {

    // FIXME Do what with this?
    
//    private final JmxFeed delegate;
//    private final ObjectName objectName;
//    
//    public JmxSensorAdapterWithObjectName(JmxFeed delegate, ObjectName objectName) {
//        this.delegate = delegate;
//        this.objectName = objectName;
//    }
//    
//    public <T> void pollAttribute(JmxAttributePollConfig<T> partialConfig) {
//        JmxAttributePollConfig<T> config = new JmxAttributePollConfig<T>(partialConfig).objectName(objectName);
//        delegate.pollAttribute(config);
//    }
//
//    public <T> void pollOperation(JmxOperationPollConfig<T> partialConfig) {
//        JmxOperationPollConfig<T> config = new JmxOperationPollConfig<T>(partialConfig).objectName(objectName);
//        delegate.pollOperation(config);
//    }
//    
//    public <T> void subscribeToNotification(JmxNotificationSubscriptionConfig<T> partialConfig) {
//        JmxNotificationSubscriptionConfig<T> config = new JmxNotificationSubscriptionConfig<T>(partialConfig).objectName(objectName);
//        delegate.subscribeToNotification(config);
//    }
}

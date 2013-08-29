package brooklyn.entity.messaging.activemq;

import brooklyn.entity.proxying.EntitySpec;

public class ActiveMQ {

    public static EntitySpec<ActiveMQBroker> brokerSpec() {
        return EntitySpec.create(ActiveMQBroker.class);
    }
    
    public static EntitySpec<ActiveMQBroker> brokerSpecChef() {
        return EntitySpec.create(ActiveMQBroker.class);
    }

}

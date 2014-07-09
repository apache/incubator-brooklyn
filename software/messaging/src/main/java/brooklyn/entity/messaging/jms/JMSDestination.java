package brooklyn.entity.messaging.jms;

import brooklyn.entity.Entity;

public interface JMSDestination extends Entity {
    public String getName();
    
    public void delete();

    public void destroy();
}

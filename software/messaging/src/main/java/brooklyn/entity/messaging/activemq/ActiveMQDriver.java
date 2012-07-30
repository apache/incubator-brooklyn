package brooklyn.entity.messaging.activemq;

import brooklyn.entity.basic.lifecycle.JavaSoftwareProcessDriver;

public interface ActiveMQDriver extends JavaSoftwareProcessDriver {

    Integer getOpenWirePort();
}

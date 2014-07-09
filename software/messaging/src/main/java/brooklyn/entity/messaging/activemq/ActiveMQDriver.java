package brooklyn.entity.messaging.activemq;

import brooklyn.entity.java.JavaSoftwareProcessDriver;

public interface ActiveMQDriver extends JavaSoftwareProcessDriver {

    Integer getOpenWirePort();
}

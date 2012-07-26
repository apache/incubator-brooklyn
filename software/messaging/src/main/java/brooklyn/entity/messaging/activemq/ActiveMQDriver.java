package brooklyn.entity.messaging.activemq;

import brooklyn.entity.basic.lifecycle.JavaStartStopDriver;

public interface ActiveMQDriver extends JavaStartStopDriver {

    Integer getOpenWirePort();
}

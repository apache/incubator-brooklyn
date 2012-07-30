package brooklyn.entity.messaging.qpid;

import brooklyn.entity.basic.lifecycle.JavaSoftwareProcessDriver;

public interface QpidDriver extends JavaSoftwareProcessDriver {

    Integer getAmqpPort();

    String getAmqpVersion();
}

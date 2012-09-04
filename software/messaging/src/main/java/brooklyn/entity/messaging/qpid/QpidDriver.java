package brooklyn.entity.messaging.qpid;

import brooklyn.entity.java.JavaSoftwareProcessDriver;

public interface QpidDriver extends JavaSoftwareProcessDriver {

    Integer getAmqpPort();

    String getAmqpVersion();
}

package brooklyn.entity.messaging.qpid;

import brooklyn.entity.basic.lifecycle.JavaStartStopDriver;

public interface QpidDriver extends JavaStartStopDriver {

    Integer getAmqpPort();

    String getAmqpVersion();
}

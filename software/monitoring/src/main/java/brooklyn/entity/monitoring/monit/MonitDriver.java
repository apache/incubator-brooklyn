package brooklyn.entity.monitoring.monit;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface MonitDriver extends SoftwareProcessDriver {

    String getStatusCmd();

}

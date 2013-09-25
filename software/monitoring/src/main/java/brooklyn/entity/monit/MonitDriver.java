package brooklyn.entity.monit;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface MonitDriver extends SoftwareProcessDriver {

    String getStatusCmd();

}

package brooklyn.entity.database.mariadb;

import brooklyn.entity.basic.SoftwareProcessDriver;
import brooklyn.util.task.system.ProcessTaskWrapper;

/**
 * The {@link SoftwareProcessDriver} for MariaDB.
 */
public interface MariaDbDriver extends SoftwareProcessDriver {
    public String getStatusCmd();
    public ProcessTaskWrapper<Integer> executeScriptAsync(String commands);
}

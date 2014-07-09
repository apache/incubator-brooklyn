package brooklyn.entity.database.mysql;

import brooklyn.entity.basic.SoftwareProcessDriver;
import brooklyn.util.task.system.ProcessTaskWrapper;

/**
 * The {@link SoftwareProcessDriver} for MySQL.
 */
public interface MySqlDriver extends SoftwareProcessDriver {
    public String getStatusCmd();
    public ProcessTaskWrapper<Integer> executeScriptAsync(String commands);
}

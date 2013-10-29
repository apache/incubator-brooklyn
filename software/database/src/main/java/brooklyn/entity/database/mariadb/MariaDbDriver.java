package brooklyn.entity.database.mariadb;

import brooklyn.entity.basic.SoftwareProcessDriver;

/**
 * The {@link SoftwareProcessDriver} for MariaDB.
 */
public interface MariaDbDriver extends SoftwareProcessDriver {
    public String getStatusCmd();
}

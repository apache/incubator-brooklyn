package brooklyn.entity.database.postgresql;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.database.DatabaseNode;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

/**
 * 
 * Usage notes:
 * <li> On OS X you may need to increase kernel/memory settings, as per http://willbryant.net/software/mac_os_x/postgres_initdb_fatal_shared_memory_error_on_leopard .
 * <li> (You will also need to enable passwordless sudo.)
 */
@Catalog(name="PostgreSQL Node", description="PostgreSQL is an object-relational database management system (ORDBMS)", iconUrl="classpath:///postgresql-logo.jpeg")
@ImplementedBy(PostgreSqlNodeImpl.class)
public interface PostgreSqlNode extends SoftwareProcess, DatabaseNode {

    @SetFromFlag("port")
    public static final PortAttributeSensorAndConfigKey POSTGRESQL_PORT =
            new PortAttributeSensorAndConfigKey("postgresql.port", "PostgreSQL port", PortRanges.fromString("5432+"));
    
    @SetFromFlag("disconnectOnStop")
    public static final ConfigKey<Boolean> DISCONNECT_ON_STOP =
            ConfigKeys.newBooleanConfigKey("postgresql.disconnect.on.stop", "If true, PostgreSQL will immediately disconnet (pg_ctl -m immediate stop) all current connections when the node is stopped", true);

    public String executeScript(String commands);
}

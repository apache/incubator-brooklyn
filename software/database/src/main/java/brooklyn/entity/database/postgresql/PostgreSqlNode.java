package brooklyn.entity.database.postgresql;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.database.DatabaseNode;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicConfigKey;
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
public interface PostgreSqlNode extends DatabaseNode {

    @SetFromFlag("creationScriptUrl")
    public static final ConfigKey<String> CREATION_SCRIPT_URL =
            new BasicConfigKey<String>(String.class, "postgresql.creation.script.url", "URL where PostgreSQL creation script can be found", null);
    
    @SetFromFlag("creationScriptContents")
    public static final ConfigKey<String> CREATION_SCRIPT_CONTENTS =
            new BasicConfigKey<String>(String.class, "postgresql.creation.script", "PostgreSQL creation script contents", "");

    @SetFromFlag("port")
    public static final PortAttributeSensorAndConfigKey POSTGRESQL_PORT =
            new PortAttributeSensorAndConfigKey("postgresql.port", "PostgreSQL port", PortRanges.fromString("5432+"));

}

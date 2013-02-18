package brooklyn.entity.database.postgresql;

import brooklyn.catalog.Catalog;
import brooklyn.entity.database.DatabaseNode;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

@Catalog(name="PostgreSQL Node", description="PostgreSQL is an object-relational database management system (ORDBMS)", iconUrl="classpath:///postgresql-logo.jpeg")
@ImplementedBy(PostgreSqlNodeImpl.class)
public interface PostgreSqlNode extends DatabaseNode {

    @SetFromFlag("creationScriptUrl")
    public static final BasicAttributeSensorAndConfigKey<String> CREATION_SCRIPT_URL =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "postgresql.creation.script.url", "URL where PostgreSQL creation script can be found", null);
    
    @SetFromFlag("creationScriptContents")
    public static final BasicAttributeSensorAndConfigKey<String> CREATION_SCRIPT_CONTENTS =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "postgresql.creation.script", "PostgreSQL creation script contents", "");

    @SetFromFlag("port")
    public static final PortAttributeSensorAndConfigKey POSTGRESQL_PORT =
            new PortAttributeSensorAndConfigKey("postgresql.port", "PostgreSQL port", PortRanges.fromString("5432+"));

}

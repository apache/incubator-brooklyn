package brooklyn.entity.database.postgresql;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

public class PostgreSqlNode extends SoftwareProcessEntity {
    public static final AttributeSensor<String> DB_URL = new BasicAttributeSensor<String>(String.class, "database.url",
            "URL where database is listening");

    @SetFromFlag("creationScriptUrl")
    public static final BasicAttributeSensorAndConfigKey<String> CREATION_SCRIPT_URL =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "postgresql.creation.script.url", "URL where PostgreSQL creation script can be found", null);
    
    @SetFromFlag("creationScriptContents")
    public static final BasicAttributeSensorAndConfigKey<String> CREATION_SCRIPT_CONTENTS =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "postgresql.creation.script", "PostgreSQL creation script contents", "");

    @SetFromFlag("port")
    public static final PortAttributeSensorAndConfigKey POSTGRESQL_PORT =
            new PortAttributeSensorAndConfigKey("postgresql.port", "PostgreSQL port", PortRanges.fromString("5432+"));

    public PostgreSqlNode(Entity parent) {
        this(MutableMap.of(), parent);
    }

    public PostgreSqlNode(@SuppressWarnings("rawtypes") Map flags, Entity parent) {
        super(flags, parent);
    }

    public Class getDriverInterface() {
        return PostgreSqlDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        setAttribute(DB_URL, "postgresql://" + getAttribute(HOSTNAME) + ":" + getAttribute(POSTGRESQL_PORT));
        setAttribute(SERVICE_UP, true);  // TODO poll for status, and activity
    }

    public int getPort() {
        return getAttribute(POSTGRESQL_PORT);
    }
}

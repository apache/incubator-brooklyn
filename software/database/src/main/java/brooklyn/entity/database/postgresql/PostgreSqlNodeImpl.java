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

public class PostgreSqlNodeImpl extends SoftwareProcessEntity implements PostgreSqlNode {

    public PostgreSqlNodeImpl() {
        super();
    }

    public PostgreSqlNodeImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }

    public PostgreSqlNodeImpl(@SuppressWarnings("rawtypes") Map flags, Entity parent) {
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

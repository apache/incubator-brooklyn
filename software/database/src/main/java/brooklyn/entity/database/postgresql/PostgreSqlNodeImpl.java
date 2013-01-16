package brooklyn.entity.database.postgresql;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.util.MutableMap;

public class PostgreSqlNodeImpl extends SoftwareProcessImpl implements PostgreSqlNode {

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

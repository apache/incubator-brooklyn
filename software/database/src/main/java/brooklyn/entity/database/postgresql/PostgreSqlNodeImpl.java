package brooklyn.entity.database.postgresql;

import brooklyn.entity.basic.SoftwareProcessImpl;

public class PostgreSqlNodeImpl extends SoftwareProcessImpl implements PostgreSqlNode {

    public Class getDriverInterface() {
        return PostgreSqlDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        setAttribute(DB_URL, "postgresql://" + getLocalHostname() + ":" + getAttribute(POSTGRESQL_PORT) + "/");
        setAttribute(SERVICE_UP, true);  // TODO poll for status, and activity
    }
}

package brooklyn.entity.database.mysql;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.util.MutableMap;

public class MySqlNodeImpl extends SoftwareProcessImpl implements MySqlNode {

    public MySqlNodeImpl() {
    }

    public MySqlNodeImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }

    public MySqlNodeImpl(Map flags) {
        super(flags, null);
    }

    public MySqlNodeImpl(Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public Class getDriverInterface() {
        return MySqlDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        setAttribute(DB_URL, String.format("mysql://%s:%s/", getLocalHostname(), getPort()));
        setAttribute(SERVICE_UP, true);  // TODO poll for status, and activity
    }

    public int getPort() {
        return getAttribute(MYSQL_PORT);
    }
}

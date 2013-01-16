package brooklyn.entity.database.mysql

import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessImpl

public class MySqlNodeImpl extends SoftwareProcessImpl implements MySqlNode {

    public MySqlNodeImpl() {
        super();
    }
    
    public MySqlNodeImpl(Entity parent) {
        this([:], parent);
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
        setAttribute(MYSQL_URL, "mysql://${localHostname}:${port}/")
        setAttribute(SERVICE_UP, true)  // TODO poll for status, and activity
    }

    public int getPort() {
        getAttribute(MYSQL_PORT)
    }
}

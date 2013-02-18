package brooklyn.entity.database.mysql

import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessImpl

import static brooklyn.util.JavaGroovyEquivalents.groovyTruth

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
        setAttribute(DB_URL, "mysql://" + localHostname + ":" + port + "/")
        setAttribute(SERVICE_UP, true)  // TODO poll for status, and activity
    }

    public int getPort() {
        getAttribute(MYSQL_PORT)
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts();
        if (groovyTruth(getAttribute(MYSQL_PORT))) result.add(getAttribute(MYSQL_PORT));
        return result;
    }
}

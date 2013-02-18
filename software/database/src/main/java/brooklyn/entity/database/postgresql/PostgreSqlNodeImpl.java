package brooklyn.entity.database.postgresql;

import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.util.Collection;
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

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Collection<Integer> result = super.getRequiredOpenPorts();
        if (groovyTruth(getAttribute(POSTGRESQL_PORT))) result.add(getAttribute(POSTGRESQL_PORT));
        return result;
    }
}

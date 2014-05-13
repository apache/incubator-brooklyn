package brooklyn.entity.nosql.couchbase;

import static java.lang.String.format;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.config.ConfigBag;

public class CouchbaseNodeImpl extends SoftwareProcessImpl implements CouchbaseNode {

    @Override
    public Class<CouchbaseNodeDriver> getDriverInterface() {
        return CouchbaseNodeDriver.class;
    }

    @Override
    public CouchbaseNodeDriver getDriver() {
        return (CouchbaseNodeDriver) super.getDriver();
    }

    @Override
    public void init() {
        super.init();
        
        subscribe(this, Attributes.SERVICE_UP, new SensorEventListener<Boolean>() {
            @Override
            public void onEvent(SensorEvent<Boolean> booleanSensorEvent) {
                if (Boolean.TRUE.equals(booleanSensorEvent.getValue())) {
                    String hostname = getAttribute(HOSTNAME);
                    String webPort = getConfig(CouchbaseNode.COUCHBASE_WEB_ADMIN_PORT).iterator().next().toString();
                    setAttribute(CouchbaseNode.COUCHBASE_WEB_ADMIN_URL, format("http://%s:%s", hostname, webPort));
                }
            }
        });
    }

    protected Map<String, Object> obtainProvisioningFlags(@SuppressWarnings("rawtypes") MachineProvisioningLocation location) {
        ConfigBag result = ConfigBag.newInstance(super.obtainProvisioningFlags(location));
        result.configure(CloudLocationConfig.OS_64_BIT, true);
        return result.getAllConfig();
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        // TODO this creates a huge list of inbound ports; much better to define on a security group using range syntax!
        int erlangRangeStart = getConfig(NODE_DATA_EXCHANGE_PORT_RANGE_START).iterator().next();
        int erlangRangeEnd = getConfig(NODE_DATA_EXCHANGE_PORT_RANGE_END).iterator().next();

        Set<Integer> newPorts = MutableSet.<Integer>copyOf(super.getRequiredOpenPorts());
        newPorts.remove(erlangRangeStart);
        newPorts.remove(erlangRangeEnd);
        for (int i = erlangRangeStart; i <= erlangRangeEnd; i++)
            newPorts.add(i);
        return newPorts;
    }

    @Override
    public void serverAdd(String serverToAdd, String username, String password) {
        getDriver().serverAdd(serverToAdd, username, password);
    }

    @Override
    public void rebalance() {
        getDriver().rebalance();
    }


    public void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }

    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
    }


}

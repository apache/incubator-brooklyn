package brooklyn.entity.nosql.riak;

import brooklyn.entity.basic.SoftwareProcessImpl;

public class RiakNodeImpl extends SoftwareProcessImpl implements RiakNode {


    @Override
    public RiakNodeDriver getDriver() {
        return (RiakNodeDriver) super.getDriver();
    }

    @Override
    public Class<RiakNodeDriver> getDriverInterface() {
        return RiakNodeDriver.class;
    }


    public void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }

    public void disconnectSensors() {
        disconnectServiceUpIsRunning();
    }


}

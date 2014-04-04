package brooklyn.entity.nosql.riak;

import brooklyn.entity.basic.SoftwareProcessImpl;

public class RiakNodeImpl extends SoftwareProcessImpl implements RiakNode {

    @Override
    public RiakNodeDriver getDriver() {
        return getDriverInterface().cast(super.getDriver());
    }

    @Override
    public Class<RiakNodeDriver> getDriverInterface() {
        return RiakNodeDriver.class;
    }


    public void connectSensors() {

    }

    public void disconnectSensors() {

    }


}

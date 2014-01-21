package brooklyn.entity.basic;


public class VanillaSoftwareProcessImpl extends SoftwareProcessImpl implements VanillaSoftwareProcess {
    @Override
    public Class<?> getDriverInterface() {
        return VanillaSoftwareProcessDriver.class;
    }
    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }
    @Override
    protected void disconnectSensors() {
        disconnectServiceUpIsRunning();
        super.disconnectSensors();
    }
}
package brooklyn.entity.basic;

public class EmptySoftwareProcessImpl extends SoftwareProcessImpl implements EmptySoftwareProcess {

    @Override
    public Class getDriverInterface() {
        return EmptySoftwareProcessDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        super.connectServiceUpIsRunning();
    }
    
    @Override
    protected void disconnectSensors() {
        super.disconnectServiceUpIsRunning();
        super.disconnectSensors();
    }
}

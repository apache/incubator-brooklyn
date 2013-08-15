package brooklyn.entity.driver;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcessDriver;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;

public class MockSshDriver implements SoftwareProcessDriver {

    public int numCallsToRunApp = 0;
    private final EntityLocal entity;
    private final SshMachineLocation machine;

    public MockSshDriver(EntityLocal entity, SshMachineLocation machine) {
        this.entity = entity;
        this.machine = machine;
    }
    
    @Override
    public void startAsync() {
        numCallsToRunApp++;
    }

    @Override
    public boolean isRunning() {
        return numCallsToRunApp>0;
    }

    @Override
    public EntityLocal getEntity() {
        return entity;
    }

    @Override
    public Location getLocation() {
        return machine;
    }

    @Override
    public void rebind() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void restart() {
    }
    
    @Override
    public void kill() {
    }
}

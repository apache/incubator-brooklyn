package brooklyn.entity.basic;

import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.ConfigKey
import brooklyn.location.MachineLocation
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation


public class SoftwareProcessEntityTest {

    @Test(groups="Integration")
    public void testShutdownIsIdempotentInFixedListMachineProvisioningLocation() {
        SshMachineLocation machine = new SshMachineLocation(address:"localhost");
        def loc = new FixedListMachineProvisioningLocation<MachineLocation>(machines:[machine]);
        Application app = new AbstractApplication() {}
        MyService entity = new MyService(owner:app)
        entity.start([loc]);
        entity.stop();
        entity.stop();
    }
    
    private static class MyService extends SoftwareProcessEntity {
        @Override
        public <T> T getConfig(ConfigKey<T> key, T defaultValue=null) {
            return super.getConfig(key, defaultValue)
        }

        Class getDriverInterface() {
            return SimulatedSshBasedAppSetup.class;
        }
    }
}

public class SimulatedSshBasedAppSetup extends AbstractSoftwareProcessSshDriver {
    private volatile boolean launched = false;
    
    SimulatedSshBasedAppSetup(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    @Override
    public boolean isRunning() {
        return launched;
    }

    @Override
    public void stop() {
        launched = false;
    }

    @Override
    public void install() {
    }

    @Override
    public void customize() {
    }

    @Override
    public void launch() {
        launched = true;
    }
}

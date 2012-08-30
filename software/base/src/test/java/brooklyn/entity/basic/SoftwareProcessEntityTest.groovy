package brooklyn.entity.basic;

import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.ConfigKey
import brooklyn.entity.basic.lifecycle.StartStopSshDriver
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
        public StartStopSshDriver newDriver(SshMachineLocation loc) {
            return new SimulatedSshBasedAppSetup(this, loc)
        }
        @Override
        public <T> T getConfig(ConfigKey<T> key, T defaultValue=null) {
            return super.getConfig(key, defaultValue)
        }
    }
}

public class SimulatedSshBasedAppSetup extends StartStopSshDriver {
    SimulatedSshBasedAppSetup(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public void stop() {
    }

    @Override
    public void install() {
    }

    @Override
    public void customize() {
    }

    @Override
    public void launch() {
    }
}

package brooklyn.entity.basic;

import groovy.lang.MetaClass

import java.util.List
import java.util.Map

import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.ConfigKey
import brooklyn.entity.basic.lifecycle.SshBasedAppSetup;
import brooklyn.location.MachineLocation
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SimulatedLocation
import brooklyn.location.basic.SshMachineLocation


public class SoftwareProcessEntityTest {

    @Test
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
        public SshBasedAppSetup getSshBasedSetup(SshMachineLocation loc) {
            return new SimulatedSshBasedAppSetup(this, loc)
        }
        @Override
        public <T> T getConfig(ConfigKey<T> key, T defaultValue=null) {
            return super.getConfig(key, defaultValue)
        }
    }
}

public class SimulatedSshBasedAppSetup extends SshBasedAppSetup {
    SimulatedSshBasedAppSetup(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine)
    }
    public List<String> getRunScript() {
        return []
    }
    public List<String> getCheckRunningScript() {
        return []
    }
}

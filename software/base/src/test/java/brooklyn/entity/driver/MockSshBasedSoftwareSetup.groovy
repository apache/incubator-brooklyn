package brooklyn.entity.driver;

import java.util.List
import java.util.Map

import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.basic.lifecycle.legacy.SshBasedAppSetup;
import brooklyn.location.basic.SshMachineLocation

public class MockSshBasedSoftwareSetup extends SshBasedAppSetup {

    public MockSshBasedSoftwareSetup(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine)
    }
    
    public int numCallsToRunApp = 0;
    
    @Override
    public void runApp() {
        super.runApp();
        numCallsToRunApp++;
    }

    @Override
    public boolean isRunning() {
        return numCallsToRunApp>0;
    }
    
    @Override
    public List<String> getRunScript() {
        return [];
    }

    @Override
    public List<String> getCheckRunningScript() {
        return [];
    }

}

package brooklyn.entity.chef.mysql;

import org.testng.annotations.Test;

import brooklyn.entity.chef.ChefLiveTestSupport;
import brooklyn.entity.software.mysql.AbstractToyMySqlEntityTest;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.SshMachineLocation;

public abstract class AbstractChefToyMySqlEntityLiveTest extends AbstractToyMySqlEntityTest {

    @Override
    // mark as live here
    @Test(groups = "Live")
    public void testMySqlOnProvisioningLocation() throws NoMachinesAvailableException {
        super.testMySqlOnProvisioningLocation();
    }
    
    protected MachineProvisioningLocation<? extends SshMachineLocation> createLocation() {
        return ChefLiveTestSupport.createLocation(mgmt);
    }
    
}

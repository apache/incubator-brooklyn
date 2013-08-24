package brooklyn.entity.chef.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.software.mysql.AbstractToyMySqlEntityTest;
import brooklyn.location.Location;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.SshMachineLocation;

public abstract class AbstractChefToyMySqlEntityLiveTest extends AbstractToyMySqlEntityTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractChefToyMySqlEntityLiveTest.class);
    
    @Override
    @Test(groups = "Live")
    public void testMySqlOnProvisioningLocation() throws NoMachinesAvailableException {
        super.testMySqlOnProvisioningLocation();
    }
    
    // to use a pre-built / fixed IP machine (you might not want to set up Chef on localhost)
    @SuppressWarnings("unchecked")
    protected MachineProvisioningLocation<? extends SshMachineLocation> createLocation() {
        Location bestLocation = mgmt.getLocationRegistry().resolveIfPossible("named:ChefTests");
        if (bestLocation==null) {
            log.info("using AWS for chef tests because named:ChefTests does not exist");
            bestLocation = mgmt.getLocationRegistry().resolveIfPossible("jclouds:aws-ec2");
        }
        if (bestLocation==null) {
            throw new IllegalStateException("Need a location called named:ChefTests or AWS configured for these tests");
        }
        return (MachineProvisioningLocation<? extends SshMachineLocation>)bestLocation; 
    }
    
}

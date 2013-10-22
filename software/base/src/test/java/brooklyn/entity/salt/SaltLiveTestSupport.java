package brooklyn.entity.salt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.BrooklynMgmtContextTestSupport;
import brooklyn.location.Location;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;

public class SaltLiveTestSupport extends BrooklynMgmtContextTestSupport {

    private static final Logger log = LoggerFactory.getLogger(SaltLiveTestSupport.class);
    
    protected MachineProvisioningLocation<? extends SshMachineLocation> targetLocation;

    @Override
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        
        targetLocation = createLocation();
    }

    protected MachineProvisioningLocation<? extends SshMachineLocation> createLocation() {
        return createLocation(mgmt);
    }
    
    /** convenience for setting up a pre-built / fixed IP machine
     * (because you might not want to set up Salt on localhost) 
     * and ensuring tests against Salt use the same configured location 
     **/
    @SuppressWarnings("unchecked")
    public static MachineProvisioningLocation<? extends SshMachineLocation> createLocation(ManagementContext mgmt) {
        Location bestLocation = mgmt.getLocationRegistry().resolveIfPossible("named:SaltTests");
        if (bestLocation==null) {
            log.info("using AWS for salt tests because named:SaltTests does not exist");
            bestLocation = mgmt.getLocationRegistry().resolveIfPossible("jclouds:aws-ec2:us-east-1");
        }
        if (bestLocation==null) {
            throw new IllegalStateException("Need a location called named:SaltTests or AWS configured for these tests");
        }
        return (MachineProvisioningLocation<? extends SshMachineLocation>)bestLocation; 
    }
}

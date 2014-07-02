package brooklyn.entity.salt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.location.Location;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;

public class SaltLiveTestSupport extends BrooklynAppLiveTestSupport {

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

    /**
     * Convenience for setting up a pre-built or fixed IP machine.
     * <p>
     * Useful if you are unable to set up Salt on localhost,
     * and for ensuring tests against Salt always use the same
     * configured location.
     */
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

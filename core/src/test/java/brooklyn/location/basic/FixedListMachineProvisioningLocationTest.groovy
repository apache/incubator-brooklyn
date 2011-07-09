package brooklyn.location.basic

import static org.testng.Assert.*

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test

import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException

/**
 * Provisions {@link SshMachineLocation}s in a specific location from a list of known machines
 */
public class FixedListMachineProvisioningLocationTest {
    MachineProvisioningLocation<SshMachineLocation> provisioner
    @BeforeMethod
    public void createProvisioner() {
        provisioner = new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines:[ new SshMachineLocation(address:Inet4Address.getByName('192.168.144.200')) ]);
    }

    @Test
    public void canGetAMachine() {
        SshMachineLocation machine = provisioner.obtain()
        assertNotNull machine
        assertEquals '192.168.144.200', machine.address.hostAddress
    }

    @Test(expectedExceptions = [ NoMachinesAvailableException.class ])
    public void throwsExceptionIfNoMachinesAvailable() {
        SshMachineLocation machine1 = provisioner.obtain()
        SshMachineLocation machine2 = provisioner.obtain()
        fail "Did not throw NoMachinesAvailableException as expected"
    }

    @Test
    public void canGetAMachineReturnItAndObtainItAgain() {
        SshMachineLocation machine = provisioner.obtain()
        provisioner.release(machine)
        machine = provisioner.obtain()
        assertNotNull machine
        assertEquals '192.168.144.200', machine.address.hostAddress
    }

    @Test(expectedExceptions = [ IllegalStateException.class ])
    public void throwsExceptionIfTryingToReleaseUnallocationMachine() {
        SshMachineLocation machine = provisioner.obtain()
        provisioner.release(new SshMachineLocation(address:Inet4Address.getByName('192.168.144.201')));
        fail "Did not throw IllegalStateException as expected"
    }
}

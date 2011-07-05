package brooklyn.location.basic

import static org.testng.Assert.*

import org.testng.annotations.Test
import brooklyn.location.NoMachinesAvailableException

/**
 * Provisions @{link SshMachineLocation}s in a specific location from a list of known machines
 */
public class FixedListMachineProvisioningLocationTest {
    @Test
    public void canGetAMachine() {
        FixedListMachineProvisioningLocation<SshMachineLocation> provisioner =
            new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines: [new SshMachineLocation(address: Inet4Address.getByAddress((byte[])[192,168,144,200]))]);
        SshMachineLocation machine = provisioner.obtain()
        assertNotNull machine
        assertEquals '192.168.144.200', machine.address.hostAddress
    }

    @Test
    public void throwsExceptionIfNoMachinesAvailable() {
        FixedListMachineProvisioningLocation<SshMachineLocation> provisioner =
            new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines: [new SshMachineLocation(address: Inet4Address.getByAddress((byte[])[192,168,144,200]))]);
        SshMachineLocation machine1 = provisioner.obtain()
        try {
            SshMachineLocation machine2 = provisioner.obtain()
            fail "Did not throw NoMachinesAvailableException as expected"
        } catch(NoMachinesAvailableException e) {
            // expected case
        }
    }

    @Test
    public void canGetAMachineReturnItAndObtainItAgain() {
        FixedListMachineProvisioningLocation<SshMachineLocation> provisioner =
            new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines: [new SshMachineLocation(address: Inet4Address.getByAddress((byte[])[192,168,144,200]))])
        SshMachineLocation machine = provisioner.obtain()
        provisioner.release(machine)
        machine = provisioner.obtain()
        assertNotNull machine
        assertEquals '192.168.144.200', machine.address.hostAddress
    }

    @Test
    public void throwsExceptionIfTryingToReleaseUnallocationMachine() {
        FixedListMachineProvisioningLocation<SshMachineLocation> provisioner =
            new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines: [new SshMachineLocation(address: Inet4Address.getByAddress((byte[])[192,168,144,200]))]);
        SshMachineLocation machine = provisioner.obtain()
        try {
            provisioner.release(new SshMachineLocation(address: Inet4Address.getByAddress((byte[])[192,168,144,201])));
            fail "Did not throw IllegalStateException as expected"
        } catch(IllegalStateException e) {
            // expected case
        }
    }
}

package brooklyn.location.basic

import static org.testng.Assert.*

import org.testng.annotations.Test

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
    public void returnsNullIfNoMachinesAvailable() {
        FixedListMachineProvisioningLocation<SshMachineLocation> provisioner =
            new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines: [new SshMachineLocation(address: Inet4Address.getByAddress((byte[])[192,168,144,200]))]);
        SshMachineLocation machine1 = provisioner.obtain()
        SshMachineLocation machine2 = provisioner.obtain()
        assertNull machine2
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
}

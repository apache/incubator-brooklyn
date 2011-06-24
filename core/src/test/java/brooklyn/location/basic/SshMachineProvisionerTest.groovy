package brooklyn.location.basic

import static org.junit.Assert.*
import org.junit.Test

/**
 * Provisions @{link SshMachine}s in a specific location from a list of known machines
 */
class SshMachineProvisionerTest {

    @Test
    public void canGetAMachine() {
        SshMachineProvisioner provisioner = new SshMachineProvisioner([Inet4Address.getByAddress((byte[])[192,168,144,200])])
        SshMachineLocation machine = provisioner.obtain()
        assertNotNull machine
        assertEquals '192.168.144.200', machine.host.hostAddress
    }

    @Test
    public void returnsNullIfNoMachinesAvailable() {
        SshMachineProvisioner provisioner = new SshMachineProvisioner([Inet4Address.getByAddress((byte[])[192,168,144,200])])
        SshMachineLocation machine1 = provisioner.obtain()
        SshMachineLocation machine2 = provisioner.obtain()
        assertNull machine2
    }

    @Test
    public void canGetAMachineReturnItAndObtainItAgain() {
        SshMachineProvisioner provisioner = new SshMachineProvisioner([Inet4Address.getByAddress((byte[])[192,168,144,200])])
        SshMachineLocation machine = provisioner.obtain()
        provisioner.release(machine)
        machine = provisioner.obtain()
        assertNotNull machine
        assertEquals '192.168.144.200', machine.host.hostAddress
    }

}

package brooklyn.location.basic

import static org.testng.Assert.*

import org.testng.annotations.Test

/**
 * Provisions @{link SshMachine}s in a specific location from a list of known machines
 */
public class SshMachineProvisionerTest {
    @Test
    public void canGetAMachine() {
        SshMachineProvisioner provisioner = new SshMachineProvisioner([new SshMachine(Inet4Address.getByAddress((byte[])[192,168,144,200]))])
        SshMachine machine = provisioner.obtain()
        assertNotNull machine
        assertEquals '192.168.144.200', machine.host.hostAddress
    }

    @Test
    public void returnsNullIfNoMachinesAvailable() {
        SshMachineProvisioner provisioner = new SshMachineProvisioner([new SshMachine(Inet4Address.getByAddress((byte[])[192,168,144,200]))])
        SshMachine machine1 = provisioner.obtain()
        SshMachine machine2 = provisioner.obtain()
        assertNull machine2
    }

    @Test
    public void canGetAMachineReturnItAndObtainItAgain() {
        SshMachineProvisioner provisioner = new SshMachineProvisioner([new SshMachine(Inet4Address.getByAddress((byte[])[192,168,144,200]))])
        SshMachine machine = provisioner.obtain()
        provisioner.release(machine)
        machine = provisioner.obtain()
        assertNotNull machine
        assertEquals '192.168.144.200', machine.host.hostAddress
    }
}

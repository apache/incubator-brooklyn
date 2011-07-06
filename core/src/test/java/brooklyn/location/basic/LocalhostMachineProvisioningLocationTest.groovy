package brooklyn.location.basic

import static org.testng.AssertJUnit.*
import org.testng.annotations.Test
import brooklyn.location.NoMachinesAvailableException

class LocalhostMachineProvisioningLocationTest {

    private static final byte[] localhostIp = [127, 0, 0, 1]

    @Test
    public void defaultInvocationCanProvisionALocalhostInstance() {
        LocalhostMachineProvisioningLocation provisioner = new LocalhostMachineProvisioningLocation()
        SshMachineLocation machine = provisioner.obtain()
        assertNotNull machine
        assertArrayEquals localhostIp, machine.address.address
    }

    @Test public void provisionWithASpecificNumberOfInstances() {
        LocalhostMachineProvisioningLocation provisioner = new LocalhostMachineProvisioningLocation(count: 2)

        // first machine
        SshMachineLocation machine = provisioner.obtain()
        assertNotNull machine
        assertArrayEquals localhostIp, machine.address.address

        // second machine
        machine = provisioner.obtain()
        assertNotNull machine
        assertArrayEquals localhostIp, machine.address.address

        // third machine
        try {
            machine = provisioner.obtain()
            fail "did not throw expected exception"
        } catch (NoMachinesAvailableException e) {
            // expected behaviour
        }
    }
}

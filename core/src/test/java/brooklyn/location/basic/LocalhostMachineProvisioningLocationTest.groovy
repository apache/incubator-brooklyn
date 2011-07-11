package brooklyn.location.basic

import static org.testng.Assert.*

import org.testng.annotations.Test

import brooklyn.location.NoMachinesAvailableException

public class LocalhostMachineProvisioningLocationTest {
    @Test
    public void defaultInvocationCanProvisionALocalhostInstance() {
        LocalhostMachineProvisioningLocation provisioner = new LocalhostMachineProvisioningLocation()
        SshMachineLocation machine = provisioner.obtain()
        assertNotNull machine
        assertEquals machine.address, InetAddress.localHost
    }

    @Test(expectedExceptions = [ NoMachinesAvailableException.class ])
    public void provisionWithASpecificNumberOfInstances() {
        LocalhostMachineProvisioningLocation provisioner = new LocalhostMachineProvisioningLocation(count:2)

        // first machine
        SshMachineLocation first = provisioner.obtain()
        assertNotNull first
        assertEquals first.address, InetAddress.localHost

        // second machine
        SshMachineLocation second = provisioner.obtain()
        assertNotNull second
        assertEquals second.address, InetAddress.localHost

        // third machine
        SshMachineLocation third = provisioner.obtain()
        fail "did not throw expected exception"
    }
}

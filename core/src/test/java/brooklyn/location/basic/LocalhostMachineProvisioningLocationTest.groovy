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
        assertTrue machine.address.isSiteLocalAddress()
    }

    @Test(expectedExceptions = [ NoMachinesAvailableException.class ])
    public void provisionWithASpecificNumberOfInstances() {
        LocalhostMachineProvisioningLocation provisioner = new LocalhostMachineProvisioningLocation(count:2)

        // first machine
        SshMachineLocation first = provisioner.obtain()
        assertNotNull first
        assertTrue first.address.isSiteLocalAddress()

        // second machine
        SshMachineLocation second = provisioner.obtain()
        assertNotNull second
        assertTrue second.address.isSiteLocalAddress()

        // third machine
        SshMachineLocation third = provisioner.obtain()
        fail "did not throw expected exception"
    }
}

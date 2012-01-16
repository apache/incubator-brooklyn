package brooklyn.location.basic

import static org.testng.Assert.*

import org.testng.Assert;
import org.testng.annotations.Test

import brooklyn.location.Location;
import brooklyn.location.NoMachinesAvailableException
import brooklyn.location.PortRange;

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
    
    @Test
    public void obtainTwoAddressesInRangeThenDontObtain() {
        LocalhostMachineProvisioningLocation p = new LocalhostMachineProvisioningLocation();
        Location m = p.obtain([:]);
        int start = 48311;
        PortRange r = PortRanges.fromString(""+start+"-"+(start+1));
        try {
            int i1 = m.obtainPort(r);
            Assert.assertEquals(i1, start);
            int i2 = m.obtainPort(r);
            Assert.assertEquals(i2, start+1);
            
            //should fail
            int i3 = m.obtainPort(r);
            Assert.assertEquals(i3, -1);

            //releasing and reapplying should succed
            m.releasePort(i2);
            int i4 = m.obtainPort(r);
            Assert.assertEquals(i4, i2);

        } finally {
            m.releasePort(start)
            m.releasePort(start+1)
        }
    }
    
    @Test
    public void obtainLowNumberedPortsAutomatically() {
        LocalhostMachineProvisioningLocation p = new LocalhostMachineProvisioningLocation();
        Location m = p.obtain([:]);
        int start = 983;  //random rarely used port, not that it matters
        try {
            int actual = m.obtainPort(PortRanges.fromInteger(start));
            Assert.assertEquals(actual, start);
        } finally {
            m.releasePort(start)
        }

    }
    
}

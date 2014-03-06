package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.net.ServerSocket;

import org.junit.AfterClass;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.LocationSpec;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.PortRange;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.net.Networking;

public class LocalhostMachineProvisioningLocationTest {
    
    private LocalManagementContext mgmt;

    @BeforeClass
    protected void setup() {
        mgmt = new LocalManagementContext();
    }
    
    @AfterClass
    protected void teardown() {
        Entities.destroyAll(mgmt);
    }
    
    protected LocalhostMachineProvisioningLocation newLocalhostProvisioner() {
        return mgmt.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
    }
    
    protected LocalhostMachineProvisioningLocation newLocalhostProvisionerWithAddress(String address) {
        return mgmt.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class)
            .configure("address", address));
    }
    
    @Test
    public void defaultInvocationCanProvisionALocalhostInstance() throws Exception {
        LocalhostMachineProvisioningLocation provisioner = mgmt.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        SshMachineLocation machine = provisioner.obtain();
        assertNotNull(machine);
        assertEquals(machine.address, Networking.getLocalHost());
    }

    @Test
    public void testUsesLocationNameProvided() throws Exception {
        LocalhostMachineProvisioningLocation provisioner = newLocalhostProvisionerWithAddress("localhost");
        assertEquals(((SshMachineLocation)provisioner.obtain()).getAddress().getHostName(), "localhost");

        LocalhostMachineProvisioningLocation provisioner2 = newLocalhostProvisionerWithAddress("1.2.3.4");
        assertEquals(((SshMachineLocation)provisioner2.obtain()).getAddress().getHostName(), "1.2.3.4");
        
        LocalhostMachineProvisioningLocation provisioner3 = newLocalhostProvisionerWithAddress("127.0.0.1");
        assertEquals(((SshMachineLocation)provisioner3.obtain()).getAddress().getHostName(), "127.0.0.1");
    }
    
    public void provisionWithASpecificNumberOfInstances() throws NoMachinesAvailableException {
        LocalhostMachineProvisioningLocation provisioner = mgmt.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class)
            .configure("count", 2));

        // first machine
        SshMachineLocation first = provisioner.obtain();
        assertNotNull(first);
        assertEquals(first.address, Networking.getLocalHost());

        // second machine
        SshMachineLocation second = provisioner.obtain();
        assertNotNull(second);
        assertEquals(second.address, Networking.getLocalHost());

        // third machine - fails
        try {
            SshMachineLocation third = provisioner.obtain();
            fail("did not throw expected exception; got "+third);
        } catch (NoMachinesAvailableException e) {
            /* expected */
        }
    }
    
    @Test
    public void obtainTwoAddressesInRangeThenDontObtain() throws Exception {
        LocalhostMachineProvisioningLocation p = newLocalhostProvisioner();
        SshMachineLocation m = p.obtain();
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
            m.releasePort(start);
            m.releasePort(start+1);
        }
    }
    
    @Test
    public void obtainLowNumberedPortsAutomatically() throws Exception {
        LocalhostMachineProvisioningLocation p = newLocalhostProvisioner();
        SshMachineLocation m = p.obtain();
        int start = 983;  //random rarely used port, not that it matters
        try {
            int actual = m.obtainPort(PortRanges.fromInteger(start));
            Assert.assertEquals(actual, start);
        } finally {
            m.releasePort(start);
        }

    }

    @Test
    public void obtainPortFailsIfInUse() throws Exception {
        LocalhostMachineProvisioningLocation p = newLocalhostProvisioner();
        SshMachineLocation m = p.obtain();
        int start = 48311;
        PortRange r = PortRanges.fromString(""+start+"-"+(start+1));
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(start);
            int i1 = m.obtainPort(r);
            Assert.assertEquals(i1, start+1);
        } finally {
            if (ss!=null) ss.close();
            m.releasePort(start);
            m.releasePort(start+1);
        }
    }

}

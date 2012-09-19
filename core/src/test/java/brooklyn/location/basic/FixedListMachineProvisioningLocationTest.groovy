package brooklyn.location.basic

import static org.testng.Assert.*

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test

import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException
import brooklyn.test.TestUtils;

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

    @Test
    public void theBuilder() {
        MachineProvisioningLocation<SshMachineLocation> p =
            new FixedListMachineProvisioningLocation.Builder().
                user("u1").
                addAddress("192.168.0.1").
                addAddress("u2@192.168.0.2").
                addAddress("192.168.0.{3,4}").
                addAddresses("192.168.0.{6-8}").
                addAddressMultipleTimes("192.168.0.{8,7}", 2).
                addAddress("u3@192.168.0.{11-20}").
                build();
        assertUserAndHost(p.obtain(), "u1", "192.168.0.1");
        assertUserAndHost(p.obtain(), "u2", "192.168.0.2");
        for (int i=3; i<=4; i++) assertUserAndHost(p.obtain(), "u1", "192.168.0."+i);
        for (int i=6; i<=8; i++) assertUserAndHost(p.obtain(), "u1", "192.168.0."+i);
        for (int j=0; j<2; j++)
            for (int i=8; i>=7; i--) assertUserAndHost(p.obtain(), "u1", "192.168.0."+i);
        for (int i=11; i<=20; i++) assertUserAndHost(p.obtain(), "u3", "192.168.0."+i);
        TestUtils.assertFails { p.obtain() }
    }
    
    private static void assertUserAndHost(SshMachineLocation l, String user, String host) {
        assertEquals(l.getUser(), user);
        assertEquals(l.getAddress().getHostAddress(), host);
    }

    @Test(expectedExceptions = [ IllegalStateException.class ])
    public void throwsExceptionIfTryingToReleaseUnallocationMachine() {
        SshMachineLocation machine = provisioner.obtain()
        provisioner.release(new SshMachineLocation(address:Inet4Address.getByName('192.168.144.201')));
        fail "Did not throw IllegalStateException as expected"
    }
}

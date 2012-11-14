package brooklyn.location.basic

import static org.testng.Assert.*

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException
import brooklyn.test.TestUtils
import brooklyn.util.MutableMap

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.io.Closeables

/**
 * Provisions {@link SshMachineLocation}s in a specific location from a list of known machines
 */
public class FixedListMachineProvisioningLocationTest {
    SshMachineLocation machine;
    MachineProvisioningLocation<SshMachineLocation> provisioner
    MachineProvisioningLocation<SshMachineLocation> provisioner2
    
    @BeforeMethod(alwaysRun=true)
    public void createProvisioner() {
        machine = new SshMachineLocation(address:Inet4Address.getByName('192.168.144.200'));
        provisioner = new FixedListMachineProvisioningLocation<SshMachineLocation>(machines:[ machine ]);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (provisioner != null) Closeables.closeQuietly(provisioner);
        if (provisioner2 != null) Closeables.closeQuietly(provisioner2);
    }
    
    @Test
    public void testSetsChildLocations() {
        // Available machines should be listed as children
		assertEquals(ImmutableList.copyOf(provisioner.getChildLocations()), ImmutableList.of(machine));
        
        // In-use machines should also be listed as children
        provisioner.obtain();
        assertEquals(ImmutableList.copyOf(provisioner.getChildLocations()), ImmutableList.of(machine));
    }

    @Test
    public void canObtainMachine() {
        SshMachineLocation obtained = provisioner.obtain()
        assertEquals(obtained, machine);
    }

    @Test(expectedExceptions = [ NoMachinesAvailableException.class ])
    public void throwsExceptionIfNoMachinesAvailable() {
        SshMachineLocation machine1 = provisioner.obtain()
        SshMachineLocation machine2 = provisioner.obtain()
        fail "Did not throw NoMachinesAvailableException as expected"
    }

    @Test
    public void canGetAMachineReturnItAndObtainItAgain() {
        SshMachineLocation obtained = provisioner.obtain()
        provisioner.release(obtained)
        SshMachineLocation obtained2 = provisioner.obtain()
        assertEquals(obtained2, machine);
    }

    @Test
    public void theBuilder() {
        provisioner2 =
            new FixedListMachineProvisioningLocation.Builder().
                user("u1").
                addAddress("192.168.0.1").
                addAddress("u2@192.168.0.2").
                addAddress("192.168.0.{3,4}").
                addAddresses("192.168.0.{6-8}").
                addAddressMultipleTimes("192.168.0.{8,7}", 2).
                addAddress("u3@192.168.0.{11-20}").
                build();
        assertUserAndHost(provisioner2.obtain(), "u1", "192.168.0.1");
        assertUserAndHost(provisioner2.obtain(), "u2", "192.168.0.2");
        for (int i=3; i<=4; i++) assertUserAndHost(provisioner2.obtain(), "u1", "192.168.0."+i);
        for (int i=6; i<=8; i++) assertUserAndHost(provisioner2.obtain(), "u1", "192.168.0."+i);
        for (int j=0; j<2; j++)
            for (int i=8; i>=7; i--) assertUserAndHost(provisioner2.obtain(), "u1", "192.168.0."+i);
        for (int i=11; i<=20; i++) assertUserAndHost(provisioner2.obtain(), "u3", "192.168.0."+i);
        TestUtils.assertFails { provisioner2.obtain() }
    }
    
    @Test(expectedExceptions = [ IllegalStateException.class ])
    public void throwsExceptionIfTryingToReleaseUnallocationMachine() {
        SshMachineLocation obtained = provisioner.obtain()
        provisioner.release(new SshMachineLocation(address:'192.168.144.201'));
        fail "Did not throw IllegalStateException as expected"
    }
    
    private static void assertUserAndHost(SshMachineLocation l, String user, String host) {
        assertEquals(l.getUser(), user);
        assertEquals(l.getAddress().getHostAddress(), host);
    }
}

package brooklyn.location.jclouds;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.internal.annotations.Sets;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class JcloudsLocationRebindTest {
    protected static final Logger LOG = LoggerFactory.getLogger(JcloudsLocationRebindTest.class);
    
    private static final String PROVIDER = "aws-ec2";
    private static final String EUWEST_REGION_NAME = "eu-west-1";
    private static final String EUWEST_IMAGE_ID = EUWEST_REGION_NAME+"/"+"ami-89def4fd";
    private static final String IMAGE_OWNER = "411009282317";

    private LocalManagementContext managementContext;
    private JcloudsLocation loc;
    private Collection<SshMachineLocation> machines = Sets.newLinkedHashSet();

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext();

        loc = (JcloudsLocation) managementContext.getLocationRegistry().resolve(PROVIDER+":"+EUWEST_REGION_NAME);
        machines = Sets.newLinkedHashSet();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        List<Exception> exceptions = Lists.newArrayList();
        for (SshMachineLocation machine : machines) {
            try {
                if (loc != null) loc.release(machine);
            } catch (Exception e) {
                LOG.warn("Error releasing machine "+machine+"; continuing...", e);
                exceptions.add(e);
            }
        }
        
        if (managementContext != null) managementContext.terminate();
        
        if (exceptions.size() > 0) {
            throw exceptions.get(0);
        }
    }
    
    @Test(groups = { "Live" })
    public void testRebindWithIncorrectId() throws Exception {
        try {
            loc.rebindMachine(ImmutableMap.of("id", "incorrectid", "hostname", "myhostname", "user", "myusername"));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Invalid id")) {
                // success
            } else {
                throw e;
            }
        }
    }
    
    @Test(groups = { "Live" })
    public void testRebindVm() throws Exception {
        // FIXME How to create a machine - go directly through jclouds instead?
        //       Going through LocationRegistry.resolve, loc and loc2 might be same instance
        
        // Create a VM through jclouds
        JcloudsSshMachineLocation machine = obtainMachine(ImmutableMap.of("imageId", EUWEST_IMAGE_ID, "imageOwner", IMAGE_OWNER));
        assertTrue(machine.isSshable());

        String id = machine.getJcloudsId();
        InetAddress address = machine.getAddress();
        String hostname = address.getHostName();
        String user = machine.getUser();
        
        // Create a new jclouds location, and re-bind the existing VM to that
        JcloudsLocation loc2 = (JcloudsLocation) managementContext.getLocationRegistry().resolve(PROVIDER+":"+EUWEST_REGION_NAME);
        SshMachineLocation machine2 = loc2.rebindMachine(ImmutableMap.of("id", id, "hostname", hostname, "user", user));
        
        // Confirm the re-bound machine is wired up
        assertTrue(machine2.isSshable());
        assertEquals(ImmutableSet.copyOf(loc2.getChildren()), ImmutableSet.of(machine2));
        
        // Confirm can release the re-bound machine via the new jclouds location
        loc2.release(machine2);
        assertFalse(machine2.isSshable());
        assertEquals(ImmutableSet.copyOf(loc2.getChildren()), Collections.emptySet());
    }
    
    @Test(groups = { "Live" })
    public void testRebindVmDeprecated() throws Exception {
        // FIXME See comments in testRebindVm

        // Create a VM through jclouds
        JcloudsSshMachineLocation machine = obtainMachine(ImmutableMap.of("imageId", EUWEST_IMAGE_ID, "imageOwner", IMAGE_OWNER));
        assertTrue(machine.isSshable());

        String id = machine.getJcloudsId();
        InetAddress address = machine.getAddress();
        String hostname = address.getHostName();
        String username = machine.getUser();
        
        // Create a new jclouds location, and re-bind the existing VM to that
        JcloudsLocation loc2 = (JcloudsLocation) managementContext.getLocationRegistry().resolve(PROVIDER+":"+EUWEST_REGION_NAME);
        // pass deprecated userName
        SshMachineLocation machine2 = loc2.rebindMachine(ImmutableMap.of("id", id, "hostname", hostname, "userName", username));
        
        // Confirm the re-bound machine is wired up
        assertTrue(machine2.isSshable());
        assertEquals(ImmutableSet.copyOf(loc2.getChildren()), ImmutableSet.of(machine2));
        
        // Confirm can release the re-bound machine via the new jclouds location
        loc2.release(machine2);
        assertFalse(machine2.isSshable());
        assertEquals(ImmutableSet.copyOf(loc2.getChildren()), Collections.emptySet());
    }

    // Useful for debugging; accesss a hard-coded existing instance so don't need to wait for provisioning a new one
    @Test(enabled=false, groups = { "Live" })
    public void testRebindVmToHardcodedInstance() throws Exception {
        String id = "eu-west-1/i-5504f21d";
        InetAddress address = InetAddress.getByName("ec2-176-34-93-58.eu-west-1.compute.amazonaws.com");
        String hostname = address.getHostName();
        String username = "root";
        
        SshMachineLocation machine = loc.rebindMachine(ImmutableMap.of("id", id, "hostname", hostname, "userName", username));
        
        // Confirm the re-bound machine is wired up
        assertTrue(machine.isSshable());
        assertEquals(ImmutableSet.copyOf(loc.getChildren()), ImmutableSet.of(machine));
    }
    
    // Use this utility method to ensure machines are released on tearDown
    protected JcloudsSshMachineLocation obtainMachine(Map<?,?> flags) throws Exception {
        SshMachineLocation result = loc.obtain(flags);
        machines.add(result);
        return (JcloudsSshMachineLocation) result;
    }
    
    protected void release(SshMachineLocation machine) {
        machines.remove(machine);
        loc.release(machine);
    }
}

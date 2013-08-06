package brooklyn.location.jclouds;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.SingleMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.exceptions.CompoundRuntimeException;

import com.google.common.collect.ImmutableMap;

public class SingleMachineProvisioningLocationJcloudsTest {
private static final Logger log = LoggerFactory.getLogger(SingleMachineProvisioningLocation.class);
    
    private LocalManagementContext managementContext;
    private Set<MachineLocation> machinesToTearDown;
    @SuppressWarnings("rawtypes")
    private SingleMachineProvisioningLocation location;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        ConfigKey<String> nameKey = ConfigKeys.newStringConfigKey("brooklyn.location.named.FooServers");
        BrooklynProperties properties = BrooklynProperties.Factory.newDefault();
        properties.put(nameKey, "jclouds:aws-ec2:us-east-1");
        managementContext = new LocalManagementContext(properties);
        machinesToTearDown = new HashSet<MachineLocation>();
    }
    
    @SuppressWarnings("unchecked")
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        Set<Exception> exceptions = Collections.EMPTY_SET;
        for (MachineLocation machineLocation: machinesToTearDown) {
            try {
                location.release(machineLocation);
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        if (managementContext != null) managementContext.terminate();
        if (exceptions.size() > 0) {
            throw new CompoundRuntimeException("Exception during tear down", exceptions);
        }
    }
    
    @SuppressWarnings("unchecked")
    @Test(groups="Live")
    public void testJcloudsSingle() throws Exception {
        location = (SingleMachineProvisioningLocation<MachineLocation>) 
            managementContext.getLocationRegistry().resolve("single:(jclouds:aws-ec2:us-east-1)");
        location.setManagementContext(managementContext);
        
        MachineLocation m1 = location.obtain();
        
        assertNotNull(m1);

        log.info("GOT "+m1);
        
        location.release(m1);
    }
    
    @SuppressWarnings("unchecked")
    @Test(groups="Live")
    public void testJcloudsSingleRelease() throws Exception {
        location = (SingleMachineProvisioningLocation<SshMachineLocation>) 
            managementContext.getLocationRegistry().resolve("single:(jclouds:aws-ec2:us-east-1)");
        location.setManagementContext(managementContext);
        
        SshMachineLocation m1 = (SshMachineLocation) location.obtain();
        machinesToTearDown.add(m1);
        log.info("GOT " + m1);
        SshMachineLocation m2 = (SshMachineLocation) location.obtain();
        log.info("GOT " + m2);
        machinesToTearDown.add(m2);
        assertSame(m1, m2);
        
        location.release(m1);

        assertTrue(m2.isSshable());
        
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        String expectedName = System.getProperty("user.name");
        Map<?, ?> flags = ImmutableMap.of("out", outStream);
        m2.run(flags, "whoami; exit");
        String outString = new String(outStream.toByteArray());

        assertTrue(outString.contains(expectedName), outString);
        
        location.release(m2);
        
        assertFalse(m2.isSshable());
    }
    
    @SuppressWarnings("unchecked")
    @Test(groups="Live")
    public void testJcloudsSingleObtainReleaseObtain() throws Exception {
        location = (SingleMachineProvisioningLocation<SshMachineLocation>) 
            managementContext.getLocationRegistry().resolve("single:(jclouds:aws-ec2:us-east-1)");
        location.setManagementContext(managementContext);
        SshMachineLocation m1 = (SshMachineLocation) location.obtain();
        log.info("GOT " + m1);
        machinesToTearDown.add(m1);
        
        location.release(m1);
        
        SshMachineLocation m2 = (SshMachineLocation) location.obtain();
        machinesToTearDown.add(m2);
        
        assertTrue(m2.isSshable());
        
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        String expectedName = System.getProperty("user.name");
        Map<?, ?> flags = ImmutableMap.of("out", outStream);
        m2.run(flags, "whoami; exit");
        String outString = new String(outStream.toByteArray());

        assertTrue(outString.contains(expectedName), outString);
        
        location.release(m2);
    }
    
    @SuppressWarnings("unchecked")
    @Test(groups="Live")
    public void testJCloudsNamedSingle() throws Exception {
        location = (SingleMachineProvisioningLocation<SshMachineLocation>) 
            managementContext.getLocationRegistry().resolve("single:(named:FooServers)");
        location.setManagementContext(managementContext);
        
        SshMachineLocation m1 = (SshMachineLocation) location.obtain();
        machinesToTearDown.add(m1);
        location.release(m1);
    }
}

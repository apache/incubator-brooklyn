package brooklyn.location.jclouds;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.Map;

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

import com.google.common.collect.ImmutableMap;

public class SingleMachineProvisioningLocationJcloudsTest {
private static final Logger log = LoggerFactory.getLogger(SingleMachineProvisioningLocation.class);
    
    private LocalManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        ConfigKey<String> nameKey = ConfigKeys.newStringConfigKey("brooklyn.location.named.FooServers");
        BrooklynProperties properties = BrooklynProperties.Factory.newDefault();
        properties.put(nameKey, "jclouds:aws-ec2:us-east-1");
        managementContext = new LocalManagementContext(properties);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) managementContext.terminate();
    }
    
    @SuppressWarnings("unchecked")
    @Test(groups="Live")
    public void testJcloudsSingle() throws Exception {
        SingleMachineProvisioningLocation<MachineLocation> l = (SingleMachineProvisioningLocation<MachineLocation>) 
            managementContext.getLocationRegistry().resolve("single:(jclouds:aws-ec2:us-east-1)");
        l.setManagementContext(managementContext);
        
        MachineLocation m1 = l.obtain();

        log.info("GOT "+m1);
        
        l.release(m1);
    }
    
    @SuppressWarnings("unchecked")
    @Test(groups="Live")
    public void testJcloudsSingleRelease() throws Exception {
        SingleMachineProvisioningLocation<SshMachineLocation> l = (SingleMachineProvisioningLocation<SshMachineLocation>) 
            managementContext.getLocationRegistry().resolve("single:(jclouds:aws-ec2:us-east-1)");
        l.setManagementContext(managementContext);
        
        SshMachineLocation m1 = l.obtain();
        log.info("GOT " + m1);
        SshMachineLocation m2 = l.obtain();
        log.info("GOT " + m2);
        assertSame(m1, m2);
        
        l.release(m1);

        assertTrue(m2.isSshable());
        
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        String expectedName = System.getProperty("user.name");
        Map<?, ?> flags = ImmutableMap.of("out", outStream);
        m2.run(flags, "whoami; exit");
        String outString = new String(outStream.toByteArray());

        assertTrue(outString.contains(expectedName), outString);
        
        l.release(m2);
        
        assertFalse(m2.isSshable());
    }
    
    @SuppressWarnings("unchecked")
    @Test(groups="Live")
    public void testJcloudsSingleObtainReleaseObtain() throws Exception {
        SingleMachineProvisioningLocation<SshMachineLocation> l = (SingleMachineProvisioningLocation<SshMachineLocation>) 
            managementContext.getLocationRegistry().resolve("single:(jclouds:aws-ec2:us-east-1)");
        l.setManagementContext(managementContext);
        SshMachineLocation m1 = l.obtain();
        log.info("GOT " + m1);
        
        l.release(m1);
        
        SshMachineLocation m2 = l.obtain();
        
        assertTrue(m2.isSshable());
        
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        String expectedName = System.getProperty("user.name");
        Map<?, ?> flags = ImmutableMap.of("out", outStream);
        m2.run(flags, "whoami; exit");
        String outString = new String(outStream.toByteArray());

        assertTrue(outString.contains(expectedName), outString);
        
        l.release(m2);
    }
    
    @SuppressWarnings("unchecked")
    @Test(groups="Live")
    public void testJCloudsNamedSingle() throws Exception {
        SingleMachineProvisioningLocation<SshMachineLocation> l = (SingleMachineProvisioningLocation<SshMachineLocation>) 
            managementContext.getLocationRegistry().resolve("single:(named:FooServers)");
        l.setManagementContext(managementContext);
        
        SshMachineLocation m1 = l.obtain();
        l.release(m1);
    }
}

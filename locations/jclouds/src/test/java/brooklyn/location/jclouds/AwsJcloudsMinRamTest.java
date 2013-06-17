package brooklyn.location.jclouds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.collections.MutableMap;

public class AwsJcloudsMinRamTest {

    private static final Logger log = LoggerFactory.getLogger(AwsJcloudsMinRamTest.class);
    
    private LocalManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) managementContext.terminate();
    }

    @Test(groups="Live")
    public void testJcloudsCreateWithMinRam() throws Exception {
        JcloudsLocation l = (JcloudsLocation) managementContext.getLocationRegistry().resolve("jclouds:aws-ec2:us-east-1");
        l.configure(MutableMap.of("minRam", "4096"));
        
        JcloudsSshMachineLocation m1 = l.obtain();

        log.info("GOT "+m1);
        
        l.release(m1);
    }

//    @Test(groups="Live")
//    public void testJcloudsCreateNamedJungleBig() throws Exception {
//        @SuppressWarnings("unchecked")
//        MachineProvisioningLocation<SshMachineLocation> l = (MachineProvisioningLocation<SshMachineLocation>) new LocationRegistry().resolve("named:jungle-big");
//        
//        SshMachineLocation m1 = l.obtain(MutableMap.<String,String>of());
//
//        log.info("GOT "+m1);
//        
//        l.release(m1);
//    }

}

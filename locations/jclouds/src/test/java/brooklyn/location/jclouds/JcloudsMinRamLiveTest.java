package brooklyn.location.jclouds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableMap;

public class JcloudsMinRamLiveTest extends AbstractJcloudsTest {

    private static final Logger log = LoggerFactory.getLogger(JcloudsMinRamLiveTest.class);
    
    @Test(groups="Live")
    public void testJcloudsCreateWithMinRam() throws Exception {
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve("jclouds:aws-ec2:us-east-1");
        jcloudsLocation.configure(MutableMap.of("minRam", "4096"));
        
        JcloudsSshMachineLocation m1 = obtainMachine(ImmutableMap.<String, Object>of());

        log.info("GOT "+m1);
        
        jcloudsLocation.release(m1);
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

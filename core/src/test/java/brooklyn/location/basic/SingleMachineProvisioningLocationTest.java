package brooklyn.location.basic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.management.internal.LocalManagementContext;

public class SingleMachineProvisioningLocationTest {
    
    private static final Logger log = LoggerFactory.getLogger(SingleMachineProvisioningLocation.class);
    
    private LocalManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) managementContext.terminate();
    }
    
    @SuppressWarnings("unchecked") 
    @Test
    public void testLocalhostSingle() throws Exception {
        SingleMachineProvisioningLocation<SshMachineLocation> l = (SingleMachineProvisioningLocation<SshMachineLocation>) 
            managementContext.getLocationRegistry().resolve("single:(localhost)");
        l.setManagementContext(managementContext);
        
        SshMachineLocation m1 = l.obtain();

        log.info("GOT "+m1);
        
        l.release(m1);
    }
    
}

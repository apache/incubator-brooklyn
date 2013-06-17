package brooklyn.extras.openshift;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.LocationRegistry;
import brooklyn.management.internal.LocalManagementContext;

public class LocationResolvesTest {

    private LocalManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) managementContext.terminate();
    }
    
    @Test
    public void testOpenshiftLocationResolves() {
        LocationRegistry reg = managementContext.getLocationRegistry();
        Assert.assertNotNull(reg.resolve("openshift"));
    }
}

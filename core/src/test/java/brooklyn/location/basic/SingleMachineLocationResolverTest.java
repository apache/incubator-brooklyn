package brooklyn.location.basic;

import static org.testng.Assert.fail;

import java.util.NoSuchElementException;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.management.internal.LocalManagementContext;


public class SingleMachineLocationResolverTest {
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
    public void testThrowsOnInvalid() throws Exception {
        assertThrowsNoSuchElement("wrongprefix:(hosts=\"1.1.1.1\")");
        assertThrowsIllegalArgument("single");
    }
    
    @Test
    public void testThrowsOnInvalidTarget() throws Exception {
        assertThrowsIllegalArgument("single:()");
        assertThrowsIllegalArgument("single:(wrongprefix:(hosts=\"1.1.1.1\"))");
        assertThrowsIllegalArgument("single:(foo:bar)");
    }

    @Test
    public void resolveHosts() {
        resolve("single:(target=localhost)");
        resolve("single:(target=named:foo)");
        resolve("single:(target=byon:(hosts=\"1.1.1.1\"))");
    }
    
    private void assertThrowsNoSuchElement(String val) {
        try {
            resolve(val);
            fail();
        } catch (NoSuchElementException e) {
            // success
        }
    }
    
    private void assertThrowsIllegalArgument(String val) {
        try {
            resolve(val);
            fail();
        } catch (IllegalArgumentException e) {
            // success
        }
    }
    
    private SingleMachineProvisioningLocation<?> resolve(String val) {
        return (SingleMachineProvisioningLocation<?>) managementContext.getLocationRegistry().resolve(val);
    }
    
}

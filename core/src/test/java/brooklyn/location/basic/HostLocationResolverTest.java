package brooklyn.location.basic;

import static org.testng.Assert.fail;

import java.util.NoSuchElementException;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.MachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;

public class HostLocationResolverTest {
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
        assertThrowsIllegalArgument("host");
    }
    
    @Test
    public void testThrowsOnInvalidTarget() throws Exception {
        assertThrowsIllegalArgument("host:()");
    }
    
    @Test
    public void resolveHosts() {
        resolve("host:(\"1.1.1.1\")");
        resolve("host:(\"localhost\")");
        resolve("host:(\"www.foo.com\")");
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
    
    private MachineProvisioningLocation<?> resolve(String val) {
        return (MachineProvisioningLocation<?>) managementContext.getLocationRegistry().resolve(val);
    }
}

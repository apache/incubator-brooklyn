package brooklyn.location.basic

import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.location.Location
import brooklyn.management.internal.LocalManagementContext

public class LocationResolverTest {

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
    public void testLocalhostLoads() {
        Assert.assertTrue(resolve("localhost") instanceof LocalhostMachineProvisioningLocation);
    }

    @Test(expectedExceptions=[ NoSuchElementException.class, IllegalArgumentException.class ])
    public void testBogusFails() {
        /* the exception is thrown by credentialsfromenv, which is okay;
         * we could query jclouds ahead of time to see if the provider is supported, 
         * and add the following parameter to the @Test annotation:
         * expectedExceptionsMessageRegExp=".*[Nn]o resolver.*")
         */
        resolve("bogus:bogus");
    }

    @Test
    public void testAcceptsList() {
        getLocationResolver().getLocationsById(["localhost"]);
    }

    @Test
    public void testRegistryCommaResolution() {
        List<Location> l;
        l = getLocationResolver().getLocationsById(["byon:(hosts=\"192.168.1.{1,2}\")"]);
        Assert.assertEquals(1, l.size());
        l = getLocationResolver().getLocationsById(["byon:(hosts=192.168.0.1),byon:(hosts=\"192.168.1.{1,2}\"),byon:(hosts=192.168.0.2)"]);
        Assert.assertEquals(3, l.size());
        l = getLocationResolver().getLocationsById(["byon:(hosts=192.168.0.1),byon:(hosts=\"192.168.1.{1,2}\",user=bob),byon:(hosts=192.168.0.2)"]);
        Assert.assertEquals(3, l.size());
    }

    @Test
    public void testAcceptsListOLists() {
        //if inner list has a single item it automatically gets coerced correctly to string
        //preserve for compatibility with older CommandLineLocations (since 0.4.0) [but log warning]
        ((BasicLocationRegistry)managementContext.getLocationRegistry()).getLocationsById([["localhost"]]);
    }

    private BasicLocationRegistry getLocationResolver() {
        return (BasicLocationRegistry) managementContext.getLocationRegistry();
    }
    
    private Location resolve(String id) {
        Location l = managementContext.getLocationRegistry().resolve(id);
        Assert.assertNotNull(l);
        return l;
    }
}

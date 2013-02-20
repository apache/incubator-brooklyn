package brooklyn.location.basic

import org.testng.Assert
import org.testng.annotations.Test

import brooklyn.config.BrooklynProperties
import brooklyn.location.Location

public class LocationResolverTest {

    public static Location resolve(Map properties = [:], String id) {
        Location l = new LocationRegistry(properties).resolve(id);
        Assert.assertNotNull(l);
        return l;
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
        buildLocationRegistry().getLocationsById(["localhost"]);
    }

    private static LocationRegistry buildLocationRegistry() {
        new LocationRegistry(BrooklynProperties.Factory.newDefault())
    }

    @Test
    public void testRegistryCommaResolution() {
        List<Location> l;
        l = buildLocationRegistry().getLocationsById(["byon:(hosts=\"192.168.1.{1,2}\")"]);
        Assert.assertEquals(1, l.size());
        l = buildLocationRegistry().getLocationsById(["byon:(hosts=192.168.0.1),byon:(hosts=\"192.168.1.{1,2}\"),byon:(hosts=192.168.0.2)"]);
        Assert.assertEquals(3, l.size());
        l = buildLocationRegistry().getLocationsById(["byon:(hosts=192.168.0.1),byon:(hosts=\"192.168.1.{1,2}\",user=bob),byon:(hosts=192.168.0.2)"]);
        Assert.assertEquals(3, l.size());
    }

    @Test
    public void testAcceptsListOLists() {
        //if inner list has a single item it automatically gets coerced correctly to string
        //preserve for compatibility with older CommandLineLocations (since 0.4.0) [but log warning]
        buildLocationRegistry().getLocationsById([["localhost"]]);
    }

}

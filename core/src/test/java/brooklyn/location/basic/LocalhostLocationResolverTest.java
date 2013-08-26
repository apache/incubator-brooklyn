package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.collect.ImmutableList;

public class LocalhostLocationResolverTest {

    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newEmpty();
        managementContext = new LocalManagementContext(brooklynProperties);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testTakesLocalhostScopedProperties() {
        brooklynProperties.put("brooklyn.location.localhost.privateKeyFile", "myprivatekeyfile");
        brooklynProperties.put("brooklyn.location.localhost.publicKeyFile", "mypublickeyfile");
        brooklynProperties.put("brooklyn.location.localhost.privateKeyData", "myprivateKeyData");
        brooklynProperties.put("brooklyn.location.localhost.publicKeyData", "myPublicKeyData");
        brooklynProperties.put("brooklyn.location.localhost.privateKeyPassphrase", "myprivateKeyPassphrase");

        Map<String, Object> conf = resolve("localhost").getAllConfig(true);
        
        assertEquals(conf.get("privateKeyFile"), "myprivatekeyfile");
        assertEquals(conf.get("publicKeyFile"), "mypublickeyfile");
        assertEquals(conf.get("privateKeyData"), "myprivateKeyData");
        assertEquals(conf.get("publicKeyData"), "myPublicKeyData");
        assertEquals(conf.get("privateKeyPassphrase"), "myprivateKeyPassphrase");
    }

    @Test
    public void testTakesLocalhostDeprecatedScopedProperties() {
        brooklynProperties.put("brooklyn.localhost.privateKeyFile", "myprivatekeyfile");
        brooklynProperties.put("brooklyn.localhost.publicKeyFile", "mypublickeyfile");
        brooklynProperties.put("brooklyn.localhost.privateKeyData", "myprivateKeyData");
        brooklynProperties.put("brooklyn.localhost.publicKeyData", "myPublicKeyData");
        brooklynProperties.put("brooklyn.localhost.privateKeyPassphrase", "myprivateKeyPassphrase");

        Map<String, Object> conf = resolve("localhost").getAllConfig(true);
        
        assertEquals(conf.get("privateKeyFile"), "myprivatekeyfile");
        assertEquals(conf.get("publicKeyFile"), "mypublickeyfile");
        assertEquals(conf.get("privateKeyData"), "myprivateKeyData");
        assertEquals(conf.get("publicKeyData"), "myPublicKeyData");
        assertEquals(conf.get("privateKeyPassphrase"), "myprivateKeyPassphrase");
    }

    @Test
    public void testTakesDeprecatedProperties() {
        brooklynProperties.put("brooklyn.localhost.private-key-file", "myprivatekeyfile");
        brooklynProperties.put("brooklyn.localhost.public-key-file", "mypublickeyfile");
        brooklynProperties.put("brooklyn.localhost.private-key-data", "myprivateKeyData");
        brooklynProperties.put("brooklyn.localhost.public-key-data", "myPublicKeyData");
        brooklynProperties.put("brooklyn.localhost.private-key-passphrase", "myprivateKeyPassphrase");
        Map<String, Object> conf = resolve("localhost").getAllConfig(true);
        
        assertEquals(conf.get("privateKeyFile"), "myprivatekeyfile");
        assertEquals(conf.get("publicKeyFile"), "mypublickeyfile");
        assertEquals(conf.get("privateKeyData"), "myprivateKeyData");
        assertEquals(conf.get("publicKeyData"), "myPublicKeyData");
        assertEquals(conf.get("privateKeyPassphrase"), "myprivateKeyPassphrase");
    }
    
    @Test
    public void testPropertyScopePrescedence() {
        brooklynProperties.put("brooklyn.location.named.mynamed", "localhost");
        
        // prefer those in "named" over everything else
        brooklynProperties.put("brooklyn.location.named.mynamed.privateKeyFile", "privateKeyFile-inNamed");
        brooklynProperties.put("brooklyn.location.localhost.privateKeyFile", "privateKeyFile-inProviderSpecific");
        brooklynProperties.put("brooklyn.localhost.privateKeyFile", "privateKeyFile-inGeneric");

        // prefer those in provider-specific over generic
        brooklynProperties.put("brooklyn.location.localhost.publicKeyFile", "publicKeyFile-inProviderSpecific");
        brooklynProperties.put("brooklyn.location.publicKeyFile", "publicKeyFile-inGeneric");

        // prefer location-generic if nothing else
        brooklynProperties.put("brooklyn.location.privateKeyData", "privateKeyData-inGeneric");

        Map<String, Object> conf = resolve("named:mynamed").getAllConfig(true);
        
        assertEquals(conf.get("privateKeyFile"), "privateKeyFile-inNamed");
        assertEquals(conf.get("publicKeyFile"), "publicKeyFile-inProviderSpecific");
        assertEquals(conf.get("privateKeyData"), "privateKeyData-inGeneric");
    }

    @Test
    public void testLocalhostLoads() {
        Assert.assertTrue(resolve("localhost") instanceof LocalhostMachineProvisioningLocation);
    }

    @Test
    public void testThrowsOnInvalid() throws Exception {
        assertThrowsNoSuchElement("wrongprefix");
        assertThrowsIllegalArgument("localhost:(name=abc"); // no closing bracket
        assertThrowsIllegalArgument("localhost:(name)"); // no value for name
        assertThrowsIllegalArgument("localhost:(name=)"); // no value for name
    }
    

    @Test
    public void testAcceptsList() {
        List<Location> l = getLocationResolver().resolve(ImmutableList.of("localhost"));
        assertEquals(l.size(), 1, "l="+l);
        assertTrue(l.get(0) instanceof LocalhostMachineProvisioningLocation, "l="+l);
    }

    @Test
    public void testRegistryCommaResolution() throws NoMachinesAvailableException {
        List<Location> l;
        l = getLocationResolver().resolve(ImmutableList.of("localhost,localhost,localhost"));
        assertEquals(l.size(), 3, "l="+l);
        assertTrue(l.get(0) instanceof LocalhostMachineProvisioningLocation, "l="+l);
        assertTrue(l.get(1) instanceof LocalhostMachineProvisioningLocation, "l="+l);
        assertTrue(l.get(2) instanceof LocalhostMachineProvisioningLocation, "l="+l);

        // And check works if comma in brackets
        l = getLocationResolver().resolve(ImmutableList.of("byon:(hosts=\"192.168.0.1\",user=bob),byon:(hosts=\"192.168.0.2\",user=bob2)"));
        assertEquals(l.size(), 2, "l="+l);
        assertTrue(l.get(0) instanceof FixedListMachineProvisioningLocation, "l="+l);
        assertTrue(l.get(1) instanceof FixedListMachineProvisioningLocation, "l="+l);
        assertEquals(((FixedListMachineProvisioningLocation<SshMachineLocation>)l.get(0)).obtain().getUser(), "bob");
        assertEquals(((FixedListMachineProvisioningLocation<SshMachineLocation>)l.get(1)).obtain().getUser(), "bob2");
    }

    @Test
    public void testAcceptsListOLists() {
        //if inner list has a single item it automatically gets coerced correctly to string
        //preserve for compatibility with older CommandLineLocations (since 0.4.0) [but log warning]
        ((BasicLocationRegistry)managementContext.getLocationRegistry()).resolve(ImmutableList.of(ImmutableList.of("localhost")));
    }

    @Test
    public void testResolvesName() throws Exception {
        Location location = resolve("localhost");
        assertTrue(location instanceof LocalhostMachineProvisioningLocation);
        assertEquals(location.getDisplayName(), "localhost");

        Location location2 = resolve("localhost:()");
        assertTrue(location2 instanceof LocalhostMachineProvisioningLocation);
        assertEquals(location2.getDisplayName(), "localhost");

        Location location3 = resolve("localhost:(name=myname)");
        assertTrue(location3 instanceof LocalhostMachineProvisioningLocation);
        assertEquals(location3.getDisplayName(), "myname");
    }
    
    private BasicLocationRegistry getLocationResolver() {
        return (BasicLocationRegistry) managementContext.getLocationRegistry();
    }
    
    private Location resolve(String val) {
        Location l = managementContext.getLocationRegistry().resolve(val);
        Assert.assertNotNull(l);
        return l;
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
}

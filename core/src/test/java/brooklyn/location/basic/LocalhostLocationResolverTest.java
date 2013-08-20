package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;

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
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

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

    @Test(expectedExceptions={ NoSuchElementException.class, IllegalArgumentException.class })
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
        getLocationResolver().getLocationsById(ImmutableList.of("localhost"));
    }

    @Test
    public void testRegistryCommaResolution() {
        List<Location> l;
        l = getLocationResolver().getLocationsById(ImmutableList.of("byon:(hosts=\"192.168.1.{1,2}\")"));
        Assert.assertEquals(1, l.size());
        l = getLocationResolver().getLocationsById(ImmutableList.of("byon:(hosts=192.168.0.1),byon:(hosts=\"192.168.1.{1,2}\"),byon:(hosts=192.168.0.2)"));
        Assert.assertEquals(3, l.size());
        l = getLocationResolver().getLocationsById(ImmutableList.of("byon:(hosts=192.168.0.1),byon:(hosts=\"192.168.1.{1,2}\",user=bob),byon:(hosts=192.168.0.2)"));
        Assert.assertEquals(3, l.size());
    }

    @Test
    public void testAcceptsListOLists() {
        //if inner list has a single item it automatically gets coerced correctly to string
        //preserve for compatibility with older CommandLineLocations (since 0.4.0) [but log warning]
        ((BasicLocationRegistry)managementContext.getLocationRegistry()).getLocationsById(ImmutableList.of(ImmutableList.of("localhost")));
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

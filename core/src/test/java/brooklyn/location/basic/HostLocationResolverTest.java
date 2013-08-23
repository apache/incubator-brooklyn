package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.net.InetAddress;
import java.util.Map;
import java.util.NoSuchElementException;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.collect.ImmutableMap;

public class HostLocationResolverTest {
    
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
    
    @Test
    public void testNamedByonLocation() throws Exception {
        brooklynProperties.put("brooklyn.location.named.mynamed", "host:(\"1.1.1.1\")");
        
        MachineProvisioningLocation<SshMachineLocation> loc = resolve("named:mynamed");
        assertEquals(loc.obtain(ImmutableMap.of()).getAddress(), InetAddress.getByName("1.1.1.1"));
    }

    @Test
    public void testPropertyScopePrescedence() throws Exception {
        brooklynProperties.put("brooklyn.location.named.mynamed", "host:(\"1.1.1.1\")");
        
        // prefer those in "named" over everything else
        brooklynProperties.put("brooklyn.location.named.mynamed.privateKeyFile", "privateKeyFile-inNamed");
        brooklynProperties.put("brooklyn.localhost.privateKeyFile", "privateKeyFile-inGeneric");

        // prefer location-generic if nothing else
        brooklynProperties.put("brooklyn.location.privateKeyData", "privateKeyData-inGeneric");

        Map<String, Object> conf = resolve("named:mynamed").obtain(ImmutableMap.of()).getAllConfig(true);
        
        assertEquals(conf.get("privateKeyFile"), "privateKeyFile-inNamed");
        assertEquals(conf.get("privateKeyData"), "privateKeyData-inGeneric");
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
    
    @SuppressWarnings("unchecked")
    private MachineProvisioningLocation<SshMachineLocation> resolve(String val) {
        return (MachineProvisioningLocation<SshMachineLocation>) managementContext.getLocationRegistry().resolve(val);
    }
}

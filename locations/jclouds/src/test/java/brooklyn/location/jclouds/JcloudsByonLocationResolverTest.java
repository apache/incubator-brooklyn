package brooklyn.location.jclouds;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.net.InetAddress;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.location.LocationRegistry;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.collect.Iterables;

public class JcloudsByonLocationResolverTest {

    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;
    private LocationRegistry registry;

    // TODO Expects this VM to exist; how to write this better? 
    // Should we just create a VM at the start of the test?
    private final String user = "aled";
    private final String instanceId = "i-f2014593";
    private final String ip = "54.226.119.72";
    private final String hostname = "ec2-54-226-119-72.compute-1.amazonaws.com";

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        managementContext = new LocalManagementContext(brooklynProperties);
        registry = new BasicLocationRegistry(managementContext);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) managementContext.terminate();
    }

    @Test
    public void testThrowsOnInvalid() throws Exception {
        assertThrowsNoSuchElement("wrongprefix:(hosts=\"1.1.1.1\")");
        assertThrowsIllegalArgument("jcloudsByon"); // no hosts
        assertThrowsIllegalArgument("jcloudsByon:()"); // no hosts
        assertThrowsIllegalArgument("jcloudsByon:(hosts=\"\")"); // empty hosts
        assertThrowsIllegalArgument("jcloudsByon:(hosts=\"1.1.1.1\""); // no closing bracket
        assertThrowsIllegalArgument("jcloudsByon:(hosts=\"1.1.1.1\", name)"); // no value for name
        assertThrowsIllegalArgument("jcloudsByon:(hosts=\"1.1.1.1\", name=)"); // no value for name
    }
    
    // TODO Requires that a VM already exists; could create that VM first to make test more robust
    @Test(groups={"Live","WIP"})
    public void testResolvesJcloudsByon() throws Exception {
        String spec = "jcloudsByon:(provider=\"aws-ec2\",region=\"us=east-1\",user=\""+user+"\",hosts=\""+instanceId+"\")";
        assertResolvesJclouds(spec);
    }

    // TODO Requires that a VM already exists; could create that VM first to make test more robust
    @Test(groups={"Live","WIP"})
    public void testResolvesNamedJcloudsByon() throws Exception {
        String spec = "jcloudsByon:(provider=\"aws-ec2\",region=\"us=east-1\",user=\""+user+"\",hosts=\""+instanceId+"\")";
        brooklynProperties.put("brooklyn.location.named.mynamed", spec);
        
        FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> loc = resolve("named:mynamed");
        assertEquals(loc.obtain().getAddress(), InetAddress.getByName("1.1.1.1"));
    }

    // TODO Requires that a VM already exists; could create that VM first to make test more robust
    @Test(groups={"Live","WIP"})
    public void testJcloudsPropertiesPrecedence() throws Exception {
        String spec = "jcloudsByon:(provider=\"aws-ec2\",region=\"us=east-1\",user=\""+user+"\",hosts=\""+instanceId+"\")";
        brooklynProperties.put("brooklyn.location.named.mynamed", spec);
        
        // prefer those in "named" over everything else
        brooklynProperties.put("brooklyn.location.named.mynamed.privateKeyFile", "privateKeyFile-inNamed");
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.privateKeyFile", "privateKeyFile-inProviderSpecific");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.privateKeyFile", "privateKeyFile-inProviderSpecificDeprecated");
        brooklynProperties.put("brooklyn.location.jclouds.privateKeyFile", "privateKeyFile-inJcloudsGeneric");
        brooklynProperties.put("brooklyn.jclouds.privateKeyFile", "privateKeyFile-inJcloudsGenericDeprecated");
        brooklynProperties.put("brooklyn.location.privateKeyFile", "privateKeyFile-inLocationGeneric");

        // prefer those in provider-specific over generic
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.publicKeyFile", "publicKeyFile-inProviderSpecific");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.publicKeyFile", "publicKeyFile-inProviderSpecificDeprecated");
        brooklynProperties.put("brooklyn.location.jclouds.publicKeyFile", "publicKeyFile-inJcloudsGeneric");
        brooklynProperties.put("brooklyn.jclouds.publicKeyFile", "publicKeyFile-inJcloudsGenericDeprecated");
        brooklynProperties.put("brooklyn.location.publicKeyFile", "publicKeyFile-inLocationGeneric");
        
        // prefer those in provider-specific (deprecated scope) over generic
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.securityGroups", "securityGroups-inProviderSpecificDeprecated");
        brooklynProperties.put("brooklyn.location.jclouds.securityGroups", "securityGroups-inJcloudsGeneric");
        brooklynProperties.put("brooklyn.jclouds.securityGroups", "securityGroups-inJcloudsGenericDeprecated");
        brooklynProperties.put("brooklyn.location.securityGroups", "securityGroups-inLocationGeneric");

        // prefer those in jclouds-generic over location-generic
        brooklynProperties.put("brooklyn.location.jclouds.loginUser", "loginUser-inJcloudsGeneric");
        brooklynProperties.put("brooklyn.jclouds.loginUser", "loginUser-inJcloudsGenericDeprecated");
        brooklynProperties.put("brooklyn.location.loginUser", "loginUser-inLocationGeneric");

        // prefer those in jclouds-generic (deprecated) over location-generic
        brooklynProperties.put("brooklyn.jclouds.user", "user-inJcloudsGenericDeprecated");
        brooklynProperties.put("brooklyn.location.user", "user-inLocationGeneric");

        // prefer location-generic if nothing else
        brooklynProperties.put("brooklyn.location.keyPair", "keyPair-inLocationGeneric");

        // prefer deprecated properties in "named" over those less specific
        brooklynProperties.put("brooklyn.location.named.mynamed.private-key-data", "privateKeyData-inNamed");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.privateKeyData", "privateKeyData-inProviderSpecific");
        brooklynProperties.put("brooklyn.jclouds.privateKeyData", "privateKeyData-inJcloudsGeneric");

        // prefer "named" over everything else: confirm deprecated don't get transformed to overwrite it accidentally
        brooklynProperties.put("brooklyn.location.named.mynamed.privateKeyPassphrase", "privateKeyPassphrase-inNamed");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.private-key-passphrase", "privateKeyPassphrase-inProviderSpecific");
        brooklynProperties.put("brooklyn.jclouds.private-key-passphrase", "privateKeyPassphrase-inJcloudsGeneric");

        Map<String, Object> conf = resolve("named:mynamed").obtain().getAllConfig(true);
        
        assertEquals(conf.get("privateKeyFile"), "privateKeyFile-inNamed");
        assertEquals(conf.get("publicKeyFile"), "publicKeyFile-inProviderSpecific");
        assertEquals(conf.get("securityGroups"), "securityGroups-inProviderSpecificDeprecated");
        assertEquals(conf.get("loginUser"), "loginUser-inJcloudsGeneric");
        assertEquals(conf.get("user"), "user-inJcloudsGenericDeprecated");
        assertEquals(conf.get("keyPair"), "keyPair-inLocationGeneric");
        assertEquals(conf.get("privateKeyData"), "privateKeyData-inNamed");
        assertEquals(conf.get("privateKeyPassphrase"), "privateKeyPassphrase-inNamed");
    }

    // TODO Requires that a VM already exists; could create that VM first to make test more robust
    private void assertResolvesJclouds(String spec) throws Exception {
        FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> loc = resolve(spec);
        
        Set<JcloudsSshMachineLocation> machines = loc.getAllMachines();
        JcloudsSshMachineLocation machine = Iterables.getOnlyElement(machines);
        assertEquals(machine.getParent().getProvider(), "aws-ec2");
        assertEquals(machine.getAddress().getHostAddress(), ip);
        assertEquals(machine.getAddress().getHostName(), hostname);
        assertEquals(machine.getUser(), user);
    }

    @SuppressWarnings("unchecked")
    private FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> resolve(String spec) {
        return (FixedListMachineProvisioningLocation<JcloudsSshMachineLocation>) registry.resolve(spec);
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

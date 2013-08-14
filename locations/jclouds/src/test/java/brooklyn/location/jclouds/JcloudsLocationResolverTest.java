package brooklyn.location.jclouds;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.management.internal.LocalManagementContext;

public class JcloudsLocationResolverTest {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(JcloudsLocationResolverTest.class);

    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newEmpty();

        brooklynProperties.put("brooklyn.jclouds.aws-ec2.identity", "aws-ec2-id");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.credential", "aws-ec2-cred");
        brooklynProperties.put("brooklyn.jclouds.rackspace-cloudservers-uk.identity", "cloudservers-uk-id");
        brooklynProperties.put("brooklyn.jclouds.rackspace-cloudservers-uk.credential", "cloudservers-uk-cred");

        managementContext = new LocalManagementContext(brooklynProperties);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (managementContext != null)
            managementContext.terminate();
    }

    @Test
    public void testJcloudsTakesProviderScopedProperties() {
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.privateKeyFile", "myprivatekeyfile");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.publicKeyFile", "mypublickeyfile");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.privateKeyData", "myprivateKeyData");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.publicKeyData", "myPublicKeyData");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.privateKeyPassphrase", "myprivateKeyPassphrase");
        Map<String, Object> conf = resolve("jclouds:aws-ec2").getAllConfig(true);

        assertEquals(conf.get("privateKeyFile"), "myprivatekeyfile");
        assertEquals(conf.get("publicKeyFile"), "mypublickeyfile");
        assertEquals(conf.get("privateKeyData"), "myprivateKeyData");
        assertEquals(conf.get("publicKeyData"), "myPublicKeyData");
        assertEquals(conf.get("privateKeyPassphrase"), "myprivateKeyPassphrase");
    }

    @Test
    public void testJcloudsTakesGenericScopedProperties() {
        brooklynProperties.put("brooklyn.jclouds.privateKeyFile", "myprivatekeyfile");
        brooklynProperties.put("brooklyn.jclouds.publicKeyFile", "mypublickeyfile");
        brooklynProperties.put("brooklyn.jclouds.privateKeyData", "myprivateKeyData");
        brooklynProperties.put("brooklyn.jclouds.publicKeyData", "myPublicKeyData");
        brooklynProperties.put("brooklyn.jclouds.privateKeyPassphrase", "myprivateKeyPassphrase");
        Map<String, Object> conf = resolve("jclouds:aws-ec2").getAllConfig(true);

        assertEquals(conf.get("privateKeyFile"), "myprivatekeyfile");
        assertEquals(conf.get("publicKeyFile"), "mypublickeyfile");
        assertEquals(conf.get("privateKeyData"), "myprivateKeyData");
        assertEquals(conf.get("publicKeyData"), "myPublicKeyData");
        assertEquals(conf.get("privateKeyPassphrase"), "myprivateKeyPassphrase");
    }

    @Test
    public void testJcloudsTakesDeprecatedProperties() {
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.private-key-file", "myprivatekeyfile");
        brooklynProperties.put("brooklyn.jclouds.public-key-file", "mypublickeyfile");
        brooklynProperties.put("brooklyn.jclouds.private-key-data", "myprivateKeyData");
        brooklynProperties.put("brooklyn.jclouds.public-key-data", "myPublicKeyData");
        brooklynProperties.put("brooklyn.jclouds.private-key-passphrase", "myprivateKeyPassphrase");
        brooklynProperties.put("brooklyn.jclouds.image-id", "myimageid");
        Map<String, Object> conf = resolve("jclouds:aws-ec2").getAllConfig(true);

        assertEquals(conf.get("privateKeyFile"), "myprivatekeyfile");
        assertEquals(conf.get("publicKeyFile"), "mypublickeyfile");
        assertEquals(conf.get("privateKeyData"), "myprivateKeyData");
        assertEquals(conf.get("publicKeyData"), "myPublicKeyData");
        assertEquals(conf.get("privateKeyPassphrase"), "myprivateKeyPassphrase");
        assertEquals(conf.get("imageId"), "myimageid");
    }

    @Test
    public void testJcloudsPropertiesPrecedence() {
        brooklynProperties.put("brooklyn.location.named.myaws-ec2", "jclouds:aws-ec2");

        // prefer those in "named" over everything else
        brooklynProperties.put("brooklyn.location.named.myaws-ec2.privateKeyFile", "privateKeyFile-inNamed");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.privateKeyFile", "privateKeyFile-inProviderSpecific");
        brooklynProperties.put("brooklyn.jclouds.privateKeyFile", "privateKeyFile-inJcloudsGeneric");

        // prefer those in provider-specific over generic
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.publicKeyFile", "publicKeyFile-inProviderSpecific");
        brooklynProperties.put("brooklyn.jclouds.publicKeyFile", "publicKeyFile-inJcloudsGeneric");

        // prefer deprecated properties in "named" over those less specific
        brooklynProperties.put("brooklyn.location.named.myaws-ec2.private-key-data", "privateKeyData-inNamed");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.privateKeyData", "privateKeyData-inProviderSpecific");
        brooklynProperties.put("brooklyn.jclouds.privateKeyData", "privateKeyData-inJcloudsGeneric");

        // prefer generic if nothing else
        brooklynProperties.put("brooklyn.jclouds.publicKeyData", "publicKeyData-inJcloudsGeneric");

        // prefer "named" over everything else: confirm deprecated don't get
        // transformed to overwrite it accidentally
        brooklynProperties
                .put("brooklyn.location.named.myaws-ec2.privateKeyPassphrase", "privateKeyPassphrase-inNamed");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.private-key-passphrase",
                "privateKeyPassphrase-inProviderSpecific");
        brooklynProperties.put("brooklyn.jclouds.private-key-passphrase", "privateKeyPassphrase-inJcloudsGeneric");

        Map<String, Object> conf = resolve("named:myaws-ec2").getAllConfig(true);

        assertEquals(conf.get("privateKeyFile"), "privateKeyFile-inNamed");
        assertEquals(conf.get("publicKeyFile"), "publicKeyFile-inProviderSpecific");
        assertEquals(conf.get("privateKeyData"), "privateKeyData-inNamed");
        assertEquals(conf.get("publicKeyData"), "publicKeyData-inJcloudsGeneric");
        assertEquals(conf.get("privateKeyPassphrase"), "privateKeyPassphrase-inNamed");
    }

    @Test
    public void testJcloudsLoads() {
        Assert.assertTrue(resolve("jclouds:aws-ec2") instanceof JcloudsLocation);
    }

    @Test
    public void testJcloudsImplicitLoads() {
        Assert.assertTrue(resolve("aws-ec2") instanceof JcloudsLocation);
    }

    @Test
    public void testJcloudsLocationLoads() {
        Assert.assertTrue(resolve("aws-ec2:eu-west-1") instanceof JcloudsLocation);
    }

    @Test
    public void testJcloudsRegionOnlyLoads() {
        Assert.assertTrue(resolve("eu-west-1") instanceof JcloudsLocation);
    }

    @Test
    public void testJcloudsEndpointLoads() {
        JcloudsLocation loc = resolve("jclouds:openstack-nova:http://foo/api");
        assertEquals(loc.getProvider(), "openstack-nova");
        assertEquals(loc.getEndpoint(), "http://foo/api");
    }

    @Test(groups = "WIP")
    public void testJcloudsEndpointLoadFails() {
        brooklynProperties.put("brooklyn.jclouds.openstack-nova.endpoint", "myendpoint");
        JcloudsLocation loc = resolve("jclouds:openstack-nova");
        assertEquals(loc.getEndpoint(), "myendpoint");
    }

    @Test
    public void testThrowsOnInvalid() throws Exception {
        // Tries to treat "wrongprefix" as a cloud provider
        assertThrows("wrongprefix:aws-ec2:us-east-1", NoSuchElementException.class);

        // no provider
        assertThrows("jclouds", IllegalArgumentException.class);

        // empty provider
        assertThrows("jclouds:", IllegalArgumentException.class);

        // invalid provider
        assertThrows("jclouds:doesnotexist", NoSuchElementException.class);
    }

    @Test
    public void testResolvesJclouds() throws Exception {
        // test with provider + region
        assertJcloudsEquals(resolve("jclouds:aws-ec2:us-east-1"), "aws-ec2", "us-east-1");

        // test with provider that has no region
        assertJcloudsEquals(resolve("jclouds:rackspace-cloudservers-uk"), "rackspace-cloudservers-uk", null);
    }

    // TODO Visual inspection test that it logs warnings
    @Test
    public void testLogsWarnings() throws Exception {
        assertJcloudsEquals(resolve("jclouds:jclouds:aws-ec2:us-east-1"), "aws-ec2", "us-east-1");
        assertJcloudsEquals(resolve("us-east-1"), "aws-ec2", "us-east-1");

        // TODO Should we enforce a jclouds prefix? Currently we don't
        // assertJcloudsEquals(resolve("aws-ec2:us-east-1"), "aws-ec2",
        // "us-east-1");

    }

    private void assertJcloudsEquals(JcloudsLocation loc, String expectedProvider, String expectedRegion) {
        assertEquals(loc.getProvider(), expectedProvider);
        assertEquals(loc.getRegion(), expectedRegion);
    }

    private void assertThrows(String val, Class<?> expectedExceptionType) throws Exception {
        try {
            resolve(val);
            fail();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e))
                throw e; // otherwise success

        }
    }

    @Test(expectedExceptions = { NoSuchElementException.class, IllegalArgumentException.class }, expectedExceptionsMessageRegExp = ".*insufficient.*")
    public void testJcloudsOnlyFails() {
        resolve("jclouds");
    }

    private JcloudsLocation resolve(String spec) {
        return (JcloudsLocation) managementContext.getLocationRegistry().resolve(spec);
    }
}

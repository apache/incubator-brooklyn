package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.location.LocationRegistry;
import brooklyn.location.basic.jclouds.JcloudsLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.collect.ImmutableMap;

public class JcloudsLocationResolverTest {

    private static final Logger log = LoggerFactory.getLogger(JcloudsLocationResolverTest.class);
    
    private BrooklynProperties brooklynProperties;
    private ManagementContext managementContext;
    private LocationRegistry registry;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newEmpty();
        managementContext = new LocalManagementContext(brooklynProperties);
        registry = new BasicLocationRegistry(managementContext);
        
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.identity", "aws-ec2-id");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.credential", "aws-ec2-cred");
        brooklynProperties.put("brooklyn.jclouds.cloudservers-uk.identity", "cloudservers-uk-id");
        brooklynProperties.put("brooklyn.jclouds.cloudservers-uk.credential", "cloudservers-uk-cred");
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
        // TODO This is being renamed to "rackspace-cloudservers-uk", so will break in a future version of jclouds!
        assertJcloudsEquals(resolve("jclouds:cloudservers-uk"), "cloudservers-uk", null);
    }

    // TODO Visual inspection test that it logs warnings
    @Test
    public void testLogsWarnings() throws Exception {
        assertJcloudsEquals(resolve("jclouds:jclouds:aws-ec2:us-east-1"), "aws-ec2", "us-east-1");
        assertJcloudsEquals(resolve("us-east-1"), "aws-ec2", "us-east-1");
        
        // TODO Should we enforce a jclouds prefix? Currently we don't
        // assertJcloudsEquals(resolve("aws-ec2:us-east-1"), "aws-ec2", "us-east-1");

    }

    private void assertJcloudsEquals(JcloudsLocation loc, String expectedProvider, String expectedRegion) {
        assertEquals(loc.getProvider(), expectedProvider);
        assertEquals(loc.getJcloudsProviderLocationId(), expectedRegion);
    }

    private void assertThrows(String val, Class<?> expectedExceptionType) throws Exception {
        try {
            resolve(val);
            fail();
        } catch (Exception e) {
            if (!expectedExceptionType.isInstance(e)) throw e; // otherwise success
            
        }
    }
    
    private JcloudsLocation resolve(String spec) {
        return new JcloudsResolver().newLocationFromString(ImmutableMap.of(), spec, registry);
    }
}

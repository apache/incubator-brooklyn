package brooklyn.location.jclouds;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

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

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newEmpty();
        
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.identity", "aws-ec2-id");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.credential", "aws-ec2-cred");
        brooklynProperties.put("brooklyn.jclouds.rackspace-cloudservers-uk.identity", "cloudservers-uk-id");
        brooklynProperties.put("brooklyn.jclouds.rackspace-cloudservers-uk.credential", "cloudservers-uk-cred");
        
        managementContext = new LocalManagementContext(brooklynProperties);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) managementContext.terminate();
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
        // assertJcloudsEquals(resolve("aws-ec2:us-east-1"), "aws-ec2", "us-east-1");

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
            if (!expectedExceptionType.isInstance(e)) throw e; // otherwise success
            
        }
    }
    
    @Test(expectedExceptions={ NoSuchElementException.class, IllegalArgumentException.class },
            expectedExceptionsMessageRegExp=".*insufficient.*")
    public void testJcloudsOnlyFails() {
        resolve("jclouds");
    }
    
    private JcloudsLocation resolve(String spec) {
        return (JcloudsLocation) managementContext.getLocationRegistry().resolve(spec);
    }
}

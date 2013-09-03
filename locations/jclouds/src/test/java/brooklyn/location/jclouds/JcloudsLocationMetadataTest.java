package brooklyn.location.jclouds;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.location.Location;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.collect.ImmutableSet;

/**
 * @author Shane Witbeck
 */
public class JcloudsLocationMetadataTest implements JcloudsLocationConfig {

    private LocalManagementContext managementContext;
    private BrooklynProperties brooklynProperties;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        managementContext = new LocalManagementContext(brooklynProperties);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) managementContext.terminate();
    }
    
    @Test
    public void testGetsDefaultAwsEc2Metadata() throws Exception {
        Location loc = managementContext.getLocationRegistry().resolve("jclouds:aws-ec2:us-west-1");
        
        assertEquals(loc.getConfig(LocationConfigKeys.LATITUDE), 40.0d);
        assertEquals(loc.getConfig(LocationConfigKeys.LONGITUDE), -120.0d);
        assertEquals(loc.getConfig(LocationConfigKeys.ISO_3166), ImmutableSet.of("US-CA"));
    }

    @Test
    public void testCanOverrideDefaultAwsEc2Metadata() throws Exception {
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2@us-west-1.latitude", "41.2");
        Location loc = managementContext.getLocationRegistry().resolve("jclouds:aws-ec2:us-west-1");
        
        assertEquals(loc.getConfig(LocationConfigKeys.LATITUDE), 41.2d);
    }
}

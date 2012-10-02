package brooklyn.location.basic.jclouds

import brooklyn.config.BrooklynProperties

import org.testng.annotations.DataProvider
import org.testng.annotations.Test;

//FIXME get Greenhouse working

@Test(enabled=false /* work in progress */)
class GreenhouseDataLocationLiveTest extends AbstractJcloudsLocationTest {
    
    private static final String PROVIDER = "greenhousedata-element-vcloud"
    private static final String REGION_NAME = "whatshoulditbe" 
    private static final String IMAGE_ID = REGION_NAME+"/"+"whatshoulditbe"
    private static final String IMAGE_OWNER = null
    private static final String IMAGE_PATTERN = ".*Ubuntu_Server_x64.*"

    public GreenhouseDataLocationLiveTest() {
        super(PROVIDER)
    }
    
    protected CredentialsFromEnv getCredentials() {
        Map<String,? extends Object> properties = [identity:"thejuggler", credential:"alistair"]
        properties.putAll(BrooklynProperties.Factory.newDefault())
        return CredentialsFromEnv.newInstance(properties, PROVIDER);
    }
    
    @Override
    @DataProvider(name = "fromImageId")
    public Object[][] cloudAndImageIds() {
        return [ [REGION_NAME, IMAGE_ID, IMAGE_OWNER], [REGION_NAME, IMAGE_ID, IMAGE_OWNER] ]
    }

    @Override
    @DataProvider(name = "fromImageDescriptionPattern")
    public Object[][] cloudAndImageDescriptionPatterns() {
        return [ [REGION_NAME, IMAGE_PATTERN, IMAGE_OWNER], [REGION_NAME, IMAGE_PATTERN, IMAGE_OWNER] ]
    }
    
    @Override
    @DataProvider(name = "fromImageNamePattern")
    public Object[][] cloudAndImageNamePatterns() {
        return []
    }
    
    @Test
    public void noop() { /* just exists to let testNG IDE run the test */ }
    
}

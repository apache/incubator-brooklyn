package brooklyn.location.jclouds

import static org.testng.Assert.*

import org.testng.annotations.DataProvider
import org.testng.annotations.Test

public class RackspaceLocationLiveTest extends AbstractJcloudsLocationTest {
    
    private static final String PROVIDER = "rackspace-cloudservers-uk"
    private static final String REGION_NAME = null;
    private static final String IMAGE_ID = "115"
    private static final String IMAGE_NAME_PATTERN = "Ubuntu 11.04"
    private static final String IMAGE_OWNER = null
    
    public RackspaceLocationLiveTest() {
        super(PROVIDER)
    }
    
    @Override
    @DataProvider(name = "fromImageId")
    public Object[][] cloudAndImageIds() {
        return [ [ REGION_NAME, IMAGE_ID, IMAGE_OWNER ] ]
    }

    @Override
    @DataProvider(name = "fromImageNamePattern")
    public Object[][] cloudAndImageNamePatterns() {
        return [ [ REGION_NAME, IMAGE_NAME_PATTERN, IMAGE_OWNER ] ]
    }
    
    @Override
    @DataProvider(name = "fromImageDescriptionPattern")
    public Object[][] cloudAndImageDescriptionPatterns() {
        return []
    }
    
    @Test(enabled = false)
    public void noop() { /* just exists to let testNG IDE run the test */ }
    
}

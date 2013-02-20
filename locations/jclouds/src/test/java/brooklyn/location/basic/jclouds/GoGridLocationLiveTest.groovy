package brooklyn.location.basic.jclouds

import static org.testng.Assert.*

import org.testng.annotations.DataProvider
import org.testng.annotations.Test

public class GoGridLocationLiveTest extends AbstractJcloudsLocationTest {
    
    private static final String PROVIDER = "gogrid"
    private static final String USWEST_REGION_NAME = "1"//"us-west-1"
    private static final String USWEST_IMAGE_ID = "1532"
    private static final String IMAGE_NAME_PATTERN = "CentOS 5.3 (64-bit) w/ None"
    private static final String IMAGE_OWNER = null
    
    public GoGridLocationLiveTest() {
        super(PROVIDER)
    }
    
    @Override
    @DataProvider(name = "fromImageId")
    public Object[][] cloudAndImageIds() {
        return [ [USWEST_REGION_NAME, USWEST_IMAGE_ID, IMAGE_OWNER] ]
    }

    @Override
    @DataProvider(name = "fromImageNamePattern")
    public Object[][] cloudAndImageNamePatterns() {
        return [ [USWEST_REGION_NAME, IMAGE_NAME_PATTERN, IMAGE_OWNER] ]
    }
    
    @Override
    @DataProvider(name = "fromImageDescriptionPattern")
    public Object[][] cloudAndImageDescriptionPatterns() {
        return []
    }
}

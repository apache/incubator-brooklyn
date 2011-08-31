package brooklyn.location.basic.jclouds

import static org.testng.Assert.*

import org.testng.annotations.DataProvider
import org.testng.annotations.Test

class AwsEc2LocationTest extends AbstractJcloudsLocationTest {
    
    private static final String PROVIDER = "aws-ec2"
    private static final String EUWEST_REGION_NAME = "eu-west-1" 
    private static final String USEAST_REGION_NAME = "us-east-1" 
    private static final String EUWEST_IMAGE_ID = EUWEST_REGION_NAME+"/"+"ami-89def4fd"
    private static final String USEAST_IMAGE_ID = USEAST_REGION_NAME+"/"+"ami-2342a94a"
    private static final String IMAGE_OWNER = "411009282317"
    private static final String IMAGE_PATTERN = ".*RightImage_CentOS_5.4_i386_v5.5.9_EBS.*"

    public AwsEc2LocationTest() {
        super(PROVIDER)
    }
    
    @Override
    @DataProvider(name = "fromImageId")
    public Object[][] cloudAndImageIds() {
        return [ [EUWEST_REGION_NAME, EUWEST_IMAGE_ID, IMAGE_OWNER], [USEAST_REGION_NAME, USEAST_IMAGE_ID, IMAGE_OWNER] ]
    }

    @Override
    @DataProvider(name = "fromImagePattern")
    public Object[][] cloudAndImagePatterns() {
        return [ [USEAST_REGION_NAME, IMAGE_PATTERN, IMAGE_OWNER], [USEAST_REGION_NAME, IMAGE_PATTERN, IMAGE_OWNER] ]
    }
    
    @Override
    @DataProvider(name = "fromImageNamePattern")
    public Object[][] cloudAndImageNamePatterns() {
        return []
    }
    
    // seems to be required in eclipse so that on the class I can runAs->testng
    @Test
    public void testDummy() {
    }
}

package brooklyn.location.jclouds.provider;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class RackspaceLocationLiveTest extends AbstractJcloudsLocationTest {

    private static final String PROVIDER = "rackspace-cloudservers-uk";
    private static final String REGION_NAME = null;
    private static final String IMAGE_ID = "LON/f70ed7c7-b42e-4d77-83d8-40fa29825b85"; // CentOS 6.4
    private static final String IMAGE_NAME_PATTERN = "Ubuntu 13.04";
    private static final String IMAGE_OWNER = null;

    public RackspaceLocationLiveTest() {
        super(PROVIDER);
    }

    @Override
    @DataProvider(name = "fromImageId")
    public Object[][] cloudAndImageIds() {
        return new Object[][] {
            new Object[] { REGION_NAME, IMAGE_ID, IMAGE_OWNER }
        };
    }

    @Override
    @DataProvider(name = "fromImageNamePattern")
    public Object[][] cloudAndImageNamePatterns() {
        return new Object[][] {
            new Object[] { REGION_NAME, IMAGE_NAME_PATTERN, IMAGE_OWNER }
        };
    }

    @Override
    @DataProvider(name = "fromImageDescriptionPattern")
    public Object[][] cloudAndImageDescriptionPatterns() {
        return new Object[0][0];
    }

    @Test(enabled = false)
    public void noop() { /* just exists to let testNG IDE run the test */ }

}

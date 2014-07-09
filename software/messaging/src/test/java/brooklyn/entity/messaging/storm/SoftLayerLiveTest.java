package brooklyn.entity.messaging.storm;

import org.testng.annotations.Test;

@Test(groups="Live")
public class SoftLayerLiveTest extends StormAbstractCloudLiveTest {

    private static final String NAMED_LOCATION = "softlayer";

    @Override
    public String getLocation() {
        return NAMED_LOCATION;
    }

}

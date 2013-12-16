package brooklyn.entity.messaging.storm;

import org.testng.annotations.Test;

@Test(groups="Live")
public class LocalhostLiveTest extends StormAbstractCloudLiveTest {

    private static final String NAMED_LOCATION = "localhost";

    @Override
    public String getLocation() {
        return NAMED_LOCATION;
    }

}

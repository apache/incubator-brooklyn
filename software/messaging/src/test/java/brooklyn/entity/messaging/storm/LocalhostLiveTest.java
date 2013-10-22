package brooklyn.entity.messaging.storm;

import brooklyn.util.collections.MutableMap;
import org.testng.annotations.Test;

import java.util.Map;

@Test(groups="Live")
public class LocalhostLiveTest extends AbstractCloudLiveTest {

    private static final String NAMED_LOCATION = "localhost";

    @Override
    public String getLocation() {
        return NAMED_LOCATION;
    }

    @Override
    public Map<String, ?> getFlags() {
        return MutableMap.of();
    }

}

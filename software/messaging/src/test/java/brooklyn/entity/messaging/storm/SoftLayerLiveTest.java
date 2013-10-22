package brooklyn.entity.messaging.storm;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.messaging.storm.AbstractCloudLiveTest;
import brooklyn.util.collections.MutableMap;
import org.testng.annotations.Test;

import java.util.Map;

@Test(groups="Live")
public class SoftLayerLiveTest extends AbstractCloudLiveTest {

    private static final String NAMED_LOCATION = "softlayer";

    private final BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newDefault();

    @Override
    public String getLocation() {
        return NAMED_LOCATION;
    }

    @Override
    public Map<String, ?> getFlags() {
        return MutableMap.of(
                "identity", getIdentity(),
                "credential", getCredential()
        );
    }

    private String getIdentity() {
        return brooklynProperties.getFirst("brooklyn.location.named." + NAMED_LOCATION + ".identity");
    }

    private String getCredential() {
        return brooklynProperties.getFirst("brooklyn.location.named." + NAMED_LOCATION + ".credential");
    }
}

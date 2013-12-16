package brooklyn.entity.messaging.storm;

import java.util.Map;

import org.testng.annotations.Test;

import brooklyn.util.collections.MutableMap;

@Test(groups="Live")
public class StormGceLiveTest extends StormAbstractCloudLiveTest {

    private static final String NAMED_LOCATION = "gce-europe-west1";
    private static final String LOCATION_ID = "gce-europe-west1-a";
    private static final String URI = "https://www.googleapis.com/compute/v1beta15/projects/google/global/images/centos-6-v20130325";
    private static final String IMAGE_ID = "centos-6-v20130325";

    @Override
    public String getLocation() {
        return NAMED_LOCATION;
    }

    @Override
    public Map<String, ?> getFlags() {
        return MutableMap.of(
                "locationId", LOCATION_ID,
                "imageId", IMAGE_ID,
                "uri", URI + IMAGE_ID,
                "groupId", "storm-test",
                "stopIptables", "true"
        );
    }

}

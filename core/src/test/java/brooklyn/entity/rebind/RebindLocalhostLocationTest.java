package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class RebindLocalhostLocationTest extends RebindTestFixtureWithApp {

    private static final Logger LOG = LoggerFactory.getLogger(RebindLocalhostLocationTest.class);

    private LocalhostMachineProvisioningLocation origLoc;
    private SshMachineLocation origChildLoc;
    
    @BeforeMethod(alwaysRun=true)

    public void setUp() throws Exception {
        super.setUp();
        origLoc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        origChildLoc = origLoc.obtain();
    }

    /** takes a while, so just doing 10; see comment on {@link RebindSshMachineLocationTest} */
    @Test(groups="Integration", invocationCount=10)
    public void testMachineUsableAfterRebindRepeatedly() throws Exception {
        try {
            testMachineUsableAfterRebind();
        } catch (Exception e) {
            // Ensure exception is reported in log immediately, so can match logging with failed run
            LOG.warn("Test failed", e);
            throw e;
        }
    }

    @Test(groups="Integration")
    public void testMachineUsableAfterRebind() throws Exception {
        // TODO See comment in RebindSshMachineLocationTets.testMachineUsableAfterRebind.
        // With locations not being entities, if you switch the order of these two lines then the test sometimes fails.
        // This is because the 'user' field is set (from the default PROP_USER value) when we first exec something.
        // Until that point, the field will be persisted as null, so will be explicitly set to null when deserializing.
        // There's a race for whether we've set the 'user' field before the loc gets persisted (which happens as a side-effect
        // of persisting the app along with its location tree).

        assertEquals(origChildLoc.execScript(Collections.<String,Object>emptyMap(), "mysummary", ImmutableList.of("true")), 0);
        origApp.start(ImmutableList.of(origLoc));

        newApp = (TestApplication) rebind();
        LocalhostMachineProvisioningLocation newLoc = (LocalhostMachineProvisioningLocation) Iterables.getOnlyElement(newApp.getLocations(), 0);
        SshMachineLocation newChildLoc = (SshMachineLocation) Iterables.get(newLoc.getChildren(), 0);
        assertEquals(newChildLoc.execScript(Collections.<String,Object>emptyMap(), "mysummary", ImmutableList.of("true")), 0);
    }
    
}

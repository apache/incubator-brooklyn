package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class RebindLocalhostLocationTest {

    private static final Logger LOG = LoggerFactory.getLogger(RebindLocalhostLocationTest.class);

    private ClassLoader classLoader = getClass().getClassLoader();
    private ManagementContext origManagementContext;
    private TestApplication origApp;
    private LocalhostMachineProvisioningLocation origLoc;
    private SshMachineLocation origChildLoc;
    private TestApplication newApp;
    private File mementoDir;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mementoDir = Files.createTempDir();
        origManagementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
        origApp = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class), origManagementContext);
        origLoc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        origChildLoc = origLoc.obtain();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (origManagementContext != null) Entities.destroyAll(origManagementContext);
        if (newApp != null) Entities.destroyAll(newApp.getManagementContext());
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }

    @Test(enabled=false, groups="Integration", invocationCount=100)
    public void testMachineUsableAfterRebindManyTimes() throws Exception {
        testMachineUsableAfterRebind();
    }
    
    @Test(groups="Integration", invocationCount=100)
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
    
    private TestApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
}

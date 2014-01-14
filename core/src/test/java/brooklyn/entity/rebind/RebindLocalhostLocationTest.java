package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.util.Collections;

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
        origLoc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class)
                .configure("user", System.getProperty("user.name")));
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
    
    @Test(groups="Integration")
    public void testMachineUsableAfterRebind() throws Exception {
        origApp.start(ImmutableList.of(origLoc));

        assertEquals(origChildLoc.execScript(Collections.<String,Object>emptyMap(), "mysummary", ImmutableList.of("true")), 0);

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

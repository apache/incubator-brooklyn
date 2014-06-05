package brooklyn.launcher;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Application;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.ha.ManagementPlaneSyncRecordPersister;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.Asserts;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.os.Os;
import brooklyn.util.time.Duration;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class BrooklynLauncherHighAvailabilityTest {
    
    private static final Duration TIMEOUT = Duration.THIRTY_SECONDS;
    
    private BrooklynLauncher primary;
    private BrooklynLauncher secondary;
    private BrooklynLauncher tertiary;
    private File persistenceDir;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        persistenceDir = Files.createTempDir();
        Os.deleteOnExitRecursively(persistenceDir);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (primary != null) primary.terminate();
        if (secondary != null) secondary.terminate();
        if (tertiary != null) tertiary.terminate();
        if (persistenceDir != null) RebindTestUtils.deleteMementoDir(persistenceDir);
    }
    
    @Test
    public void testStandbyTakesOverWhenPrimaryTerminatedGracefully() throws Exception {
        doTestStandbyTakesOver(true);
    }

    @Test(groups="Integration") // because slow waiting for timeouts to promote standbys
    public void testStandbyTakesOverWhenPrimaryFails() throws Exception {
        doTestStandbyTakesOver(false);
    }
    
    protected void doTestStandbyTakesOver(boolean stopGracefully) throws Exception {
        primary = BrooklynLauncher.newInstance()
                .webconsole(false)
                .brooklynProperties(LocalManagementContextForTests.setEmptyCatalogAsDefault(BrooklynProperties.Factory.newEmpty()))
                .highAvailabilityMode(HighAvailabilityMode.AUTO)
                .persistMode(PersistMode.AUTO)
                .persistenceDir(persistenceDir)
                .persistPeriod(Duration.millis(10))
                .haHeartbeatPeriod(Duration.millis(10))
                .haHeartbeatTimeout(Duration.millis(1000))
                .application(EntitySpec.create(TestApplication.class))
                .start();
        ManagementContext primaryManagementContext = primary.getServerDetails().getManagementContext();
        
        assertOnlyApp(primary.getServerDetails().getManagementContext(), TestApplication.class);
        primaryManagementContext.getRebindManager().getPersister().waitForWritesCompleted(TIMEOUT);
        
        // Secondary will come up as standby
        secondary = BrooklynLauncher.newInstance()
                .webconsole(false)
                .brooklynProperties(LocalManagementContextForTests.setEmptyCatalogAsDefault(BrooklynProperties.Factory.newEmpty()))
                .highAvailabilityMode(HighAvailabilityMode.AUTO)
                .persistMode(PersistMode.AUTO)
                .persistenceDir(persistenceDir)
                .persistPeriod(Duration.millis(10))
                .haHeartbeatPeriod(Duration.millis(10))
                .haHeartbeatTimeout(Duration.millis(1000))
                .start();
        ManagementContext secondaryManagementContext = secondary.getServerDetails().getManagementContext();

        assertNoApps(secondary.getServerDetails().getManagementContext());

        // Terminate primary; expect secondary to take over
        if (stopGracefully) {
            ((ManagementContextInternal)primaryManagementContext).terminate();
        } else {
            ManagementPlaneSyncRecordPersister planePersister = ((ManagementContextInternal)primaryManagementContext).getHighAvailabilityManager().getPersister();
            planePersister.stop(); // can no longer write heartbeats
            ((ManagementContextInternal)primaryManagementContext).terminate();
        }
        
        assertOnlyAppEventually(secondaryManagementContext, TestApplication.class);
        
        // Start tertiary (will come up as standby)
        tertiary = BrooklynLauncher.newInstance()
                .webconsole(false)
                .brooklynProperties(LocalManagementContextForTests.setEmptyCatalogAsDefault(BrooklynProperties.Factory.newEmpty()))
                .highAvailabilityMode(HighAvailabilityMode.AUTO)
                .persistMode(PersistMode.AUTO)
                .persistenceDir(persistenceDir)
                .persistPeriod(Duration.millis(10))
                .haHeartbeatPeriod(Duration.millis(10))
                .haHeartbeatTimeout(Duration.millis(1000))
                .start();
        ManagementContext tertiaryManagementContext = tertiary.getServerDetails().getManagementContext();

        assertNoApps(tertiary.getServerDetails().getManagementContext());

        // Terminate secondary; expect tertiary to take over
        if (stopGracefully) {
            ((ManagementContextInternal)secondaryManagementContext).terminate();
        } else {
            ManagementPlaneSyncRecordPersister planePersister = ((ManagementContextInternal)secondaryManagementContext).getHighAvailabilityManager().getPersister();
            planePersister.stop(); // can no longer write heartbeats
            ((ManagementContextInternal)secondaryManagementContext).terminate();
        }
        
        assertOnlyAppEventually(tertiaryManagementContext, TestApplication.class);
    }
    
    public void testHighAvailabilityMasterModeFailsIfAlreadyHasMaster() throws Exception {
        primary = BrooklynLauncher.newInstance()
                .webconsole(false)
                .brooklynProperties(LocalManagementContextForTests.setEmptyCatalogAsDefault(BrooklynProperties.Factory.newEmpty()))
                .highAvailabilityMode(HighAvailabilityMode.AUTO)
                .persistMode(PersistMode.AUTO)
                .persistenceDir(persistenceDir)
                .persistPeriod(Duration.millis(10))
                .application(EntitySpec.create(TestApplication.class))
                .start();

        try {
            // Secondary will come up as standby
            secondary = BrooklynLauncher.newInstance()
                    .webconsole(false)
                    .brooklynProperties(LocalManagementContextForTests.setEmptyCatalogAsDefault(BrooklynProperties.Factory.newEmpty()))
                    .highAvailabilityMode(HighAvailabilityMode.MASTER)
                    .persistMode(PersistMode.AUTO)
                    .persistenceDir(persistenceDir)
                    .persistPeriod(Duration.millis(10))
                    .start();
            fail();
        } catch (IllegalStateException e) {
            // success
        }
    }
    
    @Test
    public void testHighAvailabilityStandbyModeFailsIfNoExistingMaster() throws Exception {
        try {
            primary = BrooklynLauncher.newInstance()
                    .webconsole(false)
                    .brooklynProperties(LocalManagementContextForTests.setEmptyCatalogAsDefault(BrooklynProperties.Factory.newEmpty()))
                    .highAvailabilityMode(HighAvailabilityMode.STANDBY)
                    .persistMode(PersistMode.AUTO)
                    .persistenceDir(persistenceDir)
                    .persistPeriod(Duration.millis(10))
                    .application(EntitySpec.create(TestApplication.class))
                    .start();
            fail();
        } catch (IllegalStateException e) {
            // success
        }
    }
    
    private void assertOnlyApp(ManagementContext managementContext, Class<? extends Application> expectedType) {
        assertEquals(managementContext.getApplications().size(), 1, "apps="+managementContext.getApplications());
        assertNotNull(Iterables.find(managementContext.getApplications(), Predicates.instanceOf(TestApplication.class), null), "apps="+managementContext.getApplications());
    }
    
    private void assertNoApps(ManagementContext managementContext) {
        assertTrue(managementContext.getApplications().isEmpty(), "apps="+managementContext.getApplications());
    }
    
    private void assertOnlyAppEventually(final ManagementContext managementContext, final Class<? extends Application> expectedType) {
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertOnlyApp(managementContext, expectedType);
            }});
    }
}

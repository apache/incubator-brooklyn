package brooklyn.launcher;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.File;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.entity.Application;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToMultiFile;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.time.Duration;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class BrooklynLauncherRebindTest {
    
    private BrooklynLauncher launcher;
    private File persistenceDir;
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (launcher != null) launcher.terminate();
        if (persistenceDir != null) RebindTestUtils.deleteMementoDir(persistenceDir);
    }
    
    @Test
    public void testRebindsToExistingApp() throws Exception {
        persistenceDir = Files.createTempDir();
        populatePersistenceDir(persistenceDir, EntitySpec.create(TestApplication.class).displayName("myorig"));
        assertFalse(BrooklynLauncher.isMementoDirEmpty(persistenceDir));
        
        // Rebind to the app we started last time
        launcher = BrooklynLauncher.newInstance()
                .webconsole(false)
                .persistMode(PersistMode.REBIND)
                .persistenceDir(persistenceDir)
                .persistPeriod(Duration.millis(10))
                .start();
        
        ManagementContext managementContext = launcher.getServerDetails().getManagementContext();
        assertOnlyApp(managementContext, TestApplication.class);
        assertNotNull(Iterables.find(managementContext.getApplications(), EntityPredicates.displayNameEqualTo("myorig"), null), "apps="+managementContext.getApplications());
    }

    @Test
    public void testRebindCanAddNewApps() throws Exception {
        persistenceDir = Files.createTempDir();
        populatePersistenceDir(persistenceDir, EntitySpec.create(TestApplication.class).displayName("myorig"));
        assertFalse(BrooklynLauncher.isMementoDirEmpty(persistenceDir));
        
        // Rebind to the app we started last time
        launcher = BrooklynLauncher.newInstance()
                .webconsole(false)
                .persistMode(PersistMode.REBIND)
                .persistenceDir(persistenceDir)
                .persistPeriod(Duration.millis(10))
                .application(EntitySpec.create(TestApplication.class).displayName("mynew"))
                .start();
        
        // New app was added, and orig app was rebound
        ManagementContext managementContext = launcher.getServerDetails().getManagementContext();
        assertEquals(managementContext.getApplications().size(), 2, "apps="+managementContext.getApplications());
        assertNotNull(Iterables.find(managementContext.getApplications(), EntityPredicates.displayNameEqualTo("mynew"), null), "apps="+managementContext.getApplications());

        // And subsequently can create new apps
        StartableApplication app3 = launcher.getServerDetails().getManagementContext().getEntityManager().createEntity(
                EntitySpec.create(TestApplication.class).displayName("mynew2"));
        app3.start(ImmutableList.<Location>of());
    }

    @Test
    public void testAutoRebindsToExistingApp() throws Exception {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class);
        persistenceDir = Files.createTempDir();
        populatePersistenceDir(persistenceDir, appSpec);
        
        // Auto will rebind if the dir exists
        launcher = BrooklynLauncher.newInstance()
                .webconsole(false)
                .persistMode(PersistMode.AUTO)
                .persistenceDir(persistenceDir)
                .persistPeriod(Duration.millis(10))
                .persistPeriod(Duration.millis(10))
                .start();
        
        assertOnlyApp(launcher.getServerDetails().getManagementContext(), TestApplication.class);
    }

    @Test
    public void testCleanDoesNotRebindToExistingApp() throws Exception {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class);
        persistenceDir = Files.createTempDir();
        populatePersistenceDir(persistenceDir, appSpec);
        
        // Auto will rebind if the dir exists
        launcher = BrooklynLauncher.newInstance()
                .webconsole(false)
                .persistMode(PersistMode.CLEAN)
                .persistenceDir(persistenceDir)
                .persistPeriod(Duration.millis(10))
                .start();
        
        ManagementContext managementContext = launcher.getServerDetails().getManagementContext();
        assertTrue(managementContext.getApplications().isEmpty(), "apps="+managementContext.getApplications());
    }

    @Test
    public void testAutoRebindCreatesNewIfEmptyDir() throws Exception {
        persistenceDir = Files.createTempDir();
        
        // Auto will rebind if the dir exists
        launcher = BrooklynLauncher.newInstance()
                .webconsole(false)
                .persistMode(PersistMode.AUTO)
                .persistenceDir(persistenceDir)
                .persistPeriod(Duration.millis(10))
                .application(EntitySpec.create(TestApplication.class))
                .start();
        
        assertOnlyApp(launcher.getServerDetails().getManagementContext(), TestApplication.class);
        assertMementoDirNonEmptyEventually(persistenceDir);
    }

    @Test
    public void testRebindRespectsPersistenceDirSetInProperties() throws Exception {
        persistenceDir = Files.createTempDir();
        
        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newDefault();
        brooklynProperties.put(BrooklynServerConfig.PERSISTENCE_DIR, persistenceDir.getAbsolutePath());
        
        // Rebind to the app we started last time
        launcher = BrooklynLauncher.newInstance()
                .brooklynProperties(brooklynProperties)
                .webconsole(false)
                .persistMode(PersistMode.AUTO)
                .start();
        
        ManagementContext managementContext = launcher.getServerDetails().getManagementContext();
        assertEquals(getPersistenceDir(managementContext), persistenceDir);
    }

    @Test
    public void testRebindRespectsDefaultPersistenceDir() throws Exception {
        launcher = BrooklynLauncher.newInstance()
                .webconsole(false)
                .persistMode(PersistMode.CLEAN)
                .start();
        
        ManagementContext managementContext = launcher.getServerDetails().getManagementContext();
        assertEquals(getPersistenceDir(managementContext).getAbsolutePath(), BrooklynServerConfig.getPersistenceDir(BrooklynProperties.Factory.newDefault()));
    }

    static File getPersistenceDir(ManagementContext managementContext) {
        return ((BrooklynMementoPersisterToMultiFile)managementContext.getRebindManager().getPersister()).getDir();
    }
    
    static void assertMementoDirNonEmptyEventually(final File dir) {
        Asserts.succeedsEventually(ImmutableMap.of("timeout", Duration.TEN_SECONDS), new Runnable() {
            @Override public void run() {
                assertFalse(BrooklynLauncher.isMementoDirEmpty(dir));
            }});
    }

    @Test
    public void testPersistenceFailsIfNoDir() throws Exception {
        runRebindFails(PersistMode.REBIND, new File("/path/does/not/exist"), "does not exist");
    }

    @Test
    public void testPersistenceFailsIfIsFile() throws Exception {
        File tempFile = File.createTempFile("rebindFailsIfIsFile", "tmp");
        try {
            runRebindFails(PersistMode.AUTO, tempFile, "not a directory");
            runRebindFails(PersistMode.REBIND, tempFile, "not a directory");
            runRebindFails(PersistMode.CLEAN, tempFile, "not a directory");
        } finally {
            tempFile.delete();
        }
    }

    @Test
    public void testPersistenceFailsIfNotWritable() throws Exception {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class);
        persistenceDir = Files.createTempDir();
        populatePersistenceDir(persistenceDir, appSpec);
        persistenceDir.setWritable(false);
        try {
            runRebindFails(PersistMode.AUTO, persistenceDir, "not writable");
            runRebindFails(PersistMode.REBIND, persistenceDir, "not writable");
            runRebindFails(PersistMode.CLEAN, persistenceDir, "not writable");
        } finally {
            persistenceDir.setWritable(true);
        }
    }

    @Test
    public void testPersistenceFailsIfNotReadable() throws Exception {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class);
        persistenceDir = Files.createTempDir();
        populatePersistenceDir(persistenceDir, appSpec);
        persistenceDir.setReadable(false);
        try {
            runRebindFails(PersistMode.AUTO, persistenceDir, "not readable");
            runRebindFails(PersistMode.REBIND, persistenceDir, "not readable");
            runRebindFails(PersistMode.CLEAN, persistenceDir, "not readable");
        } finally {
            persistenceDir.setReadable(true);
        }
    }

    @Test
    public void testExplicitRebindFailsIfEmpty() throws Exception {
        persistenceDir = Files.createTempDir();
        
        runRebindFails(PersistMode.REBIND, persistenceDir, "directory is empty");
    }

    protected void runRebindFails(PersistMode persistMode, File dir, String errmsg) throws Exception {
        try {
            launcher = BrooklynLauncher.newInstance()
                    .webconsole(false)
                    .persistMode(persistMode)
                    .persistenceDir(dir)
                    .start();
        } catch (FatalConfigurationRuntimeException e) {
            if (!e.toString().contains(errmsg)) {
                throw e;
            }
        }
    }

    private void populatePersistenceDir(File dir, EntitySpec<? extends StartableApplication> appSpec) throws Exception {
        launcher = BrooklynLauncher.newInstance()
                .webconsole(false)
                .persistMode(PersistMode.CLEAN)
                .persistenceDir(dir)
                .persistPeriod(Duration.millis(10))
                .application(appSpec)
                .start();
        launcher.terminate();
        launcher = null;
        assertFalse(BrooklynLauncher.isMementoDirEmpty(dir));
    }
    
    private void assertOnlyApp(ManagementContext managementContext, Class<? extends Application> expectedType) {
        assertEquals(managementContext.getApplications().size(), 1, "apps="+managementContext.getApplications());
        assertNotNull(Iterables.find(managementContext.getApplications(), Predicates.instanceOf(TestApplication.class), null), "apps="+managementContext.getApplications());
    }
}

package brooklyn.entity.basic;

import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.os.Os;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;


public class SoftwareProcessSshDriverIntegrationTest {

    private LocalManagementContext managementContext;
    private LocalhostMachineProvisioningLocation localhost;
    private SshMachineLocation machine127;
    private TestApplication app;
    private File tempDataDir;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        tempDataDir = Files.createTempDir();
        managementContext = new LocalManagementContext();
        
        localhost = managementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        machine127 = managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "localhost"));
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
        if (tempDataDir != null) Os.tryDeleteDirectory(tempDataDir);
    }

    // Integration test because requires ssh'ing (and takes about 5 seconds)
    // See also SoftwareProcessEntityTest.testCustomInstallDirX for a lot more mocked variants
    @Test(groups="Integration")
    public void testCanInstallMultipleVersionsOnSameMachine() throws Exception {
        managementContext.getBrooklynProperties().put(BrooklynConfigKeys.ONBOX_BASE_DIR, tempDataDir.getAbsolutePath());

        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class)
                .configure(SoftwareProcess.SUGGESTED_VERSION, "0.1.0"));
        MyService entity2 = app.createAndManageChild(EntitySpec.create(MyService.class)
                .configure(SoftwareProcess.SUGGESTED_VERSION, "0.2.0"));
        app.start(ImmutableList.of(machine127));
        
        String installDir1 = entity.getAttribute(SoftwareProcess.INSTALL_DIR);
        String installDir2 = entity2.getAttribute(SoftwareProcess.INSTALL_DIR);
        
        assertNotEquals(installDir1, installDir2);
        assertTrue(installDir1.contains("0.1.0"), "installDir1="+installDir1);
        assertTrue(installDir2.contains("0.2.0"), "installDir2="+installDir2);
        assertTrue(new File(new File(installDir1), "myfile").isFile());
        assertTrue(new File(new File(installDir2), "myfile").isFile());
    }

    @Test(groups="Integration")
    public void testLocalhostInTmp() throws Exception {
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        app.start(ImmutableList.of(localhost));

        String installDir = entity.getAttribute(SoftwareProcess.INSTALL_DIR);
        assertTrue(installDir.startsWith("/tmp/brooklyn-"+Os.user()+"/installs/"), "installed in "+installDir);
    }

    @Test(groups="Integration")
    public void testMachine127InHome() throws Exception {
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        app.start(ImmutableList.of(machine127));

        String installDir = entity.getAttribute(SoftwareProcess.INSTALL_DIR);
        assertTrue(installDir.startsWith(Os.home()+"/brooklyn-managed-processes/installs/"), "installed in "+installDir);
    }

    @Test(groups="Integration")
    public void testLocalhostInCustom() throws Exception {
        localhost.setConfig(BrooklynConfigKeys.ONBOX_BASE_DIR, tempDataDir.getAbsolutePath());

        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        app.start(ImmutableList.of(localhost));

        String installDir = entity.getAttribute(SoftwareProcess.INSTALL_DIR);
        assertTrue(installDir.startsWith(tempDataDir.getAbsolutePath()+"/installs/"), "installed in "+installDir);
    }

    @Test(groups="Integration")
    @Deprecated
    public void testMachineInCustomFromDataDir() throws Exception {
        managementContext.getBrooklynProperties().put(BrooklynConfigKeys.BROOKLYN_DATA_DIR, tempDataDir.getAbsolutePath());

        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        app.start(ImmutableList.of(machine127));

        String installDir = entity.getAttribute(SoftwareProcess.INSTALL_DIR);
        assertTrue(installDir.startsWith(tempDataDir.getAbsolutePath()+"/installs/"), "installed in "+installDir);
    }

    @ImplementedBy(MyServiceImpl.class)
    public interface MyService extends SoftwareProcess {
        public SoftwareProcessDriver getDriver();
    }
    
    public static class MyServiceImpl extends SoftwareProcessImpl implements MyService {
        public MyServiceImpl() {
        }

        @Override
        public Class<?> getDriverInterface() {
            return SimulatedDriver.class;
        }
    }

    public static class SimulatedDriver extends AbstractSoftwareProcessSshDriver {
        public List<String> events = new ArrayList<String>();
        private volatile boolean launched = false;
        
        public SimulatedDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
        }

        @Override
        public void install() {
            events.add("install");
            newScript(INSTALLING)
                    .failOnNonZeroResultCode()
                    .body.append("touch myfile")
                    .execute();
        }
        
        @Override
        public void customize() {
            events.add("customize");
        }
    
        @Override
        public void launch() {
            events.add("launch");
            launched = true;
            entity.setAttribute(Startable.SERVICE_UP, true);
        }
        
        @Override
        public boolean isRunning() {
            return launched;
        }
    
        @Override
        public void stop() {
            events.add("stop");
            launched = false;
            entity.setAttribute(Startable.SERVICE_UP, false);
        }
    
        @Override
        public void kill() {
            events.add("kill");
            launched = false;
            entity.setAttribute(Startable.SERVICE_UP, false);
        }
    }
}

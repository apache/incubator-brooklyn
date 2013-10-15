package brooklyn.entity.monitoring.monit;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SameServerEntity;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

public class MonitIntegrationTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(MonitIntegrationTest.class);
    
    protected BrooklynProperties brooklynProperties;
    protected ManagementContext managementContext;
    protected TestApplication app;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        managementContext = new LocalManagementContext(brooklynProperties);
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }
    
    @AfterMethod(alwaysRun = true)
    public void ensureShutDown() {
        if (app != null) {
            Entities.destroyAll(managementContext);
            app = null;
        }
    }
    
    @Test(groups = "Integration")
    public void test_localhost() throws InterruptedException {
        final MonitNode monitNode = app.createAndManageChild(EntitySpec.create(MonitNode.class)
            .configure(MonitNode.CONTROL_FILE_URL, "classpath:///brooklyn/entity/monitoring/monit/monit.monitrc"));
        LocalhostMachineProvisioningLocation location = new LocalhostMachineProvisioningLocation();
        app.start(ImmutableSet.of(location));
        LOG.info("Monit started");
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                assertEquals(monitNode.getAttribute(MonitNode.MONIT_TARGET_PROCESS_STATUS), "Running");
            }
        });
    }
    
    @Test(groups = "Integration")
    public void test_monitorMySql() throws InterruptedException {
        SameServerEntity sameServerEntity = app.createAndManageChild(EntitySpec.create(SameServerEntity.class));
        LocalhostMachineProvisioningLocation location = new LocalhostMachineProvisioningLocation();
        MySqlNode mySqlNode = sameServerEntity.addChild(EntitySpec.create(MySqlNode.class));
        Entities.manage(mySqlNode);
        Function controlFileSubstitutionsFunction = new Function<String, Map<String, Object>>() {
            public Map<String, Object> apply(String input) {
                return ImmutableMap.<String, Object>of("targetPidFile", input);
            }
        };
        final MonitNode monitNode = sameServerEntity.addChild(EntitySpec.create(MonitNode.class)
            .configure(MonitNode.CONTROL_FILE_URL, "classpath:///brooklyn/entity/monitoring/monit/monitmysql.monitrc")
            .configure(MonitNode.CONTROL_FILE_SUBSTITUTIONS, DependentConfiguration.valueWhenAttributeReady(mySqlNode, 
                MySqlNode.PID_FILE, controlFileSubstitutionsFunction)));
        Entities.manage(monitNode);
        app.start(ImmutableSet.of(location));
        LOG.info("Monit and MySQL started");
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                String targetStatus = monitNode.getAttribute(MonitNode.MONIT_TARGET_PROCESS_STATUS);
                LOG.debug("MonitNode target status: {}", targetStatus);
                assertEquals(elvis(targetStatus, ""), "Running");
            }
        });
        mySqlNode.stop();
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                String targetStatus = monitNode.getAttribute(MonitNode.MONIT_TARGET_PROCESS_STATUS);
                LOG.debug("MonitNode target status: {}", targetStatus);
                assertNotEquals(elvis(targetStatus, ""), "Running");
            }
        });
        mySqlNode.restart();
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                String targetStatus = monitNode.getAttribute(MonitNode.MONIT_TARGET_PROCESS_STATUS);
                LOG.debug("MonitNode target status: {}", targetStatus);
                assertEquals(elvis(targetStatus, ""), "Running");
            }
        });
    }
    
    @Test(groups = "Integration")
    public void test_monitorMySqlAutoRestart() throws InterruptedException {
        // The monit node needs to know the installation and run directory of the mysql dir, so we need to specify it explicitly  
        File tempDir = Files.createTempDir();
        tempDir.deleteOnExit();
        final String mySqlInstallDir = tempDir.getAbsolutePath() + "/install";
        final String mySqlRunDir = tempDir.getAbsolutePath() + "/run";
        final String mySqlDataDir = tempDir.getAbsolutePath() + "/data";
        
        SameServerEntity sameServerEntity = app.createAndManageChild(EntitySpec.create(SameServerEntity.class));
        LocalhostMachineProvisioningLocation location = new LocalhostMachineProvisioningLocation();
        final MySqlNode mySqlNode = sameServerEntity.addChild(EntitySpec.create(MySqlNode.class)
            .configure(MySqlNode.SUGGESTED_INSTALL_DIR, mySqlInstallDir)
            .configure(MySqlNode.SUGGESTED_RUN_DIR, mySqlRunDir)
            .configure(MySqlNode.DATA_DIR, mySqlDataDir));
        Entities.manage(mySqlNode);
        Function controlFileSubstitutionsFunction = new Function<String, Map<String, Object>>() {
            public Map<String, Object> apply(String input) {
                return ImmutableMap.<String, Object>of(
                    "targetPidFile", input,
                    "mySqlInstallDir", mySqlInstallDir,
                    "mySqlRunDir", mySqlRunDir
                );
            }
        };
        final MonitNode monitNode = sameServerEntity.addChild(EntitySpec.create(MonitNode.class)
            .configure(MonitNode.CONTROL_FILE_URL, "classpath:///brooklyn/entity/monitoring/monit/monitmysqlwithrestart.monitrc")
            .configure(MonitNode.CONTROL_FILE_SUBSTITUTIONS, DependentConfiguration.valueWhenAttributeReady(mySqlNode, 
                MySqlNode.PID_FILE, controlFileSubstitutionsFunction)));
        Entities.manage(monitNode);
        app.start(ImmutableSet.of(location));
        LOG.info("Monit and MySQL started");
        final String[] initialPid = {""};
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                String targetStatus = monitNode.getAttribute(MonitNode.MONIT_TARGET_PROCESS_STATUS);
                LOG.debug("MonitNode target status: {}", targetStatus);
                assertEquals(elvis(targetStatus, ""), "Running");
                try {
                	initialPid[0] = Files.readFirstLine(new File(mySqlNode.getAttribute(MySqlNode.PID_FILE)), Charset.defaultCharset());
                	LOG.debug("Initial PID: {}", initialPid[0]);
                } catch (IOException e) {
                	Asserts.fail("Could not read PID file: " + e);
                }
            }
        });
        mySqlNode.stop();
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                String targetStatus = monitNode.getAttribute(MonitNode.MONIT_TARGET_PROCESS_STATUS);
                LOG.debug("MonitNode target status: {}", targetStatus);
                assertNotEquals(elvis(targetStatus, ""), "Running");
            }
        });
        // NOTE: Do not manually restart the mySqlNode, it should be restarted by monit
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
            	try {
            		String pidFileLocation = mySqlNode.getAttribute(MySqlNode.PID_FILE);
            		String newPid = Files.readFirstLine(new File(pidFileLocation), Charset.defaultCharset());
            		LOG.debug("Old PID: {}, New PID: {} read from PID file: {}", new String[] {initialPid[0], newPid, pidFileLocation});
            		assertNotEquals(initialPid[0], newPid, "Process PID has not changed");
            	} catch (IOException e) {
            		Asserts.fail("Could not read PID file: " + e);
            	}
                String targetStatus = monitNode.getAttribute(MonitNode.MONIT_TARGET_PROCESS_STATUS);
                LOG.debug("MonitNode target status: {}", targetStatus);
                assertEquals(elvis(targetStatus, ""), "Running");
            }
        });
    }
}

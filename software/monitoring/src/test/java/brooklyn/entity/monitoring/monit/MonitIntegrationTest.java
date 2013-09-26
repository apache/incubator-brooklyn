package brooklyn.entity.monitoring.monit;

import static brooklyn.util.GroovyJavaMethods.elvis;

import java.util.Map;

import org.junit.Assert;
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
import brooklyn.test.entity.TestApplication;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

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
            Entities.destroyAll(app.getManagementContext());
            app = null;
        }
    }
    
    @Test(groups = "Integration")
    public void test_localhost() throws InterruptedException {
        MonitNode monitNode = app.createAndManageChild(EntitySpec.create(MonitNode.class)
            .configure(MonitNode.CONTROL_FILE_URL, "classpath:///brooklyn/entity/monitoring/monit/monit.monitrc"));
        LocalhostMachineProvisioningLocation location = new LocalhostMachineProvisioningLocation();
        app.start(ImmutableSet.of(location));
        LOG.info("Monit started");
        boolean started = false;
        for (int i = 1; i < 10; i++) {
            if (monitNode.getAttribute(MonitNode.MONIT_TARGET_PROCESS_STATUS).equals("Running")) {
                started = true;
                break;
            }
            Thread.sleep(1000);
        }
        Assert.assertTrue("Expected monit target process to return status 'Running' within 10 seconds", started);
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
        MonitNode monitNode = sameServerEntity.addChild(EntitySpec.create(MonitNode.class)
            .configure(MonitNode.CONTROL_FILE_URL, "classpath:///brooklyn/entity/monitoring/monit/monitmysql.monitrc")
            .configure(MonitNode.CONTROL_FILE_SUBSTITUTIONS, DependentConfiguration.valueWhenAttributeReady(mySqlNode, 
                MySqlNode.PID_FILE, controlFileSubstitutionsFunction)));
        Entities.manage(monitNode);
        app.start(ImmutableSet.of(location));
        LOG.info("Monit and MySQL started");
        boolean started = false;
        for (int i = 1; i < 10; i++) {
            String targetStatus = monitNode.getAttribute(MonitNode.MONIT_TARGET_PROCESS_STATUS);
            LOG.debug("MonitNode target status: {}", targetStatus);
            if (elvis(targetStatus, "").equals("Running")) {
                started = true;
                break;
            }
            Thread.sleep(1000);
        }
        Assert.assertTrue("Expected monit target process to return status 'Running' within 10 seconds", started);
    }
}

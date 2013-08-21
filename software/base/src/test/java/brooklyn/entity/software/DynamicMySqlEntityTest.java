package brooklyn.entity.software;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.location.LocationSpec;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.task.ssh.SshTaskWrapper;
import brooklyn.util.task.ssh.SshTasks;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;


public class DynamicMySqlEntityTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicMySqlEntityTest.class);
    
    protected TestApplication app;
    protected ManagementContext mgmt;
    protected LocalhostMachineProvisioningLocation localhostCloud;

    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        mgmt = app.getManagementContext();
        
        localhostCloud = mgmt.getLocationManager().createLocation(LocationSpec.create(
                LocalhostMachineProvisioningLocation.class));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
        mgmt = null;
    }

    @Test(groups="Integration")
    public void testMySqlOnLocalhost() throws NoMachinesAvailableException {
        Entity mysql = createMysql();
        
        SshMachineLocation lh = localhostCloud.obtain();
        app.start(Arrays.asList(lh));
        
        Asserts.eventually(MutableMap.of("timeout", Duration.FIVE_MINUTES),
                Entities.attributeSupplier(mysql, Attributes.SERVICE_STATE),
                Predicates.equalTo(Lifecycle.RUNNING));
        
        Integer pid = mysql.getAttribute(Attributes.PID);
        Assert.assertNotNull(pid);
        SshTasks.newSshTaskFactory(lh, "ps -p "+pid).requiringExitCodeZero();
        
        app.stop();

        // let the kill -1 take effect 
        Time.sleep(Duration.ONE_SECOND);
        
        // and assert it has died
        log.info("mysql in pid "+pid+" should be dead now");
        SshTaskWrapper<Integer> t = SshTasks.newSshTaskFactory(lh, "ps -p "+pid).allowingNonZeroExitCode().newTask();
        mgmt.getExecutionManager().submit(t);
        t.block();
        Assert.assertNotEquals(t.getExitCode(), (Integer)0);
    }

    @Test(groups="Integration")
    public void testMySqlOnLocalhostProvisioning() throws NoMachinesAvailableException {
        Entity mysql = createMysql();
        
        app.start(Arrays.asList(localhostCloud));
        
        Asserts.eventually(MutableMap.of("timeout", Duration.FIVE_MINUTES),
                Entities.attributeSupplier(mysql, Attributes.SERVICE_STATE),
                Predicates.equalTo(Lifecycle.RUNNING));
        
        SshMachineLocation lh = (SshMachineLocation) Iterables.getOnlyElement( mysql.getLocations() );
        Integer pid = mysql.getAttribute(Attributes.PID);
        Assert.assertNotNull(pid);
        SshTasks.newSshTaskFactory(lh, "ps -p "+pid).requiringExitCodeZero();
        
        app.stop();

        // let the kill -1 take effect 
        Time.sleep(Duration.ONE_SECOND);
        
        // and assert it has died
        log.info("mysql in pid "+pid+" should be dead now");
        SshTaskWrapper<Integer> t = SshTasks.newSshTaskFactory(lh, "ps -p "+pid).allowingNonZeroExitCode().newTask();
        mgmt.getExecutionManager().submit(t);
        t.block();
        Assert.assertNotEquals(t.getExitCode(), (Integer)0);
    }

    protected Entity createMysql() {
        Entity mysql = app.createAndManageChild(TestDynamicMySqlEntityBuilder.spec());
        TestDynamicMySqlEntityBuilder.makeMySql((EntityInternal) mysql);
        return mysql;
    }
    
}

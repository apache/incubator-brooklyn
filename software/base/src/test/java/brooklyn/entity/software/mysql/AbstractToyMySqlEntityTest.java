package brooklyn.entity.software.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.LocationSpec;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.task.ssh.SshExecTaskWrapper;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;


public abstract class AbstractToyMySqlEntityTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractToyMySqlEntityTest.class);
    
    protected TestApplication app;
    protected ManagementContext mgmt;
    protected MachineProvisioningLocation<? extends SshMachineLocation> targetLocation;

    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        mgmt = app.getManagementContext();
        
        targetLocation = createLocation();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
        mgmt = null;
    }

    protected MachineProvisioningLocation<? extends SshMachineLocation> createLocation() {
        return mgmt.getLocationManager().createLocation(LocationSpec.create(
                LocalhostMachineProvisioningLocation.class));
    }
    
    protected abstract Entity createMysql();

    // deliberately not marked as a test here so that subclasses mark it correctly (Live v Integration)
    public void testMySqlOnProvisioningLocation() throws NoMachinesAvailableException {
        Entity mysql = createMysql();
        app.start(MutableList.of(targetLocation));
        checkStartsRunning(mysql);
        checkIsRunningAndStops(mysql, (SshMachineLocation) Iterables.getOnlyElement( mysql.getLocations() ));
    }

    protected Integer getPid(Entity mysql) {
        return mysql.getAttribute(Attributes.PID);
    }

    protected void checkStartsRunning(Entity mysql) {
        // should be starting within a few seconds (and almost certainly won't complete in that time) 
        Asserts.eventually(MutableMap.of("timeout", Duration.FIVE_SECONDS),
                Entities.attributeSupplier(mysql, Attributes.SERVICE_STATE),
                Predicates.or(Predicates.equalTo(Lifecycle.STARTING), Predicates.equalTo(Lifecycle.RUNNING)));
        // should be up and running within 5m 
        Asserts.eventually(MutableMap.of("timeout", Duration.FIVE_MINUTES),
                Entities.attributeSupplier(mysql, Attributes.SERVICE_STATE),
                Predicates.equalTo(Lifecycle.RUNNING));
    }

    protected void checkIsRunningAndStops(Entity mysql, SshMachineLocation lh) {
        Integer pid = getPid(mysql);
        Assert.assertNotNull(pid, "PID should be set as an attribute (or getPid() overridden to supply)");
        Entities.submit(app, SshEffectorTasks.requirePidRunning(pid).machine(lh).newTask() ).get();
        
        app.stop();

        // let the kill -1 take effect 
        Time.sleep(Duration.ONE_SECOND);
        
        // and assert it has died
        log.info("mysql in pid "+pid+" should be dead now");
        // (app has stopped, so submit on mgmt context)
        SshExecTaskWrapper<Integer> t = SshEffectorTasks.codePidRunning(pid).machine(lh).newTask();
        mgmt.getExecutionManager().submit(t);
        Assert.assertNotEquals(t.block().getExitCode(), (Integer)0);
    }
    
}

package brooklyn.entity.basic.lifecycle;

import java.util.List;
import java.util.Map;

import org.testng.Assert;
import org.testng.TestException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcessEntityTest;
import brooklyn.entity.basic.SoftwareProcessEntityTest.MyServiceImpl;
import brooklyn.entity.trait.Startable;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

public class ScriptHelperTest extends BrooklynAppUnitTestSupport {
    
    private SshMachineLocation machine;
    private FixedListMachineProvisioningLocation<SshMachineLocation> loc;
    boolean shouldFail = false;
    int failCount = 0;
    
    @BeforeMethod(alwaysRun=true)
    @SuppressWarnings("unchecked")
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = mgmt.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class));
        machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "localhost"));
        loc.addMachine(machine);
    }
    
    @Test(groups = "Integration")
    public void testCheckRunningForcesInessential() {
        MyServiceInessentialDriverImpl entity = new MyServiceInessentialDriverImpl(app);
        Entities.manage(entity);
        
        entity.start(ImmutableList.of(loc));
        SimulatedInessentialIsRunningDriver driver = (SimulatedInessentialIsRunningDriver) entity.getDriver();
        Assert.assertTrue(driver.isRunning());
        
        entity.connectPolling();
        
        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);
        driver.setFailExecution(true);
        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, false);
        driver.setFailExecution(false);
        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);
    }
    
    private class MyServiceInessentialDriverImpl extends MyServiceImpl {
        public MyServiceInessentialDriverImpl(Entity parent) {
            super(parent);
        }
        
        @Override public Class<?> getDriverInterface() {
            return SimulatedInessentialIsRunningDriver.class;
        }
        
        public void connectPolling() {
            connectServiceUpIsRunning();
        }
    }
    
    public static class SimulatedInessentialIsRunningDriver extends SoftwareProcessEntityTest.SimulatedDriver {
        private boolean failExecution = false;

        public SimulatedInessentialIsRunningDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
        }
        
        @Override
        public boolean isRunning() {
            return newScript(CHECK_RUNNING)
                .body.append("ls")
                .failOnNonZeroResultCode()
                .execute() == 0;
        }
        
        @Override
        public int execute(List<String> script, String summaryForLogging) {
            if (failExecution) {
                throw new TestException("Simulated driver exception");
            }
            return super.execute(script, summaryForLogging);
        }
        
        @SuppressWarnings("rawtypes")
        @Override
        public int execute(Map flags2, List<String> script, String summaryForLogging) {
            if (failExecution) {
                throw new TestException("Simulated driver exception");
            }
            return super.execute(flags2, script, summaryForLogging);
        }
        
        public void setFailExecution(boolean failExecution) {
            this.failExecution = failExecution;
        }
        
    }
    
}

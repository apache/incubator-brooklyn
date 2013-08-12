package brooklyn.policy.ha;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.ManagementContext;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.config.ConfigBag;

import com.google.common.collect.ImmutableList;

public class ServiceRestarterTest {

    private static final int TIMEOUT_MS = 10*1000;

    private ManagementContext managementContext;
    private TestApplication app;
    private TestEntity e1;
    private ServiceRestarter policy;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = Entities.newManagementContext();
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        e1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testRestartsOnFailure() throws Exception {
        policy = new ServiceRestarter(new ConfigBag().configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));
        e1.addPolicy(policy);
        
        e1.emit(HASensors.ENTITY_FAILED, new FailureDescriptor(e1, "simulate failure"));
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(e1.getCallHistory(), ImmutableList.of("restart"));
            }});
    }
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testDoesNotRestartsWhenHealthy() throws Exception {
        policy = new ServiceRestarter(new ConfigBag().configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));
        e1.addPolicy(policy);
        
        e1.emit(HASensors.ENTITY_RECOVERED, new FailureDescriptor(e1, "not a failure"));
        
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertEquals(e1.getCallHistory(), ImmutableList.of());
            }});
    }
}

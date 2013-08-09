package brooklyn.entity.basic.lifecycle;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;

public class JavaSoftwareProcessSshDriverIntegrationTest {

    private static final long TIMEOUT_MS = 10*1000;
    
    private MachineProvisioningLocation localhost = new LocalhostMachineProvisioningLocation(MutableMap.of("name", "localhost"));
    private TestApplication app;

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups = "Integration")
    public void testJavaStartStopSshDriverStartsAndStopsApp() {
        final MyEntity entity = app.createAndManageChild(EntitySpec.create(MyEntity.class));
        app.start(ImmutableList.of(localhost));
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                assertTrue(entity.getAttribute(SoftwareProcess.SERVICE_UP));
            }});
        
        entity.stop();
        assertFalse(entity.getAttribute(SoftwareProcess.SERVICE_UP));
    }
}

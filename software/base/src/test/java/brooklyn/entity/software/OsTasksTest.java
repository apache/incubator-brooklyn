package brooklyn.entity.software;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.location.LocationSpec;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.test.entity.TestApplication;

public class OsTasksTest {

    public static final Logger LOG = LoggerFactory.getLogger(OsTasksTest.class);

    TestApplication app;
    ManagementContext mgmt;
    SshMachineLocation host;

    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        mgmt = app.getManagementContext();

        LocalhostMachineProvisioningLocation localhost = mgmt.getLocationManager().createLocation(
                LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        host = localhost.obtain();
        app.start(Arrays.asList(host));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
        mgmt = null;
    }

    @Test(groups="Integration")
    public void testGetOsDetails() {
        Task<OsDetails> detailsTask = app.getExecutionContext().submit(OsTasks.getOsDetailsTask(app));
        OsDetails details = detailsTask.getUnchecked();
        LOG.info("testGetOsDetails found the following on localhost: name={}, version={}, arch={}, is64bit={}",
                new Object[] {details.getName(), details.getVersion(), details.getArch(), details.is64bit()});
        assertNotNull(details);
        assertNotNull(details.getArch());
        assertNotNull(details.getName());
        assertNotNull(details.getVersion());
        assertFalse(details.getArch().startsWith("architecture:"), "architecture prefix not removed from details");
        assertFalse(details.getName().startsWith("name:"), "name prefix not removed from details");
        assertFalse(details.getVersion().startsWith("version:"), "version prefix not removed from details");
    }

}

package brooklyn.entity.java;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Optional;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.entity.TestApplication;

public class JavaSoftwareProcessSshDriverTest {

    private static final Logger LOG = LoggerFactory.getLogger(JavaSoftwareProcessSshDriverTest.class);

    TestApplication app;
    JavaSoftwareProcessSshDriver driver;

    private static class ConcreteJavaSoftwareProcessSshDriver extends JavaSoftwareProcessSshDriver {
        public ConcreteJavaSoftwareProcessSshDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
        }
        @Override protected String getLogFileLocation() { return null; }
        @Override public boolean isRunning() { return false; }
        @Override public void stop() {}
        @Override public void install() {}
        @Override public void customize() {}
        @Override public void launch() {}
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        SshMachineLocation sshLocation = app.getManagementContext().getLocationManager().createLocation(
                LocationSpec.create(SshMachineLocation.class).configure("address", "localhost"));
        driver = new ConcreteJavaSoftwareProcessSshDriver(app, sshLocation);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testGetJavaVersion() {
        Optional<String> version = driver.getCurrentJavaVersion();
        assertNotNull(version);
        assertTrue(version.isPresent());
        LOG.info("{}.testGetJavaVersion found: {} on localhost", getClass(), version.get());
    }

}

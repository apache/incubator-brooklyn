package brooklyn.entity.osgi.karaf

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.TimeUnit

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.trait.Startable
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.util.internal.LanguageUtils
import brooklyn.util.internal.TimeExtras

public class KarafContainerTest {
    static { TimeExtras.init() }

	MachineProvisioningLocation localhost = new LocalhostMachineProvisioningLocation(name:'localhost', count:2, address:"localhost")
	AbstractApplication app
    KarafContainer karaf

    @BeforeMethod(alwaysRun=true)
    public void setup() {
		app = new AbstractApplication() {}
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroy(app);
    }

    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        karaf = new KarafContainer(owner:app, name:LanguageUtils.newUid(), displayName:"Karaf Test", jmxPort:"8099+", rmiServerPort:"9099+");
        app.start([ localhost ]);
        executeUntilSucceeds(timeout:30 * SECONDS) {
            assertNotNull karaf.getAttribute(Startable.SERVICE_UP)
            assertTrue karaf.getAttribute(Startable.SERVICE_UP)
        }
        
        karaf.stop();
        executeUntilSucceeds(timeout:10 * SECONDS) {
            assertFalse karaf.getAttribute(Startable.SERVICE_UP)
        }
    }
}

package monterey.brooklyn.karaf

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.TimeUnit

import monterey.brooklyn.karaf.KarafContainer

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.legacy.JavaApp
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
        if (karaf) karaf.stop()
        app.stop()
    }

    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        karaf = new KarafContainer(owner:app, name:LanguageUtils.newUid(), displayName:"Karaf Test", jmxPort:8099, rmiPort:9099);
        app.start([ localhost ]);
        executeUntilSucceedsWithShutdown(karaf, timeout:30 * SECONDS) {
            assertNotNull karaf.getAttribute(JavaApp.SERVICE_UP)
            assertTrue karaf.getAttribute(JavaApp.SERVICE_UP)
        }
        assertFalse karaf.getAttribute(JavaApp.SERVICE_UP)
    }
}

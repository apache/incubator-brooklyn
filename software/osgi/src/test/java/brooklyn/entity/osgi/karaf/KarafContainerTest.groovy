package brooklyn.entity.osgi.karaf

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.TimeUnit

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxying.EntitySpecs
import brooklyn.entity.trait.Startable
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras
import brooklyn.util.text.Identifiers

public class KarafContainerTest {
    static { TimeExtras.init() }

    MachineProvisioningLocation localhost = new LocalhostMachineProvisioningLocation(name:'localhost', address:"localhost")
    TestApplication app
    KarafContainer karaf

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    // FIXME Test failing in jenkins; not sure why. The karaf log shows the mbeans never being
    // registered so we are never able to connect to them over jmx.
    @Test(groups = ["Integration", "WIP"])
    public void canStartupAndShutdown() {
        karaf = app.createAndManageChild(EntitySpecs.spec(KarafContainer.class)
                .configure("name", Identifiers.makeRandomId(8))
                .configure("displayName", "Karaf Test")
                .configure("jmxPort", "8099+")
                .configure("rmiServerPort", "9099+"));
        
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
    
    // FIXME Test failing in jenkins; not sure why. The karaf log shows the mbeans never being
    // registered so we are never able to connect to them over jmx.
    @Test(groups = ["Integration", "WIP"])
    public void testCanInstallAndUninstallBundle() {
        karaf = app.createAndManageChild(EntitySpecs.spec(KarafContainer.class)
            .configure("name", Identifiers.makeRandomId(8))
            .configure("displayName", "Karaf Test")
            .configure("jmxPort", "8099+")
            .configure("rmiServerPort", "9099+"));
        
        app.start([ localhost ]);
        
        URL jarUrl = getClass().getClassLoader().getResource("hello-world.jar");
        assertNotNull(jarUrl);
        
        long bundleId = karaf.installBundle("wrap:"+jarUrl.toString());
        
        Map<Long, Map<String,?>> bundles = karaf.listBundles();
        Map<String,?> bundle = bundles.get(bundleId);
        assertNotNull(bundle, "expected="+bundleId+"; actual="+bundles.keySet());

        // Undeploy: expect bundle to no longer be listed        
        karaf.uninstallBundle(bundleId);
        
        Map<Long, Map<String,?>> bundles2 = karaf.listBundles();
        Map<String,?> bundle2 = bundles2.get(bundleId);
        assertNull(bundle2, "expectedAbsent="+bundleId+"; actual="+bundles2.keySet());
    }
}

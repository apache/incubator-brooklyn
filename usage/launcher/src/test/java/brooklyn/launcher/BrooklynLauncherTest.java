package brooklyn.launcher;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.net.URI;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Application;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestApplicationImpl;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class BrooklynLauncherTest {
    
    private BrooklynLauncher launcher;
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (launcher != null) launcher.terminate();
    }

    // Integration because takes a few seconds to start web-console
    @Test(groups="Integration")
    public void testStartsWebServerOnExpectectedPort() throws Exception {
        launcher = BrooklynLauncher.newInstance()
                .webconsolePort("10000+")
                .start();
        
        String webServerUrlStr = launcher.getServerDetails().getWebServerUrl();
        URI webServerUri = new URI(webServerUrlStr);
        
        assertEquals(launcher.getApplications(), ImmutableList.of());
        assertTrue(webServerUri.getPort() >= 10000 && webServerUri.getPort() < 10100, "port="+webServerUri.getPort()+"; uri="+webServerUri);
        HttpTestUtils.assertUrlReachable(webServerUrlStr);
    }
    
    @Test
    public void testCanDisableWebServerStartup() throws Exception {
        launcher = BrooklynLauncher.newInstance()
                .webconsole(false)
                .start();
        
        assertNull(launcher.getServerDetails().getWebServer());
        assertNull(launcher.getServerDetails().getWebServerUrl());
    }
    
    @Test
    public void testStartsAppInstance() throws Exception {
        launcher = BrooklynLauncher.newInstance()
                .webconsole(false)
                .application(new TestApplicationImpl())
                .start();
        
        assertOnlyApp(launcher, TestApplication.class);
    }
    
    @Test
    public void testStartsAppFromSpec() throws Exception {
        launcher = BrooklynLauncher.newInstance()
                .webconsole(false)
                .application(EntitySpecs.appSpec(TestApplication.class))
                .start();
        
        assertOnlyApp(launcher, TestApplication.class);
    }
    
    @Test
    public void testStartsAppFromBuilder() throws Exception {
        launcher = BrooklynLauncher.newInstance()
                .webconsole(false)
                .application(new ApplicationBuilder(EntitySpec.create(TestApplication.class)) {
                        @Override protected void doBuild() {
                        }})
                .start();
        
        assertOnlyApp(launcher, TestApplication.class);
    }
    
    @Test
    public void testStartsAppInSuppliedLocations() throws Exception {
        launcher = BrooklynLauncher.newInstance()
                .webconsole(false)
                .location("localhost")
                .application(new ApplicationBuilder(EntitySpec.create(TestApplication.class)) {
                        @Override protected void doBuild() {
                        }})
                .start();
        
        Application app = Iterables.find(launcher.getApplications(), Predicates.instanceOf(TestApplication.class));
        assertOnlyLocation(app, LocalhostMachineProvisioningLocation.class);
    }
    
    @Test
    public void testUsesSuppliedManagementContext() throws Exception {
        LocalManagementContext myManagementContext = new LocalManagementContext();
        launcher = BrooklynLauncher.newInstance()
                .webconsole(false)
                .managementContext(myManagementContext)
                .start();
        
        assertSame(launcher.getServerDetails().getManagementContext(), myManagementContext);
    }
    
    @Test
    public void testUsesSuppliedBrooklynProperties() throws Exception {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
        props.put("mykey", "myval");
        launcher = BrooklynLauncher.newInstance()
                .webconsole(false)
                .brooklynProperties(props)
                .start();
        
        assertEquals(launcher.getServerDetails().getManagementContext().getConfig().getFirst("mykey"), "myval");
    }

    @Test
    public void testUsesSupplementaryBrooklynProperties() throws Exception {
        launcher = BrooklynLauncher.newInstance()
                .webconsole(false)
                .brooklynProperties("mykey", "myval")
                .start();
        
        assertEquals(launcher.getServerDetails().getManagementContext().getConfig().getFirst("mykey"), "myval");
    }

    private void assertOnlyApp(BrooklynLauncher launcher, Class<? extends Application> expectedType) {
        assertEquals(launcher.getApplications().size(), 1, "apps="+launcher.getApplications());
        assertNotNull(Iterables.find(launcher.getApplications(), Predicates.instanceOf(TestApplication.class), null), "apps="+launcher.getApplications());
    }
    
    private void assertOnlyLocation(Application app, Class<? extends Location> expectedType) {
        assertEquals(app.getLocations().size(), 1, "locs="+app.getLocations());
        assertNotNull(Iterables.find(app.getLocations(), Predicates.instanceOf(LocalhostMachineProvisioningLocation.class), null), "locs="+app.getLocations());
    }
}

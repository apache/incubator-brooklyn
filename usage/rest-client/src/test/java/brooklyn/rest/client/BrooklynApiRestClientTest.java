package brooklyn.rest.client;

import static brooklyn.rest.BrooklynRestApiLauncher.startServer;

import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.entity.Application;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.BrooklynRestApiLauncherTest;
import brooklyn.rest.domain.LocationSummary;

@Test
public class BrooklynApiRestClientTest {

    private static final Logger log = LoggerFactory.getLogger(BrooklynApiRestClientTest.class);

    private ManagementContext manager;

    private BrooklynApi api;

    protected synchronized ManagementContext getManagementContext() throws Exception {
        if (manager == null) {
            manager = new LocalManagementContext();
            BrooklynRestApiLauncherTest.forceUseOfDefaultCatalogWithJavaClassPath(manager);
            BasicLocationRegistry.setupLocationRegistryForTesting(manager);
            BrooklynRestApiLauncherTest.enableAnyoneLogin(manager);
        }
        return manager;
    }

    @BeforeClass
    public void setUp() throws Exception {
        WebAppContext context;

        // running in source mode; need to use special classpath        
        context = new WebAppContext("src/test/webapp", "/");
        context.setExtraClasspath("./target/test-rest-server/");
        context.setAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT, getManagementContext());

        Server server = startServer(manager, context, "from WAR at " + context.getWar());

        api = new BrooklynApi("http://localhost:" + server.getConnectors()[0].getPort() + "/");
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() throws Exception {
        for (Application app : getManagementContext().getApplications()) {
            try {
                ((StartableApplication) app).stop();
            } catch (Exception e) {
                log.warn("Error stopping app " + app + " during test teardown: " + e);
            }
        }
        Entities.destroyAll(getManagementContext());
    }

    public void testListLocations() throws Exception {
        List<LocationSummary> locations = api.getLocationApi().list();
        log.info("locations are: "+locations);
    }

}

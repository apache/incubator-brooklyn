package brooklyn.rest;

import org.eclipse.jetty.server.Server;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import brooklyn.rest.testing.mocks.RestMockApp;
import brooklyn.util.ResourceUtils;

public class BrooklynRestApiLauncherTest {

    Server server = null;
    
    @AfterTest
    public void stopServer() throws Exception {
        if (server!=null) {
            server.stop();
            server = null;
        }
    }
    
    @Test
    public void testFilterStart() throws Exception {
        checkRestCatalogApplications(BrooklynRestApiLauncher.startRestResourcesViaFilter());
    }

    @Test
    public void testServletStart() throws Exception {
        checkRestCatalogApplications(BrooklynRestApiLauncher.startRestResourcesViaServlet());
    }

    @Test
    public void testWebAppStart() throws Exception {
        checkRestCatalogApplications(BrooklynRestApiLauncher.startRestResourcesViaWebXml());
    }

    private static void checkRestCatalogApplications(Server server) {
        String rootUrl = "http://localhost:"+server.getConnectors()[0].getLocalPort();
        String catalogApplications = new ResourceUtils(null).getResourceAsString(rootUrl+"/v1/catalog/applications");
        Assert.assertTrue(catalogApplications.contains(RestMockApp.class.getCanonicalName()));
    }
    
}

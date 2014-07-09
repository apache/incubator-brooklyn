package brooklyn.rest;

import org.eclipse.jetty.server.Server;
import org.testng.annotations.Test;

import brooklyn.rest.util.BrooklynRestResourceUtilsTest.SampleNoOpApplication;
import brooklyn.test.HttpTestUtils;

public class BrooklynRestApiLauncherTest extends BrooklynRestApiLauncherTestFixture {

    @Test
    public void testFilterStart() throws Exception {
        checkRestCatalogApplications(useServerForTest(BrooklynRestApiLauncher.startRestResourcesViaFilter()));
    }

    @Test
    public void testServletStart() throws Exception {
        checkRestCatalogApplications(useServerForTest(BrooklynRestApiLauncher.startRestResourcesViaServlet()));
    }

    @Test
    public void testWebAppStart() throws Exception {
        checkRestCatalogApplications(useServerForTest(BrooklynRestApiLauncher.startRestResourcesViaWebXml()));
    }
    
    private static void checkRestCatalogApplications(Server server) {
        enableAnyoneLogin(server);
        forceUseOfDefaultCatalogWithJavaClassPath(server);
        String rootUrl = "http://localhost:"+server.getConnectors()[0].getLocalPort();
        HttpTestUtils.assertContentContainsText(rootUrl+"/v1/catalog/applications", SampleNoOpApplication.class.getSimpleName());
    }
    
}

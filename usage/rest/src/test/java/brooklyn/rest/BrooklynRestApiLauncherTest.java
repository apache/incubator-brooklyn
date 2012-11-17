package brooklyn.rest;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.management.ManagementContext;
import brooklyn.rest.security.provider.AnyoneSecurityProvider;
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
        enableAnyoneLogin(server);
        String rootUrl = "http://localhost:"+server.getConnectors()[0].getLocalPort();
        String catalogApplications = new ResourceUtils(null).getResourceAsString(rootUrl+"/v1/catalog/applications");
        Assert.assertTrue(catalogApplications.contains(RestMockApp.class.getCanonicalName()));
    }

    public static void enableAnyoneLogin(Server server) {
        ManagementContext mgmt = (ManagementContext) ((ContextHandler)server.getHandler()).getAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT);
        ((BrooklynProperties)mgmt.getConfig()).put(BrooklynWebConfig.SECURITY_PROVIDER_CLASSNAME, 
                AnyoneSecurityProvider.class.getName());
    }
    
}

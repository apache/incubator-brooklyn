package brooklyn.rest.jsgui;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.rest.BrooklynRestApiLauncherTest;
import brooklyn.test.HttpTestUtils;

/** Convenience and demo for launching programmatically. */
public class BrooklynJavascriptGuiLauncherTest {

    Server server = null;
    
    @AfterMethod(alwaysRun=true)
    public void stopServer() throws Exception {
        if (server!=null) {
            ManagementContext mgmt = getManagementContextFromJettyServerAttributes(server);
            server.stop();
            if (mgmt!=null) Entities.destroyAll(mgmt);
            server = null;
        }
    }
    
    @Test
    public void testJavascriptWithoutRest() throws Exception {
        server = BrooklynJavascriptGuiLauncher.startJavascriptWithoutRest();
        checkUrlContains("/index.html", "Brooklyn");
    }

    @Test
    public void testJavascriptWithRest() throws Exception {
        server = BrooklynJavascriptGuiLauncher.startJavascriptAndRest();
        BrooklynRestApiLauncherTest.forceUseOfDefaultCatalogWithJavaClassPath(server);
        BrooklynRestApiLauncherTest.enableAnyoneLogin(server);
        checkUrlContains("/index.html", "Brooklyn");
        checkUrlContains("/v1/catalog/entities", "Tomcat");
    }

    protected void checkUrlContains(String path, String text) {
        String rootUrl = "http://localhost:"+server.getConnectors()[0].getLocalPort();
        HttpTestUtils.assertContentContainsText(rootUrl+path, text);
    }

    public static ManagementContext getManagementContextFromJettyServerAttributes(Server server) {
        ManagementContext mgmt = (ManagementContext) ((ContextHandler)server.getHandler()).getAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT);
        return mgmt;
    }

}

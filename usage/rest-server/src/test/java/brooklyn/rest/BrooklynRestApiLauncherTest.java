package brooklyn.rest;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.reflections.util.ClasspathHelper;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.rest.security.provider.AnyoneSecurityProvider;
import brooklyn.rest.util.BrooklynRestResourceUtilsTest.SampleNoOpApplication;
import brooklyn.test.HttpTestUtils;

public class BrooklynRestApiLauncherTest {

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

    protected Server useServerForTest(Server server) {
        if (this.server!=null) {
            Assert.fail("Test only meant for single server; already have "+server+" when checking "+server);
        } else {
            this.server = server;
        }
        return server;
    }
    
    private static void checkRestCatalogApplications(Server server) {
        enableAnyoneLogin(server);
        forceUseOfDefaultCatalogWithJavaClassPath(server);
        String rootUrl = "http://localhost:"+server.getConnectors()[0].getLocalPort();
        HttpTestUtils.assertContentContainsText(rootUrl+"/v1/catalog/applications", SampleNoOpApplication.class.getSimpleName());
    }

    public static void forceUseOfDefaultCatalogWithJavaClassPath(Server server) {
        ManagementContext mgmt = getManagementContextFromJettyServerAttributes(server);
        forceUseOfDefaultCatalogWithJavaClassPath(mgmt);
        
    }

    public static void forceUseOfDefaultCatalogWithJavaClassPath(ManagementContext manager) {
        // don't use any catalog.xml which is set
        ((BrooklynProperties)manager.getConfig()).put(ManagementContextInternal.BROOKLYN_CATALOG_URL, "");
        // sets URLs for a surefire
        ((LocalManagementContext)manager).setBaseClassPathForScanning(ClasspathHelper.forJavaClassPath());
        // this also works
//        ((LocalManagementContext)manager).setBaseClassPathForScanning(ClasspathHelper.forPackage("brooklyn"));
        // but this (near-default behaviour) does not
//        ((LocalManagementContext)manager).setBaseClassLoader(getClass().getClassLoader());
    }

    public static void enableAnyoneLogin(Server server) {
        ManagementContext mgmt = getManagementContextFromJettyServerAttributes(server);
        enableAnyoneLogin(mgmt);
    }
    public static void enableAnyoneLogin(ManagementContext mgmt) {
        ((BrooklynProperties)mgmt.getConfig()).put(BrooklynWebConfig.SECURITY_PROVIDER_CLASSNAME, 
                AnyoneSecurityProvider.class.getName());
    }

    public static ManagementContext getManagementContextFromJettyServerAttributes(Server server) {
        ManagementContext mgmt = (ManagementContext) ((ContextHandler)server.getHandler()).getAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT);
        return mgmt;
    }
    
}

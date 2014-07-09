package brooklyn.entity.webapp.jetty;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.AbstractWebAppFixtureIntegrationTest;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.location.basic.PortRanges;
import brooklyn.test.entity.TestApplication;

public class JettyWebAppFixtureIntegrationTest extends AbstractWebAppFixtureIntegrationTest {

    @DataProvider(name = "basicEntities")
    public Object[][] basicEntities() {
        TestApplication jettyApp = newTestApplication();
        Jetty6Server jetty = jettyApp.createAndManageChild(EntitySpec.create(Jetty6Server.class)
                .configure(Jetty6Server.HTTP_PORT, PortRanges.fromString(DEFAULT_HTTP_PORT)));
        
        return new JavaWebAppSoftwareProcess[][] {
                new JavaWebAppSoftwareProcess[] {jetty}
        };
    }

    // to be able to test on this class in Eclipse IDE
    @Override
    @Test(groups = "Integration", dataProvider = "entitiesWithWarAndURL")
    public void testWarDeployAndUndeploy(JavaWebAppSoftwareProcess entity, String war, String urlSubPathToWebApp,
            String urlSubPathToPageToQuery) {
        super.testWarDeployAndUndeploy(entity, war, urlSubPathToWebApp, urlSubPathToPageToQuery);
    }
    
    public static void main(String ...args) throws Exception {
        JettyWebAppFixtureIntegrationTest t = new JettyWebAppFixtureIntegrationTest();
        t.setUp();
        t.canStartAndStop((SoftwareProcess) t.basicEntities()[0][0]);
        t.shutdownApp();
        t.shutdownMgmt();
    }

}

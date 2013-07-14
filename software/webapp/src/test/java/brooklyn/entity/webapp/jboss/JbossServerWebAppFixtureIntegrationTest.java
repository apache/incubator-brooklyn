package brooklyn.entity.webapp.jboss;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.webapp.AbstractWebAppFixtureIntegrationTest;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.location.basic.PortRanges;
import brooklyn.test.entity.TestApplication;

public class JbossServerWebAppFixtureIntegrationTest extends AbstractWebAppFixtureIntegrationTest {

    @DataProvider(name = "basicEntities")
    public Object[][] basicEntities() {
        TestApplication jboss6App = newTestApplication();
        JBoss6Server jboss6 = jboss6App.createAndManageChild(EntitySpecs.spec(JBoss6Server.class)
                .configure(JBoss6Server.PORT_INCREMENT, PORT_INCREMENT));
        
        TestApplication jboss7App = newTestApplication();
        JBoss7Server jboss7 = jboss7App.createAndManageChild(EntitySpecs.spec(JBoss7Server.class)
                .configure(JBoss7Server.HTTP_PORT, PortRanges.fromString(DEFAULT_HTTP_PORT)));
        
        return new JavaWebAppSoftwareProcess[][] {
                new JavaWebAppSoftwareProcess[] {jboss6}, 
                new JavaWebAppSoftwareProcess[] {jboss7}
                
        };
    }

    // to be able to test on this class in Eclipse IDE
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void canStartAndStop(final SoftwareProcess entity) {
        super.canStartAndStop(entity);
    }
    
    public static void main(String ...args) throws Exception {
        JbossServerWebAppFixtureIntegrationTest t = new JbossServerWebAppFixtureIntegrationTest();
        t.setUp();
        t.testReportsServiceDownWhenKilled((SoftwareProcess) t.basicEntities()[0][0]);
        t.shutdownApp();
        t.shutdownMgmt();
    }

}

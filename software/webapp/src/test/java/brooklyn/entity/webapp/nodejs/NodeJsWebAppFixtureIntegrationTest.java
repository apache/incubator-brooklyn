package brooklyn.entity.webapp.nodejs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.AbstractWebAppFixtureIntegrationTest;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.location.basic.PortRanges;
import brooklyn.test.entity.TestApplication;

public class NodeJsWebAppFixtureIntegrationTest extends AbstractWebAppFixtureIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(NodeJsWebAppFixtureIntegrationTest.class);

    public static final String GIT_REPO_URL = "https://github.com/grkvlt/node-hello-world.git";
    public static final String APP_FILE = "app.js";
    public static final String APP_NAME = "node-hello-world";

    @DataProvider(name = "basicEntities")
    public Object[][] basicEntities() {
        TestApplication nodejsApp = newTestApplication();
        NodeJsWebAppService nodejs = nodejsApp.createAndManageChild(EntitySpec.create(NodeJsWebAppService.class)
                .configure(NodeJsWebAppService.HTTP_PORT, PortRanges.fromString(DEFAULT_HTTP_PORT))
                .configure("gitRepoUrl", GIT_REPO_URL)
                .configure("appFileName", APP_FILE)
                .configure("appName", APP_NAME));

        return new WebAppService[][] {
                new WebAppService[] { nodejs }
        };
    }

    public static void main(String ...args) throws Exception {
        NodeJsWebAppFixtureIntegrationTest t = new NodeJsWebAppFixtureIntegrationTest();
        t.setUp();
        t.testReportsServiceDownWhenKilled((SoftwareProcess) t.basicEntities()[0][0]);
        t.shutdownApp();
        t.shutdownMgmt();
    }

}

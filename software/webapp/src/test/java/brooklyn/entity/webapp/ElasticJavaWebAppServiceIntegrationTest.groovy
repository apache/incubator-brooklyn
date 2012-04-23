package brooklyn.entity.webapp

import org.testng.Assert
import org.testng.annotations.Test

import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.TestUtils
import brooklyn.test.entity.TestApplication

class ElasticJavaWebAppServiceIntegrationTest {

    @Test
    public void testFactory() {
        def app = new TestApplication();
        try {
            ElasticJavaWebAppService svc =
                new ElasticJavaWebAppService.Factory().newEntity(app, war: "classpath://hello-world.war");
            app.start([new LocalhostMachineProvisioningLocation()]);
            String url = svc.getAttribute(ElasticJavaWebAppService.ROOT_URL);
            Assert.assertNotNull(url);
            TestUtils.assertUrlHasText(url, "Hello");
        } finally {
            app.stop();
        }
    }
}

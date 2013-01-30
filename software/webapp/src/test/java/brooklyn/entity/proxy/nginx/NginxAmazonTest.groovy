package brooklyn.entity.proxy.nginx

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.config.BrooklynProperties
import brooklyn.entity.Application
import brooklyn.entity.basic.Entities
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.proxying.BasicEntitySpec
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.entity.webapp.WebAppService
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.test.HttpTestUtils
import brooklyn.location.basic.BasicLocationRegistry
import brooklyn.test.entity.TestApplication

/**
 * Test Nginx proxying a cluster of JBoss7Server entities on AWS for ENGR-1689.
 *
 * This test is a proof-of-concept for the Brooklyn demo application, with each
 * service running on a separate Amazon EC2 instance.
 */
public class NginxAmazonTest {
    private static final Logger LOG = LoggerFactory.getLogger(NginxAmazonTest.class)
    
    private TestApplication app
    private NginxController nginx
    private DynamicCluster cluster
    private Location loc

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        app = ApplicationBuilder.builder(TestApplication.class).manage();
        Entities.manage(app)
        
        BrooklynProperties props = BrooklynProperties.Factory.newDefault()
        props.put("brooklyn.location.jclouds.aws-ec2.image-id", "us-east-1/ami-2342a94a")

        loc = new BasicLocationRegistry(props).resolve("aws-ec2:us-east-1")
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app);
    }
    
    @Test(groups = "Live")
    public void testProvisionAwsCluster() {
        URL war = getClass().getClassLoader().getResource("swf-booking-mvc.war")
        assertNotNull war, "Unable to locate resource $war"
        
        cluster = app.createAndManageChild(BasicEntitySpec.newInstance(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, BasicEntitySpec.newInstance(JBoss7Server.class))
                .configure("initialSize", 2)
                .configure("httpPort", 8080)
                .configure(JavaWebAppService.ROOT_WAR, war.path));
        
        nginx = app.createAndManageChild(BasicEntitySpec.newInstance(NginxController.class)
                .configure("cluster", cluster)
                .configure("domain", "localhost")
                .configure("port", 8000)
                .configure("portNumberSensor", WebAppService.HTTP_PORT));

        app.start([ loc ])
        
        executeUntilSucceeds {
            // Nginx URL is available
            MachineLocation machine = nginx.locations.find { true }
            String url = "http://" + machine.address.hostName + ":" + nginx.getAttribute(NginxController.PROXY_HTTP_PORT) + "/swf-booking-mvc"
            HttpTestUtils.assertHttpStatusCodeEquals(url, 200)

            // Web-app URL is available
            cluster.members.each {
                HttpTestUtils.assertHttpStatusCodeEquals(it.getAttribute(JavaWebAppService.ROOT_URL) + "swf-booking-mvc", 200)
            }
        }

		nginx.stop()
    }
}

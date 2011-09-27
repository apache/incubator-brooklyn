package brooklyn.entity.proxy.nginx;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.EntityStartUtils
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Preconditions

/**
 * Test the operation of the {@link NginxController} class.
 *
 * TODO clarify test purpose
 */
public class NginxIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(NginxIntegrationTest.class)

    static { TimeExtras.init() }

    private Application app
    private NginxController nginx
    private DynamicCluster cluster

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = new TestApplication();
    }

    @AfterMethod(groups = "Integration")
    public void shutdown() {
        if (cluster != null && cluster.getAttribute(Startable.SERVICE_UP)) {
	        EntityStartUtils.stopEntity(cluster)
        }
        if (nginx != null && nginx.getAttribute(Startable.SERVICE_UP)) {
	        EntityStartUtils.stopEntity(nginx)
        }
    }

    /**
     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        def template = { Map properties -> new TomcatServer(properties) }
        URL war = getClass().getClassLoader().getResource("hello-world.war")
        Preconditions.checkState war != null, "Unable to locate resource $war"
        
        cluster = new DynamicCluster(owner:app, newEntity:template, initialSize:1, httpPort:7080)
        cluster.setConfig(TomcatServer.WAR, war.path)
        cluster.start([ new LocalhostMachineProvisioningLocation(count:1) ])
        nginx = new NginxController([
	            "owner" : app,
	            "cluster" : cluster,
	            "domain" : "localhost",
	            "port" : 8000,
	            "portNumberSensor" : TomcatServer.HTTP_PORT,
            ])
        nginx.start([ new LocalhostMachineProvisioningLocation(count:1) ])
        executeUntilSucceedsWithShutdown(nginx) {
            // Services are running
            assertTrue cluster.getAttribute(AbstractService.SERVICE_UP)
            assertTrue nginx.getAttribute(AbstractService.SERVICE_UP)
            cluster.members.each { assertTrue it.getAttribute(AbstractService.SERVICE_UP) }

            // Nginx URL is available
	        String url = nginx.getAttribute(NginxController.URL) + "hello-world"
            assertTrue urlRespondsWithStatusCode200(url)

            // Tomcat URL is available
            cluster.members.each {
	            assertTrue urlRespondsWithStatusCode200(it.getAttribute(TomcatServer.ROOT_URL) + "hello-world")
            }
        }
        cluster.stop()

        // Services have stopped
        assertFalse nginx.getAttribute(AbstractService.SERVICE_UP)
        assertFalse cluster.getAttribute(AbstractService.SERVICE_UP)
        cluster.members.each { assertFalse it.getAttribute(AbstractService.SERVICE_UP) }
    }
}

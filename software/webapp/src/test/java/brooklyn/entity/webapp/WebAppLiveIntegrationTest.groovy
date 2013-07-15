package brooklyn.entity.webapp

import static brooklyn.entity.basic.ConfigKeys.*
import static brooklyn.entity.webapp.jboss.JBoss6Server.*
import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

import brooklyn.config.BrooklynProperties
import brooklyn.entity.Application
import brooklyn.entity.basic.SoftwareProcess
import brooklyn.entity.basic.Entities
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.jboss.JBoss6Server
import brooklyn.entity.webapp.jboss.JBoss6ServerImpl
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.jboss.JBoss7ServerImpl
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.entity.webapp.tomcat.TomcatServerImpl
import brooklyn.location.Location
import brooklyn.location.basic.BasicLocationRegistry
import brooklyn.test.TestUtils
import brooklyn.test.entity.TestApplicationImpl
import brooklyn.util.internal.TimeExtras

/**
 * This tests that we can run jboss entity on AWS.
 */
public class WebAppLiveIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(WebAppLiveIntegrationTest.class)

    static { TimeExtras.init() }

    public static final int DEFAULT_HTTP_PORT = 8080
    public static final int DEFAULT_JMX_PORT = 32199

    // Port increment for JBoss 6.
    public static final int PORT_INCREMENT = 400

    // The parent application entity for these tests
    Application application = new TestApplicationImpl()

    Location loc

    /**
     * Provides instances of {@link TomcatServer}, {@link JBoss6Server} and {@link JBoss7Server} to the tests below.
     *
     * TODO combine the data provider here with the integration tests
     *
     * @see WebAppIntegrationTest#basicEntities()
     */
    @DataProvider(name = "basicEntities")
    public Object[][] basicEntities() {
        TomcatServer tomcat = new TomcatServerImpl(parent:application, httpPort:DEFAULT_HTTP_PORT, jmxPort:DEFAULT_JMX_PORT)
        JBoss6Server jboss6 = new JBoss6ServerImpl(parent:application, portIncrement:PORT_INCREMENT, jmxPort:DEFAULT_JMX_PORT)
        JBoss7Server jboss7 = new JBoss7ServerImpl(parent:application, httpPort:DEFAULT_HTTP_PORT, jmxPort:DEFAULT_JMX_PORT)
        return [ [ tomcat ], [ jboss6 ], [ jboss7 ] ]
    }

    private File getResource(String path) {
        return TestUtils.getResource(path, getClass().getClassLoader());
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        Entities.manage(application)

        BrooklynProperties props = BrooklynProperties.Factory.newDefault()
        props.put("brooklyn.location.jclouds.aws-ec2.imagel-id", "us-east-1/ami-2342a94a")
        props.put("brooklyn.location.jclouds.aws-ec2.image-owner", "411009282317")

        loc = new BasicLocationRegistry(props).resolve("aws-ec2:us-east-1")
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups = "Live", dataProvider="basicEntities")
    public void testStartsWebAppInAws(final SoftwareProcess entity) {
        entity.start([ loc ])
        executeUntilSucceedsWithShutdown(entity, abortOnError:false, timeout:75*SECONDS, useGroovyTruth:true) {
            assertTrue(entity.getAttribute(Startable.SERVICE_UP))
            true
        }
    }
}

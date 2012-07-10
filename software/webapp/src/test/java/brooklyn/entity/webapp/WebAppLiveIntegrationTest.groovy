package brooklyn.entity.webapp

import static brooklyn.entity.basic.ConfigKeys.*
import static brooklyn.entity.webapp.jboss.JBoss6Server.*
import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.io.File
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.jboss.JBoss6Server
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.location.Location
import brooklyn.location.basic.jclouds.CredentialsFromEnv
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.location.basic.jclouds.JcloudsLocationFactory
import brooklyn.test.TestUtils
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras
import brooklyn.entity.basic.SoftwareProcessEntity

/**
 * This tests that we can run jboss entity on AWS.
 */
public class WebAppLiveIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(WebAppLiveIntegrationTest.class)

    private static final String USEAST_REGION_NAME = "us-east-1"
    private static final String USEAST_IMAGE_ID = USEAST_REGION_NAME+"/"+"ami-2342a94a"
    private static final String EUWEST_REGION_NAME = "eu-west-1"
    private static final String EUWEST_IMAGE_ID = EUWEST_REGION_NAME+"/"+"ami-89def4fd"
    private static final String IMAGE_OWNER = "411009282317"

    static { TimeExtras.init() }

    public static final int DEFAULT_HTTP_PORT = 8080
    public static final int DEFAULT_JMX_PORT = 32199

    // Port increment for JBoss 6.
    public static final int PORT_INCREMENT = 400

    // The owner application entity for these tests
    Application application = new TestApplication()

    private JcloudsLocationFactory locFactory
    private JcloudsLocation loc

    /**
     * Provides instances of {@link TomcatServer}, {@link JBoss6Server} and {@link JBoss7Server} to the tests below.
     *
     * TODO combine the data provider here with the integration tests
     *
     * @see WebAppIntegrationTest#basicEntities()
     */
    @DataProvider(name = "basicEntities")
    public Object[][] basicEntities() {
        TomcatServer tomcat = [ owner:application, httpPort:DEFAULT_HTTP_PORT, jmxPort:DEFAULT_JMX_PORT ]
        JBoss6Server jboss6 = [ owner:application, portIncrement:PORT_INCREMENT, jmxPort:DEFAULT_JMX_PORT ]
        JBoss7Server jboss7 = [ owner:application, httpPort:DEFAULT_HTTP_PORT, jmxPort:DEFAULT_JMX_PORT ]
        return [ [ tomcat ], [ jboss6 ], [ jboss7 ] ]
    }

    private File getResource(String path) {
        return TestUtils.getResource(path, getClass().getClassLoader());
    }

    @BeforeMethod(groups = "Live")
    public void setUp() {
        File sshPrivateKey = getResource("jclouds/id_rsa.private")
        File sshPublicKey = getResource("jclouds/id_rsa.pub")
        CredentialsFromEnv creds = new CredentialsFromEnv("aws-ec2");
        locFactory = new JcloudsLocationFactory([
                provider:"aws-ec2",
                identity:creds.getIdentity(),
                credential:creds.getCredential(),
                sshPublicKey:sshPublicKey,
                sshPrivateKey:sshPrivateKey])

        loc = locFactory.newLocation(USEAST_REGION_NAME)
        loc.setTagMapping( [
                (JBoss6Server.class.getName()):[imageId:USEAST_IMAGE_ID,securityGroups:["brooklyn-all"]],
                (JBoss7Server.class.getName()):[imageId:USEAST_IMAGE_ID,securityGroups:["brooklyn-all"]],
                (TomcatServer.class.getName()):[imageId:USEAST_IMAGE_ID,securityGroups:["brooklyn-all"]]
                ])
    }

    @AfterMethod(groups = "Live")
    public void tearDown() {
        List<Exception> exceptions = []
        for (Location child : loc.getChildLocations()) {
            try {
                loc?.release(child)
            } catch (Exception e) {
                LOG.warn("Error releasing machine $child; continuing...", e)
                exceptions.add(e)
            }
        }
        if (exceptions) {
            throw exceptions.get(0)
        }
    }

    @Test(groups = [ "Live" ], dataProvider="basicEntities")
    public void testStartsWebAppInAws(final SoftwareProcessEntity entity) {
        entity.start([ loc ])
        executeUntilSucceedsWithShutdown(entity, abortOnError:false, timeout:75*SECONDS, useGroovyTruth:true) {
            assertTrue(entity.getAttribute(Startable.SERVICE_UP))
            true
        }
    }
}

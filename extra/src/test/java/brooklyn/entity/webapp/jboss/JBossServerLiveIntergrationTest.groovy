package brooklyn.entity.webapp.jboss

import static brooklyn.entity.basic.ConfigKeys.*
import static brooklyn.entity.webapp.jboss.JBoss6Server.*
import static java.util.concurrent.TimeUnit.*

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.ConfigKeys
import brooklyn.location.Location
import brooklyn.location.basic.aws.AWSCredentialsFromEnv
import brooklyn.location.basic.aws.AwsLocation
import brooklyn.location.basic.aws.AwsLocationFactory
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

/**
 * This tests that we can run jboss entity on AWS.
 */
public class JBossServerLiveIntergrationTest {
    private static final Logger logger = LoggerFactory.getLogger(brooklyn.entity.webapp.jboss.JBossServerLiveIntergrationTest.class)
    
    /** don't use 8080 since that is commonly used by testing software */
    private static final int HTTP_PORT = 8080
    
    private static final int PORT_INCREMENT = 400
    
    private static final int JMX_PORT = 32199
    private static final String USEAST_REGION_NAME = "us-east-1"
    private static final String USEAST_IMAGE_ID = USEAST_REGION_NAME+"/"+"ami-2342a94a"
    private static final String EUWEST_REGION_NAME = "eu-west-1"
    private static final String EUWEST_IMAGE_ID = EUWEST_REGION_NAME+"/"+"ami-89def4fd"
    private static final String IMAGE_OWNER = "411009282317"

    static { TimeExtras.init() }

    private AwsLocationFactory locFactory;
    private AwsLocation loc;
    private JBoss6Server jb

    private File getResource(String path) {
        URL resource = getClass().getClassLoader().getResource(path)
        return new File(resource.path)
    }

    @BeforeMethod(groups = "Live")
    public void setUp() {
        File sshPrivateKey = getResource("jclouds/id_rsa.private")
        File sshPublicKey = getResource("jclouds/id_rsa.pub")
        AWSCredentialsFromEnv creds = new AWSCredentialsFromEnv();
        locFactory = new AwsLocationFactory([
                identity:creds.getAWSAccessKeyId(), 
                credential:creds.getAWSSecretKey(),
                sshPublicKey:sshPublicKey,
                sshPrivateKey:sshPrivateKey])

        loc = locFactory.newLocation(USEAST_REGION_NAME)
        loc.setTagMapping([(JBoss6Server.class.getName()):[
                imageId:USEAST_IMAGE_ID,
                securityGroups:["brooklyn-all"],
                ]])
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

    @Test(groups = [ "Live" ])
    public void testStartsJBossInAws() {
        JBoss6Server jb = new JBoss6Server([ owner:new TestApplication(), httpPort:HTTP_PORT,
                config:[(SUGGESTED_PORT_INCREMENT):PORT_INCREMENT, (SUGGESTED_JMX_PORT):JMX_PORT] ])
        jb.start([ loc ])
        TestUtils.executeUntilSucceedsWithShutdown(jb, {
                Assert.assertTrue(jb.getAttribute(JBossServer.SERVICE_UP))
                true 
            }, abortOnError:false, timeout:75*SECONDS, useGroovyTruth:true)
    }
}

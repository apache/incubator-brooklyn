package brooklyn.entity.webapp.tomcat

import static brooklyn.entity.basic.ConfigKeys.*
import static brooklyn.entity.webapp.tomcat.TomcatServer.*

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.ConfigKeys
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.aws.AWSCredentialsFromEnv
import brooklyn.location.basic.aws.AwsLocation
import brooklyn.test.TestUtils
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.Repeater
import brooklyn.util.internal.TimeExtras

/**
 * This tests that we can run tomcat entity on AWS.
 */
public class TomcatServerLiveIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(brooklyn.entity.webapp.tomcat.TomcatServerLiveIntegrationTest.class)
    
    // FIXME Uses aled's ssh paths!
    
    /** don't use 8080 since that is commonly used by testing software */
    private static final int HTTP_PORT = 8080//40122
    private static final int SHUTDOWN_PORT = 31880//40123
    private static final int JMX_PORT = 32199//40124
    private static final String REGION_NAME = "us-east-1" // "eu-west-1"
    private static final String IMAGE_ID = REGION_NAME+"/"+"ami-0859bb61" // "ami-d7bb90a3"
    private static final String IMAGE_OWNER = "411009282317"
    private static final String SSH_PUBLIC_KEY_PATH = "/Users/aled/id_rsa.junit.pub"
    private static final String SSH_PRIVATE_KEY_PATH = "/Users/aled/id_rsa.junit.private"

    static { TimeExtras.init() }

    private AwsLocation loc;
    private Collection<SshMachineLocation> machines = []
    private TomcatServer tc
    
    @BeforeMethod(groups = "Live")
    public void setUp() {
        AWSCredentialsFromEnv creds = new AWSCredentialsFromEnv();
        loc = new AwsLocation([identity:creds.getAWSAccessKeyId(), credential:creds.getAWSSecretKey(), 
                providerLocationId:REGION_NAME])
        loc.setTagMapping([(TomcatServer.class.getName()):[
                imageId:IMAGE_ID,
                securityGroups:["everything"],
                sshPublicKey:new File(SSH_PUBLIC_KEY_PATH),
                sshPrivateKey:new File(SSH_PRIVATE_KEY_PATH),
                ]]) //, imageOwner:IMAGE_OWNER]])
    }
    
    @AfterMethod(groups = "Live")
    public void tearDown() {
        List<Exception> exceptions = []
        machines.each {
            try {
                loc?.release(it)
            } catch (Exception e) {
                LOG.warn("Error releasing machine $it; continuing...", e)
                exceptions.add(e)
            }
        }
        if (exceptions) {
            throw exceptions.get(0)
        }
    }

    @AfterMethod(groups = [ "Live" ])
    public void ensureTomcatIsShutDown() {
        Socket shutdownSocket = null;
        SocketException gotException = null;

        if (tc && tc.locations) {
            SshMachineLocation machine = tc.locations.iterator().next()
            InetAddress addr = machine.address
            
            boolean socketClosed = new Repeater("Checking Tomcat has shut down")
                .repeat({
    		            if (shutdownSocket) shutdownSocket.close();
    		            try { shutdownSocket = new Socket(addr, SHUTDOWN_PORT); }
    		            catch (SocketException e) { gotException = e; return; }
    		            gotException = null
    		        })
                .every(100, TimeUnit.MILLISECONDS)
                .until({ gotException })
                .limitIterationsTo(25)
    	        .run();

            if (socketClosed == false) {
                logger.error "Tomcat did not shut down - this is a failure of the last test run";
                logger.warn "I'm sending a message to the Tomcat shutdown port";
                OutputStreamWriter writer = new OutputStreamWriter(shutdownSocket.getOutputStream());
                writer.write("SHUTDOWN\r\n");
                writer.flush();
                writer.close();
                shutdownSocket.close();
                throw new Exception("Last test run did not shut down Tomcat")
            }
        }
    }

    @Test(groups = [ "Live" ])
    public void testStartsTomcatInAws() {
        TomcatServer tc = new TomcatServer([ owner:new TestApplication(), httpPort:HTTP_PORT, 
                config:[(SUGGESTED_SHUTDOWN_PORT):SHUTDOWN_PORT, (SUGGESTED_HTTP_PORT):HTTP_PORT, (SUGGESTED_HTTP_PORT):HTTP_PORT, 
                (SUGGESTED_JMX_PORT):JMX_PORT] ])
        tc.start([ loc ])
        TestUtils.executeUntilSucceedsWithFinallyBlock ([:],
                { Assert.assertTrue(tc.getAttribute(TomcatServer.SERVICE_UP)) }, 
                { tc.stop() })
    }
}

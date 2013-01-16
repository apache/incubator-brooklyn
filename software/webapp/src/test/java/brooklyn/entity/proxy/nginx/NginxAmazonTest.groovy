package brooklyn.entity.proxy.nginx

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Entities
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.proxying.BasicEntitySpec
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.entity.webapp.WebAppService
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.location.MachineLocation
import brooklyn.location.basic.jclouds.CredentialsFromEnv
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.location.basic.jclouds.JcloudsLocationFactory
import brooklyn.test.HttpTestUtils
import brooklyn.test.entity.TestApplication

/**
 * Test Nginx proxying a cluster of JBoss7Server entities on AWS for ENGR-1689.
 *
 * This test is a proof-of-concept for the Brooklyn demo application, with each
 * service running on a separate Amazon EC2 instance.
 */
public class NginxAmazonTest {
    private static final Logger LOG = LoggerFactory.getLogger(NginxAmazonTest.class)
    
    private static final String REGION_NAME = "us-east-1"
    private static final String IMAGE_ID = REGION_NAME+"/"+"ami-2342a94a"
    
    private JcloudsLocation loc
    private File sshPrivateKey
    private File sshPublicKey

    private TestApplication app
    private NginxController nginx
    private DynamicCluster cluster

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        app = ApplicationBuilder.builder(TestApplication.class).manage();
        
        URL resource = getClass().getClassLoader().getResource("jclouds/id_rsa.private")
        assertNotNull resource
        sshPrivateKey = new File(resource.path)
        resource = getClass().getClassLoader().getResource("jclouds/id_rsa.pub")
        assertNotNull resource
        sshPublicKey = new File(resource.path)
        
        CredentialsFromEnv creds = new CredentialsFromEnv("aws-ec2");
		JcloudsLocationFactory locationFactory = new JcloudsLocationFactory(provider:"aws-ec2",identity:creds.getIdentity(), credential:creds.getCredential())
        loc = locationFactory.newLocation(REGION_NAME)
    }
    
    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app);
    }
    
    @Test(groups = "Live")
    public void testProvisionAwsCluster() {
        URL war = getClass().getClassLoader().getResource("swf-booking-mvc.war")
        assertNotNull war, "Unable to locate resource $war"
        
        Map imageData = [
	            imageId:IMAGE_ID,
	            providerLocationId:REGION_NAME,
	            sshPublicKey:sshPublicKey,
	            sshPrivateKey:sshPrivateKey,
	            securityGroups:[ "everything" ]
            ]
        loc.setTagMapping([
            "brooklyn.entity.webapp.jboss.JBoss7Server":imageData,
            "brooklyn.entity.proxy.nginx.NginxController":imageData,
        ])
 
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

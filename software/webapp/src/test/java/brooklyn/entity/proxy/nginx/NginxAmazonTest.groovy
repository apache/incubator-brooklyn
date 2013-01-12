package brooklyn.entity.proxy.nginx

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.Entities
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.group.DynamicClusterImpl
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.entity.webapp.WebAppService
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.location.MachineLocation
import brooklyn.location.basic.jclouds.CredentialsFromEnv
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.location.basic.jclouds.JcloudsLocationFactory
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

    private Application app
    private NginxController nginx
    private DynamicCluster cluster

    @BeforeMethod(groups = "Live")
    public void setup() {
        app = new TestApplication();
    }

    @AfterMethod(groups = "Live")
    public void shutdown() {
        if (app != null) Entities.destroyAll(app);
    }
    
    @BeforeMethod(groups = "Live")
    public void setUp() {
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
    
    @Test(groups = "Live")
    public void testProvisionAwsCluster() {
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
 
        def template = { Map properties -> new JBoss7Server(properties) }
        
        cluster = new DynamicClusterImpl(parent:app, factory:template, initialSize:2, httpPort:8080 )
        URL war = getClass().getClassLoader().getResource("swf-booking-mvc.war")
        assertNotNull war, "Unable to locate resource $war"
        cluster.setConfig(JavaWebAppService.ROOT_WAR, war.path)
        cluster.start([ loc ])

        nginx = new NginxController([
                "parent" : app,
                "cluster" : cluster,
                "domain" : "localhost",
                "port" : 8000,
                "portNumberSensor" : WebAppService.HTTP_PORT,
            ])

        nginx.start([ loc ])
        
        executeUntilSucceeds {
            // Nginx URL is available
            MachineLocation machine = nginx.locations.find { true }
            String url = "http://" + machine.address.hostName + ":" + nginx.getAttribute(NginxController.HTTP_PORT) + "/swf-booking-mvc"
            assertTrue urlRespondsWithStatusCode200(url)

            // Web-app URL is available
            cluster.members.each {
                assertTrue urlRespondsWithStatusCode200(it.getAttribute(JavaWebAppService.ROOT_URL) + "swf-booking-mvc")
            }
        }

		nginx.stop()
    }
}

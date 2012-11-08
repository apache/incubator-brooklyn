package brooklyn.entity.proxy.nginx;

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.proxy.ProxySslConfig
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.entity.webapp.WebAppService
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

/**
 * Test the operation of the {@link NginxController} class.
 */
public class NginxHttpsSslIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(NginxHttpsSslIntegrationTest.class)

    static { TimeExtras.init() }

    private TestApplication app
    private NginxController nginx
    private DynamicCluster cluster

    private static final String WAR_URL = "classpath://hello-world.war";
    private static final String CERTIFICATE_URL = "classpath://ssl/certs/localhost/server.crt";
    private static final String KEY_URL = "classpath://ssl/certs/localhost/server.key";
    
    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = new TestApplication();
    }

    @AfterMethod(groups = "Integration", alwaysRun=true)
    public void shutdown() {
        if (app != null && Entities.isManaged(app)) app.stop();
    }

    /**
     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void testStartsWithGlobalSsl_withCertificateAndKeyCopy() {
        def template = { Map properties -> new JBoss7Server(properties) }
        cluster = new DynamicCluster(owner:app, factory:template, initialSize:1)
        cluster.setConfig(JavaWebAppService.ROOT_WAR, WAR_URL)
        
        ProxySslConfig ssl = new ProxySslConfig(
                certificateSourceUrl:CERTIFICATE_URL,
                keySourceUrl:KEY_URL);
        nginx = new NginxController(app,
                sticky: false,
                cluster: cluster,
                domain : "localhost",
                port: "8443+",
                ssl: ssl
            );
        
        app.start([ new LocalhostMachineProvisioningLocation() ])

        String url = nginx.getAttribute(WebAppService.ROOT_URL);
        if (!url.startsWith("https://")) Assert.fail("URL should be https: "+url);
        
        executeUntilSucceeds() {
            // Services are running
            assertTrue cluster.getAttribute(SoftwareProcessEntity.SERVICE_UP)
            cluster.members.each { assertTrue it.getAttribute(SoftwareProcessEntity.SERVICE_UP) }
            
            assertTrue nginx.getAttribute(SoftwareProcessEntity.SERVICE_UP)

            // Nginx URL is available
            assertTrue urlRespondsWithStatusCode200(url)

            // Web-server URL is available
            cluster.members.each {
                assertTrue urlRespondsWithStatusCode200(it.getAttribute(WebAppService.ROOT_URL))
            }
        }
        
        app.stop()

        // Services have stopped
        assertFalse nginx.getAttribute(SoftwareProcessEntity.SERVICE_UP)
        assertFalse cluster.getAttribute(SoftwareProcessEntity.SERVICE_UP)
        cluster.members.each { assertFalse it.getAttribute(SoftwareProcessEntity.SERVICE_UP) }
    }

    private String getFile(String file) {
           return new File(getClass().getResource("/" + file).getFile()).getAbsolutePath();
       }

    @Test(groups = "Integration")
    public void testStartsWithGlobalSsl_withPreinstalledCertificateAndKey() {
           def template = { Map properties -> new JBoss7Server(properties) }
           cluster = new DynamicCluster(owner:app, factory:template, initialSize:1)
           cluster.setConfig(JavaWebAppService.ROOT_WAR, WAR_URL)

           ProxySslConfig ssl = new ProxySslConfig(
                   certificateDestination: getFile("ssl/certs/localhost/server.crt"),
                   keyDestination: getFile("ssl/certs/localhost/server.key"));

           nginx = new NginxController(app,
                   sticky: false,
                   cluster: cluster,
                   domain : "localhost",
                   port: "8443+",
                   ssl: ssl
               );

           app.start([ new LocalhostMachineProvisioningLocation() ])

           String url = nginx.getAttribute(WebAppService.ROOT_URL);
           if (!url.startsWith("https://")) Assert.fail("URL should be https: "+url);

           executeUntilSucceeds() {
               // Services are running
               assertTrue cluster.getAttribute(SoftwareProcessEntity.SERVICE_UP)
               cluster.members.each { assertTrue it.getAttribute(SoftwareProcessEntity.SERVICE_UP) }

               assertTrue nginx.getAttribute(SoftwareProcessEntity.SERVICE_UP)

               // Nginx URL is available
               assertTrue urlRespondsWithStatusCode200(url)

               // Web-server URL is available
               cluster.members.each {
                   assertTrue urlRespondsWithStatusCode200(it.getAttribute(WebAppService.ROOT_URL))
               }
           }

           app.stop()

           // Services have stopped
           assertFalse nginx.getAttribute(SoftwareProcessEntity.SERVICE_UP)
           assertFalse cluster.getAttribute(SoftwareProcessEntity.SERVICE_UP)
           cluster.members.each { assertFalse it.getAttribute(SoftwareProcessEntity.SERVICE_UP) }
       }
}

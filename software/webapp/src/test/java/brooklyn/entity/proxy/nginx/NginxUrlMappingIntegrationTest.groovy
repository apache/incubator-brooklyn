package brooklyn.entity.proxy.nginx;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.entity.webapp.WebAppService
import brooklyn.entity.webapp.jboss.JBoss7ServerFactory
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.ResourceUtils
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Preconditions
import com.google.common.collect.Iterables

/**
 * Test the operation of the {@link NginxController} class, for URL mapped groups (two different pools).
 */
public class NginxUrlMappingIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(NginxUrlMappingIntegrationTest.class)

    static { TimeExtras.init() }

    private TestApplication app
    private NginxController nginx
    private DynamicCluster cluster

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = new TestApplication();
    }

    @AfterMethod(groups = "Integration", alwaysRun=true)
    public void shutdown() {
        app?.stop()
    }

    protected void checkExtraLocalhosts() {
        Set failedHosts = []
        ["localhost", "localhost1", "localhost2", "localhost3", "localhost4"].each { String host ->
            try {
                InetAddress i = InetAddress.getByName(host);
                byte[] b = ((Inet4Address)i).getAddress();
                if (b[0]!=127 || b[1]!=0 || b[2]!=0 || b[3]!=1) {
                    log.warn "Failed to resolve "+host+" (test will subsequently fail, but looking for more errors first; see subsequent failure for more info): wrong IP "+Arrays.asList(b)
                    failedHosts += host;
                }
            } catch (Exception e) {
                log.warn "Failed to resolve "+host+" (test will subsequently fail, but looking for more errors first; see subsequent failure for more info): "+e, e
                failedHosts += host;
            }
        }
        if (!failedHosts.isEmpty()) {
            fail("These tests (in "+this+") require special hostnames to map to 127.0.0.1, in /etc/hosts: "+failedHosts);
        }
    }
    
    @Test(groups = "Integration")
    public void startWithCoreClusterAndUrlMappedGroupWorksAndRespondsToScaleOut() {
        URL war = getClass().getClassLoader().getResource("hello-world.war")
        Preconditions.checkState war != null, "Unable to locate resource $war"
        
        checkExtraLocalhosts();
        
        def c0 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        c0.setConfig(JavaWebAppService.ROOT_WAR, war.path);
                
        nginx = new NginxController([
	            "owner" : app,
	            "cluster" : c0,
	            "domain" : "localhost",
	            "port" : 8000,
	            "portNumberSensor" : WebAppService.HTTP_PORT,
            ])
        
        def c1 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        c1.setConfig(JavaWebAppService.ROOT_WAR, war.path);
        def u1 = new UrlMapping(nginx, domain: "localhost1", target: c1);
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        
//        Entities.dumpInfo(app);
        Collection l1targets = u1.getAttribute(UrlMapping.TARGET_ADDRESSES)
        assertNotNull(l1targets);
        assertEquals(l1targets.size(), 1, "targets "+l1targets);

        //could also assert on:
//        nginx.getConfigFile()
        
        // check localhost1 resolves
        Entity l1jboss = Iterables.getOnlyElement(c1.getOwnedChildren());
        assertUrlHasText("http://localhost1:"+l1jboss.getAttribute(Attributes.HTTP_PORT), "Hello");
        // and forwards
        assertUrlHasText("http://localhost1:8000", "Hello");

        // and check core group resolves
        Entity c0jboss = Iterables.getOnlyElement(c0.getOwnedChildren());
        assertUrlHasText("http://localhost:"+c0jboss.getAttribute(Attributes.HTTP_PORT), "Hello");
        // and forwards
        assertUrlHasText("http://localhost:8000", "Hello");

        c1.resize(2);
        List l1jbosses = new ArrayList(c1.getOwnedChildren());
        l1jbosses.remove(l1jboss);
        Entity l1jboss2 = Iterables.getOnlyElement(l1jbosses);
        
        // now check jboss2 resolves
        String l1jboss2addr = "localhost1:"+l1jboss2.getAttribute(Attributes.HTTP_PORT);
        assertEventually( { new ResourceUtils(this).getResourceAsString("http://"+l1jboss2addr); } );
        assertUrlHasText("http://"+l1jboss2addr, "Hello");
        // and is included in nginx rules
        assertEventually( { 
            if (!nginx.getConfigFile().contains(l1jboss2addr))
                return new BooleanWithMessage(false, "could not find "+l1jboss2addr+" in:\n"+nginx.getConfigFile());
        } );
        
        app.stop()

        // Services have stopped
        assertFalse nginx.getAttribute(SoftwareProcessEntity.SERVICE_UP)
        assertFalse c0.getAttribute(SoftwareProcessEntity.SERVICE_UP)
        assertFalse l1jboss.getAttribute(SoftwareProcessEntity.SERVICE_UP)
    }
    
    @Test(groups = "Integration")
    public void startWithUrlMappedGroupButNoCoreCluster() {
        URL war = getClass().getClassLoader().getResource("hello-world.war")
        Preconditions.checkState war != null, "Unable to locate resource $war"
        
        checkExtraLocalhosts();
                        
        nginx = new NginxController([
                "owner" : app,
                "port" : 8000,
            ])
        
        def c1 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        c1.setConfig(JavaWebAppService.ROOT_WAR, war.path);
        def u1 = new UrlMapping(nginx, domain: "localhost1", target: c1);
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        
//        Entities.dumpInfo(app);
        Collection l1targets = u1.getAttribute(UrlMapping.TARGET_ADDRESSES)
        assertNotNull(l1targets);
        assertEquals(l1targets.size(), 1, "targets "+l1targets);

        String webPage;
        
        // check localhost1 resolves
        Entity l1jboss = Iterables.getOnlyElement(c1.getOwnedChildren());
        webPage = new ResourceUtils(this).getResourceAsString("http://localhost1:"+l1jboss.getAttribute(Attributes.HTTP_PORT));
        assertTrue(webPage.contains("Hello"), webPage);
        // and forwards
        webPage = new ResourceUtils(this).getResourceAsString("http://localhost1:8000");
        assertTrue(webPage.contains("Hello"), webPage);

        // and check core group does _not_ resolve
        assertFails { new ResourceUtils(this).getResourceAsString("http://localhost:8000"); }

        app.stop()

        // Services have stopped
        assertFalse nginx.getAttribute(SoftwareProcessEntity.SERVICE_UP)
        assertFalse l1jboss.getAttribute(SoftwareProcessEntity.SERVICE_UP)
    }

}

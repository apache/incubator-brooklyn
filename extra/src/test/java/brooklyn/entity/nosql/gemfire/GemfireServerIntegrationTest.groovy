package brooklyn.entity.nosql.gemfire

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import org.testng.annotations.AfterMethod

import brooklyn.entity.Application
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.trait.Startable
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractService

import com.gemstone.gemfire.cache.Region
import com.gemstone.gemfire.cache.client.ClientCacheFactory
import com.gemstone.gemfire.cache.client.ClientCache

/**
 * This tests the operation of the {@link GemfireServer} entity.
 * 
 * TODO clarify test purpose
 */
public class GemfireServerIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(GemfireServerIntegrationTest.class)

    // TODO: Make these machine independent
    private String installDir = "/Users/sam/code/cloudsoft/brooklyn/gemfire"
    private String jarFile = "/Users/sam/aksdb/trunk/sandbox/spring-booking-demo-bixby/" +
            "com.cloudsoftcorp.demo.bixby.booking.webapp/src/main/webapp/WEB-INF/lib/" +
            "com.cloudsoftcorp.demo.bixby.bookingsvc.api.jar"

    // if installDir is set machine independently then gemfireLicense.zip can be deleted from resources
    private static final String licenseFile = "gemfireLicense.zip"
    private static final String euCache = "eu/cache.xml"
    private static final String clientCache = "eu/client-cache.xml"

    private Application app = new TestApplication()

    /** Returns the absolute path to requested resource that should live in brooklyn/entity/nosql/gemfire */
    private static String pathTo(String resource) {
        URL url = GemfireServerIntegrationTest.class.getResource(resource)
        assertNotNull(url, "Couldn't find $resource, aborting")
        return url.path;
    }

    @BeforeMethod
    public void setup() {
        app = new TestApplication()
    }

    private final List<Entity> createdEntities = []
    @AfterMethod(alwaysRun=true)
    public void killStartedEntities() {
        createdEntities.each { it.stop() }
        createdEntities.clear()
    }

    /** Creates server and returns it after adding it to the createdEntities list */
    private GemfireServer createGemfireServer(Application owner, String installDir, String license,
            String config, String jarFile=null) {
        GemfireServer entity = new GemfireServer(owner: owner)
        entity.setConfig(GemfireServer.SUGGESTED_INSTALL_DIR, installDir)
        entity.setConfig(GemfireServer.LICENSE, license);
        entity.setConfig(GemfireServer.CONFIG_FILE, config)
        if (jarFile != null) {
            entity.setConfig(GemfireServer.JAR_FILE, jarFile)
        }
        createdEntities.push(entity)
        return entity
    }

    @Test(groups=["Integration"])
    public void testGemfireStartsAndStops() {
        Entity entity = createGemfireServer(app, installDir, pathTo(licenseFile), pathTo(euCache))
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceedsWithShutdown(entity) {
            assertTrue entity.getAttribute(Startable.SERVICE_UP)
        }
        assertFalse entity.getAttribute(JavaApp.SERVICE_UP)
    }

    @Test(groups=["Integration"])
    public void testRegionInsertRetrieve() {

        Entity entity = createGemfireServer(app, installDir, pathTo(licenseFile), pathTo(euCache))
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])

        executeUntilSucceeds(timeout: 15000) {
            assertTrue entity.getAttribute(Startable.SERVICE_UP)
        }

        ClientCache cache = new ClientCacheFactory().set("cache-xml-file", pathTo(clientCache)).create()
        Region region = cache.getRegion("integrationTests")
        region.put("life, etc.", 42)

        // whoyougonnacall set in euCache, life etc. set above
        assertEquals region.get("whoyougonnacall"), "ghostbusters!"
        assertEquals region.get("life, etc."), 42

        cache.close()
        entity.stop()

    }

    @Test(groups=["Integration"], enabled=false)
    public void testInOneRegionOutAnother() {
        Entity server = createGemfireServer(app, installDir, pathTo(licenseFile), pathTo(euCache))
        server.start([ new LocalhostMachineProvisioningLocation(name:'london') ])

        // Invoke create region
        // Create client cache
        // Insert keyval pair
        // Create second client cache in same region
        // Retrieve keyval pair
        
    }
}

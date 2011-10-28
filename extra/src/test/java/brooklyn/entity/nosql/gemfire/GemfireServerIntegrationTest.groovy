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

import org.testng.annotations.AfterMethod
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
    private String installDir = "/Users/aled/eclipse-workspaces/cloudsoft/brooklyn/gemfire"
    private String jarFile = "/Users/aled/eclipse-workspaces/bixby-demo/com.cloudsoftcorp.sample.booking.webapp/src/main/webapp/WEB-INF/lib/com.cloudsoftcorp.sample.booking.svc.api_3.2.0.v20110317-295-10281.jar"

    // if installDir is set machine independently then gemfireLicense.zip can be deleted from resources
    private static final String licenseFile = "gemfireLicense.zip"
    private static final String euCache = "eu/cache.xml"
    private static final String clientCache = "eu/client-cache.xml"

    private Application app

    /** Returns the absolute path to requested resource that should live in brooklyn/entity/nosql/gemfire */
    private static String pathTo(String resource) {
        URL url = GemfireServerIntegrationTest.class.getResource(resource)
        assertNotNull(url, "Couldn't find $resource, aborting")
        return url.path;
    }

    @BeforeMethod(groups = [ "Integration" ])
    public void setUp() {
    	app = new TestApplication()
    }

    private final List<Entity> createdEntities = []
    @AfterMethod(alwaysRun=true)
    public void callStopOnAllStartedEntities() {
        createdEntities.each { it.stop() }
        createdEntities.clear()
    }
	
	private GemfireServer createGemfireServer(Application owner) {
		GemfireServer server = createGemfireServer(owner, installDir, pathTo(licenseFile), pathTo(configFile))
		server.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
		executeUntilSucceeds() {
			assertTrue server.getAttribute(Startable.SERVICE_UP)
		}
		return server
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

    @Test(groups = [ "Integration" ])
    public void testGemfireStartsAndStops() {
        Application app = new TestApplication()
        Entity entity = createGemfireServer(app, installDir, pathTo(licenseFile), pathTo(euCache))
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceedsWithShutdown(entity) {
            assertTrue entity.getAttribute(Startable.SERVICE_UP)
        }
        assertFalse entity.getAttribute(JavaApp.SERVICE_UP)
    }
	
	@Test(groups = [ "Integration" ])
	public void testRegionSensor() {
		Application app = new TestApplication()
		GemfireServer entity = createGemfireServer(app)
		
		executeUntilSucceeds() {
			Collection<String> regions = entity.getAttribute(GemfireServer.REGION_LIST)
			assertEquals ( regions, Arrays.asList("/trades") )
			
		}
	}
	
	//TODO rewrite these with generic a/b/c/d or 1/2/3/4, then use indexing to test add/remove, ie. add(3) -> add 1/2/3, etc.
	@Test(groups = [ "Integration" ])
	public void testAddRegions() {
		Application app = new TestApplication()
		GemfireServer entity = createGemfireServer(app)
		
		Collection<String> regionsToAdd = Arrays.asList("Foo/Bar", 
				"Fizz", "/Fizz/Buzz",
				"/Buzz",
				"/Tom/Dick/Harry"),
			expectedRegions = Arrays.asList("/Foo", "/Foo/Bar", 
				"/Fizz", "/Fizz/Buzz",
				"/Buzz", 
				"/Tom", "/Tom/Dick", "/Tom/Dick/Harry")
		entity.addRegions(regionsToAdd)
		
		executeUntilSucceeds() {
			Collection<String> regions = entity.getAttribute(GemfireServer.REGION_LIST)
			assertTrue( regions.containsAll(expectedRegions), "Expected = $expectedRegions, actual = $regions" )
		}
	}
	
	@Test(groups = [ "Integration" ])
	public void testRemoveRegions() {
		Application app = new TestApplication()
		GemfireServer entity = createGemfireServer(app)
		
		Collection<String> regionsToAdd = Arrays.asList("Foo/Bar", 
				"Fizz", "/Fizz/Buzz",
				"/Buzz",
				"/Tom/Dick/Harry"),
//			expectedRegions = Arrays.asList("/Foo", "/Foo/Bar", 
//				"/Fizz", "/Fizz/Buzz",
//				"/Buzz", 
//				"/Tom", "/Tom/Dick", "/Tom/Dick/Harry"),
			regionsToRemove = Arrays.asList("/Foo/Bar", 
				"/Fizz", 
				"Buzz",
				"/Tom/Dick"), 
			regionsActuallyRemoved = Arrays.asList("/Foo/Bar", 
				"/Fizz", "/Fizz/Buzz",
				"/Buzz", 
				"/Tom/Dick", "/Tom/Dick/Harry")
		
		
		entity.addRegions(regionsToAdd)
		entity.removeRegions(regionsToRemove)
		
		executeUntilSucceeds() {
			Collection<String> regions = entity.getAttribute(GemfireServer.REGION_LIST)
			assertTrue (regions.disjoint(regionsActuallyRemoved), "Expected removed = $regionsActuallyRemoved, actual = $regions")
		}
	}

    @Test(groups=["Integration"])
    public void testJarDeploy() {
//        entity.setConfig(GemfireServer.JAR_FILE, jarFile)
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

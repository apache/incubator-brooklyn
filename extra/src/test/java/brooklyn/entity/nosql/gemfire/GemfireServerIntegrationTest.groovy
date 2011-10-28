package brooklyn.entity.nosql.gemfire

import static brooklyn.test.TestUtils.*

import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.trait.Startable
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication

import org.testng.annotations.AfterMethod
import brooklyn.entity.Entity

/**
 * This tests the operation of the {@link GemfireServer} entity.
 * 
 * TODO clarify test purpose
 */
public class GemfireServerIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(GemfireServerIntegrationTest.class)
    
    private String installDir = "/Users/aled/eclipse-workspaces/cloudsoft/brooklyn/gemfire"
    private String jarFile = "/Users/aled/eclipse-workspaces/bixby-demo/com.cloudsoftcorp.sample.booking.webapp/src/main/webapp/WEB-INF/lib/com.cloudsoftcorp.sample.booking.svc.api_3.2.0.v20110317-295-10281.jar"

    private static final String licenseFile = "gemfireLicense.zip"
    private static final String configFile = "eu/cache.xml"

    /** Returns the absolute path to requested resource that should live in brooklyn/entity/nosql/gemfire */
    private static String pathTo(String resource) {
        URL url = GemfireServerIntegrationTest.class.getResource(resource)
        assertNotNull(url, "Couldn't find $resource, aborting")
        return url.path;
    }

    @BeforeMethod(groups = [ "Integration" ])
    public void setUp() {
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
    private GemfireServer createGemfireServer(Application owner, String installDir, String license, String config) {
        GemfireServer entity = new GemfireServer(owner: owner)
        entity.setConfig(GemfireServer.SUGGESTED_INSTALL_DIR, installDir)
        entity.setConfig(GemfireServer.LICENSE, license);
        entity.setConfig(GemfireServer.CONFIG_FILE, config)
        createdEntities.push(entity)
        return entity
    }

    @Test(groups = [ "Integration" ])
    public void testGemfireStartsAndStops() {
        Application app = new TestApplication()
        Entity entity = createGemfireServer(app, installDir, pathTo(licenseFile), pathTo(configFile))
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
}

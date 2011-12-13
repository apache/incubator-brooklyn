package brooklyn.entity.nosql.gemfire

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.basic.legacy.JavaApp
import brooklyn.entity.trait.Startable
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication

import com.gemstone.gemfire.cache.Region
import com.gemstone.gemfire.cache.client.ClientCache
import com.gemstone.gemfire.cache.client.ClientCacheFactory
import com.gemstone.gemfire.cache.client.ClientRegionShortcut
import com.gemstone.gemfire.cache.GemFireCache

public class GemfireServerIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(GemfireServerIntegrationTest.class)

    private static final String API_JAR = pathTo("gemfire-api.jar")
    private static final String LICENSE_FILE = pathTo("gemfireLicense.zip")
    private static final String EU_CACHE = pathTo("eu/cache.xml")
    private static final String CLIENT_CACHE = pathTo("eu/client-cache.xml")

    private Application app

    /** Returns the absolute path to requested resource in the classpath  */
    private static String pathTo(String resource) {
        URL url = GemfireServerIntegrationTest.class.getResource("/" + resource)
        assertNotNull(url, "Couldn't find $resource, aborting")
        return url.path;
    }

    @BeforeMethod(groups=["Integration"])
    public void setUp() {
        app = new TestApplication()
    }

    @BeforeMethod(alwaysRun=true, groups=["Integration"])
    public void ensureGemfireInstallDirExists() {
        File api = new File(API_JAR)
        File license = new File(LICENSE_FILE)
        if (!api.exists()) {
            fail("Expected to find Gemfire API jar at $api")
        } else if (!license.exists()) {
            fail("Expected to find Gemfire license file at $license")
        }
    }

    private final List<Entity> createdEntities = []

    @AfterMethod(alwaysRun=true)
    public void callStopOnAllStartedEntities() {
        createdEntities.each { it.stop() }
        createdEntities.clear()
    }

    /** Creates server and returns it after adding it to the createdEntities list  */
    private GemfireServer createGemfireServer(
            Application owner, String apiJar, String license, String config, String jarFile = null) {
        GemfireServer entity = new GemfireServer(owner: owner)
        entity.setConfig(GemfireServer.SUGGESTED_API_JAR, apiJar)
        entity.setConfig(GemfireServer.LICENSE, license);
        entity.setConfig(GemfireServer.CONFIG_FILE, config)
        if (jarFile != null) {
            entity.setConfig(GemfireServer.JAR_FILE, jarFile)
        }
        createdEntities.add(entity)
        return entity
    }

    /** Creates gemfire instance owned by given owner, defaults to app  */
    private GemfireServer createAndStartGemfireServer(Application owner=app) {
        GemfireServer server = createGemfireServer(owner, API_JAR, LICENSE_FILE, EU_CACHE)
        server.start([new LocalhostMachineProvisioningLocation(name: 'london')])
        executeUntilSucceeds(timeout: 15000) {
            assertTrue server.getAttribute(Startable.SERVICE_UP)
        }
        return server
    }

    @Test(groups=["Integration"])
    public void testGemfireStartsAndStops() {
        GemfireServer entity = createAndStartGemfireServer()
        entity.stop();
        executeUntilSucceeds(timeout: 10000) {
            assertFalse entity.getAttribute(JavaApp.SERVICE_UP)
        }
    }

    @Test(groups=["Integration"], dependsOnMethods=["testGemfireStartsAndStops"])
    public void testRegionSensor() {
        GemfireServer entity = createAndStartGemfireServer()
        executeUntilSucceeds() {
            Collection<String> regions = entity.getAttribute(GemfireServer.REGION_LIST)
            assertEquals(regions, Arrays.asList("/integrationTests"))
        }
    }

    //TODO rewrite these with generic a/b/c/d or 1/2/3/4, then use indexing to test add/remove, ie. add(3) -> add 1/2/3, etc.
    @Test(groups=["Integration"], dependsOnMethods=["testGemfireStartsAndStops"])
    public void testAddRegions() {
        GemfireServer entity = createAndStartGemfireServer()

        Collection<String> regionsToAdd = Arrays.asList("Foo/Bar",
                "Fizz", "/Fizz/Buzz",
                "/Buzz",
                "/Tom/Dick/Harry"),
        expectedRegions = Arrays.asList("/integrationTests",
                "/Foo", "/Foo/Bar",
                "/Fizz", "/Fizz/Buzz",
                "/Buzz",
                "/Tom", "/Tom/Dick", "/Tom/Dick/Harry")
        entity.addRegions(regionsToAdd)

        executeUntilSucceeds(timeout: 5000) {
            Collection<String> regions = entity.getAttribute(GemfireServer.REGION_LIST)
            assertTrue(expectedRegions.containsAll(regions), "\nExpected: $expectedRegions\nActual: $regions");
        }
    }

    @Test(groups=["Integration"], dependsOnMethods=["testGemfireStartsAndStops"])
    public void testRemoveRegions() {
        GemfireServer entity = createAndStartGemfireServer()

        Collection<String> regionsToAdd = Arrays.asList("Foo/Bar",
                "Fizz", "/Fizz/Buzz",
                "/Buzz",
                "/Tom/Dick/Harry"),
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
            assertTrue(regions.disjoint(regionsActuallyRemoved), "Expected removed = $regionsActuallyRemoved, actual = $regions")
        }
    }

    @Test(groups=["Integration"], dependsOnMethods=["testGemfireStartsAndStops"])
    public void testRegionInsertRetrieve() {
        GemfireServer entity = createAndStartGemfireServer()

        ClientCache cache = new ClientCacheFactory().set("cache-xml-file", CLIENT_CACHE).create()
        Region region = cache.getRegion("integrationTests")
        assertEquals region.get("whoyougonnacall"), "ghostbusters!" // whoyougonnacall set in EU_CACHE

        entity.addRegions(Arrays.asList("adams"))
        cache.createClientRegionFactory(ClientRegionShortcut.CACHING_PROXY_OVERFLOW).create("adams")

        executeUntilSucceeds() {
            Collection<String> serverRegions = entity.getAttribute(GemfireServer.REGION_LIST)
            assertTrue(serverRegions.contains("/adams"), "Expected \"/adam\" in server regions: $serverRegions")
            region = cache.getRegion("/adams")
            Collection<String> clientRegions = regionList(cache)
            assertNotNull(region, "Failed to get /adams from client cache regions: ${clientRegions}")
        }

        region.put("life, etc.", 42)
        assertEquals region.get("life, etc."), 42

        cache.close()
        entity.stop()

    }

    /**
     * Returns a list containing all the regions in the given cache
     */
    public static List<String> regionList(GemFireCache cache) {
        List<String> regions = new LinkedList<String>();
        for (Region<?, ?> rootRegion: cache.rootRegions()) {
            regions.add(rootRegion.getFullPath());
            for (Region<?, ?> region: rootRegion.subregions(true)) {
                regions.add(region.getFullPath());
            }
        }
        return regions;
    }

    @Test(groups=["Integration"], enabled = false)
    public void testInOneCacheOutAnother() {
        // Invoke create region
        // Create client cache
        // Insert keyval pair
        // Create second client cache in same region
        // Retrieve keyval pair
    }

}

package brooklyn.entity.brooklynnode;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.BasicApplicationImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.http.JsonFunctions;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.PortRanges;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class BrooklynNodeIntegrationTest {

    // TODO Need test for copying/setting classpath
    
    private static final File BROOKLYN_PROPERTIES_PATH = new File(System.getProperty("user.home")+"/.brooklyn/brooklyn.properties");
    private static final File BROOKLYN_PROPERTIES_BAK_PATH = new File(BROOKLYN_PROPERTIES_PATH+".test.bak");
    
    private File pseudoBrooklynPropertiesFile;
    private File pseudoBrooklynCatalogFile;
    private List<LocalhostMachineProvisioningLocation> locs;
    private TestApplication app;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        pseudoBrooklynPropertiesFile = File.createTempFile("brooklynnode-test", ".properties");
        pseudoBrooklynPropertiesFile.delete();
        
        pseudoBrooklynCatalogFile = File.createTempFile("brooklynnode-test", ".catalog");
        pseudoBrooklynCatalogFile.delete();
        
        locs = ImmutableList.of(new LocalhostMachineProvisioningLocation());
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
        if (pseudoBrooklynPropertiesFile != null) pseudoBrooklynPropertiesFile.delete();
        if (pseudoBrooklynCatalogFile != null) pseudoBrooklynCatalogFile.delete();
    }
    
    @Test(groups="Integration")
    public void testCanStartAndStop() throws Exception {
        BrooklynNode brooklynNode = app.createAndManageChild(EntitySpec.create(BrooklynNode.class));
        app.start(locs);
        
        EntityTestUtils.assertAttributeEqualsEventually(brooklynNode, BrooklynNode.SERVICE_UP, true);
        
        brooklynNode.stop();
        EntityTestUtils.assertAttributeEquals(brooklynNode, BrooklynNode.SERVICE_UP, false);
    }
    
    @Test(groups="Integration")
    public void testCanStartAndStopWithoutAuthentication() throws Exception {
        BrooklynNode brooklynNode = app.createAndManageChild(EntitySpec.create(BrooklynNode.class)
                .configure(BrooklynNode.NO_WEB_CONSOLE_AUTHENTICATION, true)
                .configure(BrooklynNode.MANAGEMENT_USER, null));
        app.start(locs);
        
        EntityTestUtils.assertAttributeEqualsEventually(brooklynNode, BrooklynNode.SERVICE_UP, true);
        
        brooklynNode.stop();
        EntityTestUtils.assertAttributeEquals(brooklynNode, BrooklynNode.SERVICE_UP, false);
    }
    

    @Test(groups="Integration")
    public void testSetsGlobalBrooklynPropertiesFromContents() throws Exception {
        BrooklynNode brooklynNode = app.createAndManageChild(EntitySpec.create(BrooklynNode.class)
                .configure(BrooklynNode.BROOKLYN_GLOBAL_PROPERTIES_REMOTE_PATH, pseudoBrooklynPropertiesFile.getAbsolutePath())
                .configure(BrooklynNode.BROOKLYN_GLOBAL_PROPERTIES_CONTENTS, "abc=def"));
        app.start(locs);
        
        assertEquals(Files.readLines(pseudoBrooklynPropertiesFile, Charsets.UTF_8), ImmutableList.of("abc=def"));
    }
    
    @Test(groups="Integration")
    public void testSetsLocalBrooklynPropertiesFromContents() throws Exception {
        BrooklynNode brooklynNode = app.createAndManageChild(EntitySpec.create(BrooklynNode.class)
                .configure(BrooklynNode.BROOKLYN_LOCAL_PROPERTIES_REMOTE_PATH, pseudoBrooklynPropertiesFile.getAbsolutePath())
                .configure(BrooklynNode.BROOKLYN_LOCAL_PROPERTIES_CONTENTS, "abc=def"));
        app.start(locs);
        
        assertEquals(Files.readLines(pseudoBrooklynPropertiesFile, Charsets.UTF_8), ImmutableList.of("abc=def"));
    }
    
    @Test(groups="Integration")
    public void testSetsBrooklynPropertiesFromUri() throws Exception {
        File brooklynPropertiesSourceFile = File.createTempFile("brooklynnode-test", ".properties");
        Files.write("abc=def", brooklynPropertiesSourceFile, Charsets.UTF_8);
        
        BrooklynNode brooklynNode = app.createAndManageChild(EntitySpec.create(BrooklynNode.class)
                .configure(BrooklynNode.BROOKLYN_GLOBAL_PROPERTIES_REMOTE_PATH, pseudoBrooklynPropertiesFile.getAbsolutePath())
                .configure(BrooklynNode.BROOKLYN_GLOBAL_PROPERTIES_URI, brooklynPropertiesSourceFile.toURI().toString()));
        app.start(locs);
        
        assertEquals(Files.readLines(pseudoBrooklynPropertiesFile, Charsets.UTF_8), ImmutableList.of("abc=def"));
    }
    
    @Test(groups="Integration")
    public void testSetsBrooklynCatalogFromContents() throws Exception {
        BrooklynNode brooklynNode = app.createAndManageChild(EntitySpec.create(BrooklynNode.class)
                .configure(BrooklynNode.BROOKLYN_CATALOG_REMOTE_PATH, pseudoBrooklynCatalogFile.getAbsolutePath())
                .configure(BrooklynNode.BROOKLYN_CATALOG_CONTENTS, "<catalog/>"));
        app.start(locs);
        
        assertEquals(Files.readLines(pseudoBrooklynCatalogFile, Charsets.UTF_8), ImmutableList.of("<catalog/>"));
    }
    
    @Test(groups="Integration")
    public void testSetsBrooklynCatalogFromUri() throws Exception {
        File brooklynCatalogSourceFile = File.createTempFile("brooklynnode-test", ".catalog");
        Files.write("abc=def", brooklynCatalogSourceFile, Charsets.UTF_8);
        
        BrooklynNode brooklynNode = app.createAndManageChild(EntitySpec.create(BrooklynNode.class)
                .configure(BrooklynNode.BROOKLYN_CATALOG_REMOTE_PATH, pseudoBrooklynCatalogFile.getAbsolutePath())
                .configure(BrooklynNode.BROOKLYN_CATALOG_URI, brooklynCatalogSourceFile.toURI().toString()));
        app.start(locs);
        
        assertEquals(Files.readLines(pseudoBrooklynCatalogFile, Charsets.UTF_8), ImmutableList.of("abc=def"));
    }
    
    @Test(groups="Integration")
    public void testCopiesResources() throws Exception {
        File sourceFile = File.createTempFile("brooklynnode-test", ".properties");
        Files.write("abc=def", sourceFile, Charsets.UTF_8);
        File tempDir = Files.createTempDir();
        File expectedFile = new File(tempDir, "myfile.txt");
        
        try {
            BrooklynNode brooklynNode = app.createAndManageChild(EntitySpec.create(BrooklynNode.class)
                    .configure(BrooklynNode.SUGGESTED_RUN_DIR, tempDir.getAbsolutePath())
                    .configure(BrooklynNode.COPY_TO_RUNDIR, ImmutableMap.of(sourceFile.getAbsolutePath(), "${RUN}/myfile.txt")));
            app.start(locs);
    
            assertEquals(Files.readLines(expectedFile, Charsets.UTF_8), ImmutableList.of("abc=def"));
        } finally {
            expectedFile.delete();
            tempDir.delete();
            sourceFile.delete();
        }
    }
    
    @Test(groups="Integration")
    public void testSetsBrooklynWebConsolePort() throws Exception {
        BrooklynNode brooklynNode = app.createAndManageChild(EntitySpec.create(BrooklynNode.class)
                .configure(BrooklynNode.HTTP_PORT, PortRanges.fromString("45000+")));
        app.start(locs);
        
        Integer httpPort = brooklynNode.getAttribute(BrooklynNode.HTTP_PORT);
        URI webConsoleUri = brooklynNode.getAttribute(BrooklynNode.WEB_CONSOLE_URI);
        assertTrue(httpPort >= 45000 && httpPort < 54100, "httpPort="+httpPort);
        assertEquals((Integer)webConsoleUri.getPort(), httpPort);
        HttpTestUtils.assertHttpStatusCodeEquals(webConsoleUri.toString(), 200, 401);
    }
    
    @Test(groups="Integration")
    public void testStartsApp() throws Exception {
        BrooklynNode brooklynNode = app.createAndManageChild(EntitySpec.create(BrooklynNode.class)
                .configure(BrooklynNode.NO_WEB_CONSOLE_AUTHENTICATION, true)
                .configure(BrooklynNode.APP, BasicApplicationImpl.class.getName()));
        app.start(locs);
        
        URI webConsoleUri = brooklynNode.getAttribute(BrooklynNode.WEB_CONSOLE_URI);
        String apps = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/applications");
        List<String> appType = parseJsonList(apps, ImmutableList.of("spec", "type"), String.class);
        assertEquals(appType, ImmutableList.of(BasicApplication.class.getName()));
    }
    
    @Test(groups="Integration")
    public void testUsesLocation() throws Exception {
        String brooklynPropertiesContents = "brooklyn.location.named.mynamedloc=localhost";
        Files.copy(BROOKLYN_PROPERTIES_PATH, BROOKLYN_PROPERTIES_BAK_PATH);
        
        try {
            BrooklynNode brooklynNode = app.createAndManageChild(EntitySpec.create(BrooklynNode.class)
                    .configure(BrooklynNode.NO_WEB_CONSOLE_AUTHENTICATION, true)
                    .configure(BrooklynNode.BROOKLYN_GLOBAL_PROPERTIES_CONTENTS, brooklynPropertiesContents)
                    .configure(BrooklynNode.APP, BasicApplicationImpl.class.getName())
                    .configure(BrooklynNode.LOCATIONS, "named:mynamedloc"));
            app.start(locs);

            URI webConsoleUri = brooklynNode.getAttribute(BrooklynNode.WEB_CONSOLE_URI);

            // Check that "mynamedloc" has been picked up from the brooklyn.properties
            String locsContent = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/locations");
            List<String> locNames = parseJsonList(locsContent, ImmutableList.of("name"), String.class);
            assertTrue(locNames.contains("mynamedloc"), "locNames="+locNames);

            // Find the id of the concrete location instance of the app
            String appsContent = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/applications");
            List<String[]> appLocationIds = parseJsonList(appsContent, ImmutableList.of("spec", "locations"), String[].class);
            String appLocationId = Iterables.getOnlyElement(appLocationIds)[0];
            
            // Check that the concrete location is of the required type
            String locatedLocationsContent = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/locations/usage/LocatedLocations");
            String appLocationName = parseJson(locatedLocationsContent, ImmutableList.of(appLocationId, "name"), String.class);
            assertEquals(appLocationName, "mynamedloc");
            
        } finally {
            Files.copy(BROOKLYN_PROPERTIES_BAK_PATH, BROOKLYN_PROPERTIES_PATH);
        }
    }
    
    private <T> T parseJson(String json, List<String> elements, Class<T> clazz) {
        Function<String, T> func = HttpValueFunctions.chain(
                JsonFunctions.asJson(),
                JsonFunctions.walk(elements.toArray(new String[elements.size()])), 
                JsonFunctions.cast(clazz));
        return func.apply(json);
    }
    
    private <T> List<T> parseJsonList(String json, List<String> elements, Class<T> clazz) {
        Function<String, List<T>> func = HttpValueFunctions.chain(
                JsonFunctions.asJson(), 
                JsonFunctions.forEach(HttpValueFunctions.chain(
                        JsonFunctions.walk(elements.toArray(new String[elements.size()])), 
                        JsonFunctions.cast(clazz))));
        return func.apply(json);
    }
}

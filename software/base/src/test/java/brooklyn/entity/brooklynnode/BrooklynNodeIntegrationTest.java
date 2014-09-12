/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.brooklynnode;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.BasicApplicationImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.brooklynnode.BrooklynNode.DeployBlueprintEffector;
import brooklyn.entity.brooklynnode.BrooklynNode.ExistingFileBehaviour;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.feed.http.JsonFunctions;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.PortRanges;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.PropagatedRuntimeException;
import brooklyn.util.guava.Functionals;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.os.Os;
import brooklyn.util.text.Strings;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

/**
 * This test needs to able to access the binary artifact in order to run.
 * The default behaviour is to take this from maven, which works pretty well if you're downloading from hosted maven.
 * <p>
 * This class has been updated so that it does not effect or depend on the contents of ~/.brooklyn/brooklyn.properties .
 * <p>
 * If you wish to supply your own version (useful if testing changes locally!), you'll need to force download of this file.
 * The simplest way is to install:
 * <ul>
 * <li>file://$HOME/.brooklyn/repository/BrooklynNode/${VERSION}/BrooklynNode-${VERSION}.tar.gz - for snapshot versions (filename is default format due to lack of filename in sonatype inferencing; 
 *     note on case-sensitive systems it might have to be all in lower case!)
 * <li>file://$HOME/.brooklyn/repository/BrooklynNode/${VERSION}/brooklyn-${VERSION}-dist.tar.gz - for release versions, filename should match that in maven central
 * </ul>
 * In both cases, remember that you may also need to wipe the local apps cache ($BROOKLYN_DATA_DIR/installs/BrooklynNode).
 * The following commands may be useful:
 * <p>
 * <code>
 * cp ~/.m2/repository/io/brooklyn/brooklyn-dist/0.7.0-SNAPSHOT/brooklyn-dist-0.7.0-SNAPSHOT-dist.tar.gz ~/.brooklyn/repository/BrooklynNode/0.7.0-SNAPSHOT/BrooklynNode-0.7.0-SNAPSHOT.tar.gz
 * rm -rf /tmp/brooklyn-`whoami`/installs/BrooklynNode*
 * </code>
 */
public class BrooklynNodeIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BrooklynNodeIntegrationTest.class);
    
    private File pseudoBrooklynPropertiesFile;
    private File pseudoBrooklynCatalogFile;
    private List<? extends Location> locs;
    private TestApplication app;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        pseudoBrooklynPropertiesFile = Os.newTempFile("brooklynnode-test", ".properties");
        pseudoBrooklynPropertiesFile.delete();

        pseudoBrooklynCatalogFile = Os.newTempFile("brooklynnode-test", ".catalog");
        pseudoBrooklynCatalogFile.delete();

        app = TestApplication.Factory.newManagedInstanceForTests();
        Location localhost = app.getManagementSupport().getManagementContext().getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        locs = ImmutableList.of(localhost);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementSupport().getManagementContext());
        if (pseudoBrooklynPropertiesFile != null) pseudoBrooklynPropertiesFile.delete();
        if (pseudoBrooklynCatalogFile != null) pseudoBrooklynCatalogFile.delete();
    }

    protected EntitySpec<BrooklynNode> newBrooklynNodeSpecForTest() {
        // poor man's way to output which test is running
        log.info("Creating entity spec for "+JavaClassNames.callerNiceClassAndMethod(1));
        
        return EntitySpec.create(BrooklynNode.class)
                .configure(BrooklynNode.WEB_CONSOLE_BIND_ADDRESS, "127.0.0.1")
                .configure(BrooklynNode.ON_EXISTING_PROPERTIES_FILE, ExistingFileBehaviour.DO_NOT_USE);
    }

    @Test(groups="Integration")
    public void testCanStartAndStop() throws Exception {
        BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest());
        app.start(locs);
        log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());

        EntityTestUtils.assertAttributeEqualsEventually(brooklynNode, BrooklynNode.SERVICE_UP, true);

        brooklynNode.stop();
        EntityTestUtils.assertAttributeEquals(brooklynNode, BrooklynNode.SERVICE_UP, false);
    }

    @Test(groups="Integration")
    public void testSetsGlobalBrooklynPropertiesFromContents() throws Exception {
        BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest()
                .configure(BrooklynNode.BROOKLYN_GLOBAL_PROPERTIES_REMOTE_PATH, pseudoBrooklynPropertiesFile.getAbsolutePath())
                .configure(BrooklynNode.BROOKLYN_GLOBAL_PROPERTIES_CONTENTS, "abc=def"));
        app.start(locs);
        log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());

        assertEquals(Files.readLines(pseudoBrooklynPropertiesFile, Charsets.UTF_8), ImmutableList.of("abc=def"));
    }

    @Test(groups="Integration")
    public void testSetsLocalBrooklynPropertiesFromContents() throws Exception {
        BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest()
                .configure(BrooklynNode.BROOKLYN_LOCAL_PROPERTIES_REMOTE_PATH, pseudoBrooklynPropertiesFile.getAbsolutePath())
                .configure(BrooklynNode.BROOKLYN_LOCAL_PROPERTIES_CONTENTS, "abc=def"));
        app.start(locs);
        log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());

        assertEquals(Files.readLines(pseudoBrooklynPropertiesFile, Charsets.UTF_8), ImmutableList.of("abc=def"));
    }

    @Test(groups="Integration")
    public void testSetsBrooklynPropertiesFromUri() throws Exception {
        File brooklynPropertiesSourceFile = File.createTempFile("brooklynnode-test", ".properties");
        Files.write("abc=def", brooklynPropertiesSourceFile, Charsets.UTF_8);

        BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest()
                .configure(BrooklynNode.BROOKLYN_GLOBAL_PROPERTIES_REMOTE_PATH, pseudoBrooklynPropertiesFile.getAbsolutePath())
                .configure(BrooklynNode.BROOKLYN_GLOBAL_PROPERTIES_URI, brooklynPropertiesSourceFile.toURI().toString()));
        app.start(locs);
        log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());

        assertEquals(Files.readLines(pseudoBrooklynPropertiesFile, Charsets.UTF_8), ImmutableList.of("abc=def"));
    }

    @Test(groups="Integration")
    public void testSetsBrooklynCatalogFromContents() throws Exception {
        BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest()
                .configure(BrooklynNode.BROOKLYN_CATALOG_REMOTE_PATH, pseudoBrooklynCatalogFile.getAbsolutePath())
                .configure(BrooklynNode.BROOKLYN_CATALOG_CONTENTS, "<catalog/>"));
        app.start(locs);
        log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());

        assertEquals(Files.readLines(pseudoBrooklynCatalogFile, Charsets.UTF_8), ImmutableList.of("<catalog/>"));
    }

    @Test(groups="Integration")
    public void testSetsBrooklynCatalogFromUri() throws Exception {
        File brooklynCatalogSourceFile = File.createTempFile("brooklynnode-test", ".catalog");
        Files.write("abc=def", brooklynCatalogSourceFile, Charsets.UTF_8);

        BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest()
                .configure(BrooklynNode.BROOKLYN_CATALOG_REMOTE_PATH, pseudoBrooklynCatalogFile.getAbsolutePath())
                .configure(BrooklynNode.BROOKLYN_CATALOG_URI, brooklynCatalogSourceFile.toURI().toString()));
        app.start(locs);
        log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());

        assertEquals(Files.readLines(pseudoBrooklynCatalogFile, Charsets.UTF_8), ImmutableList.of("abc=def"));
    }

    @Test(groups="Integration")
    public void testCopiesResources() throws Exception {
        File sourceFile = File.createTempFile("brooklynnode-test", ".properties");
        Files.write("abc=def", sourceFile, Charsets.UTF_8);
        File tempDir = Files.createTempDir();
        File expectedFile = new File(tempDir, "myfile.txt");

        try {
            BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest()
                    .configure(BrooklynNode.RUN_DIR, tempDir.getAbsolutePath())
                    .configure(BrooklynNode.COPY_TO_RUNDIR, ImmutableMap.of(sourceFile.getAbsolutePath(), "${RUN}/myfile.txt")));
            app.start(locs);
            log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());

            assertEquals(Files.readLines(expectedFile, Charsets.UTF_8), ImmutableList.of("abc=def"));
        } finally {
            expectedFile.delete();
            tempDir.delete();
            sourceFile.delete();
        }
    }

    @Test(groups="Integration")
    public void testCopiesClasspathEntriesInConfigKey() throws Exception {
        String content = "abc=def";
        File classpathEntry1 = File.createTempFile("first", ".properties");
        File classpathEntry2 = File.createTempFile("second", ".properties");
        Files.write(content, classpathEntry1, Charsets.UTF_8);
        Files.write(content, classpathEntry2, Charsets.UTF_8);
        File tempDir = Files.createTempDir();
        File expectedFile1 = new File(new File(tempDir, "lib"), classpathEntry1.getName());
        File expectedFile2 = new File(new File(tempDir, "lib"), classpathEntry2.getName());

        try {
            BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest()
                    .configure(BrooklynNode.RUN_DIR, tempDir.getAbsolutePath())
                    .configure(BrooklynNode.CLASSPATH, ImmutableList.of(classpathEntry1.getAbsolutePath(), classpathEntry2.getAbsolutePath()))
                    );
            app.start(locs);
            log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());

            assertEquals(Files.readLines(expectedFile1, Charsets.UTF_8), ImmutableList.of(content));
            assertEquals(Files.readLines(expectedFile2, Charsets.UTF_8), ImmutableList.of(content));
        } finally {
            expectedFile1.delete();
            expectedFile2.delete();
            tempDir.delete();
            classpathEntry1.delete();
            classpathEntry2.delete();
        }
    }

    @Test(groups="Integration")
    public void testCopiesClasspathEntriesInBrooklynProperties() throws Exception {
        String content = "abc=def";
        File classpathEntry1 = File.createTempFile("first", ".properties");
        File classpathEntry2 = File.createTempFile("second", ".properties");
        Files.write(content, classpathEntry1, Charsets.UTF_8);
        Files.write(content, classpathEntry2, Charsets.UTF_8);
        File tempDir = Files.createTempDir();
        File expectedFile1 = new File(new File(tempDir, "lib"), classpathEntry1.getName());
        File expectedFile2 = new File(new File(tempDir, "lib"), classpathEntry2.getName());

        try {
            String propName = BrooklynNode.CLASSPATH.getName();
            String propValue = classpathEntry1.toURI().toString() + "," + classpathEntry2.toURI().toString();
            ((BrooklynProperties)app.getManagementContext().getConfig()).put(propName, propValue);
    
            BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest()
                    .configure(BrooklynNode.RUN_DIR, tempDir.getAbsolutePath())
                    );
            app.start(locs);
            log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());

            assertEquals(Files.readLines(expectedFile1, Charsets.UTF_8), ImmutableList.of(content));
            assertEquals(Files.readLines(expectedFile2, Charsets.UTF_8), ImmutableList.of(content));
        } finally {
            expectedFile1.delete();
            expectedFile2.delete();
            tempDir.delete();
            classpathEntry1.delete();
            classpathEntry2.delete();
        }
    }
    
    // TODO test that the classpath set above is actually used

    @Test(groups="Integration")
    public void testSetsBrooklynWebConsolePort() throws Exception {
        BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest()
                .configure(BrooklynNode.HTTP_PORT, PortRanges.fromString("45000+")));
        app.start(locs);
        log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());

        Integer httpPort = brooklynNode.getAttribute(BrooklynNode.HTTP_PORT);
        URI webConsoleUri = brooklynNode.getAttribute(BrooklynNode.WEB_CONSOLE_URI);
        assertTrue(httpPort >= 45000 && httpPort < 54100, "httpPort="+httpPort);
        assertEquals((Integer)webConsoleUri.getPort(), httpPort);
        HttpTestUtils.assertHttpStatusCodeEquals(webConsoleUri.toString(), 200, 401);
    }

    @Test(groups="Integration")
    public void testStartsAppOnStartup() throws Exception {
        BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest()
                .configure(BrooklynNode.APP, BasicApplicationImpl.class.getName()));
        app.start(locs);
        log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());

        URI webConsoleUri = brooklynNode.getAttribute(BrooklynNode.WEB_CONSOLE_URI);
        String apps = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/applications");
        List<String> appType = parseJsonList(apps, ImmutableList.of("spec", "type"), String.class);
        assertEquals(appType, ImmutableList.of(BasicApplication.class.getName()));
    }

    @Test(groups="Integration")
    public void testStartsAppViaEffector() throws Exception {
        BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest());
        app.start(locs);
        log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());
        
        final String id = brooklynNode.invoke(BrooklynNode.DEPLOY_BLUEPRINT, ConfigBag.newInstance()
            .configure(DeployBlueprintEffector.BLUEPRINT_TYPE, BasicApplication.class.getName())
            .getAllConfig()).get();
        
        // note there is also a test for this in DeployApplication
        final URI webConsoleUri = brooklynNode.getAttribute(BrooklynNode.WEB_CONSOLE_URI);
        String apps = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/applications");
        List<String> appType = parseJsonList(apps, ImmutableList.of("spec", "type"), String.class);
        assertEquals(appType, ImmutableList.of(BasicApplication.class.getName()));
        
        HttpTestUtils.assertContentEventuallyMatches(
            webConsoleUri.toString()+"/v1/applications/"+id+"/entities/"+id+"/sensors/service.state",
            "\"?(running|RUNNING)\"?");
    }
    
    @Test(groups="Integration")
    public void testUsesLocation() throws Exception {
        String brooklynPropertiesContents = 
            "brooklyn.location.named.mynamedloc=localhost:(name=myname)\n"+
                //force lat+long so test will work when offline
                "brooklyn.location.named.mynamedloc.latitude=123\n"+ 
                "brooklyn.location.named.mynamedloc.longitude=45.6\n";

        BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest()
            .configure(BrooklynNode.BROOKLYN_LOCAL_PROPERTIES_CONTENTS, brooklynPropertiesContents)
            .configure(BrooklynNode.APP, BasicApplicationImpl.class.getName())
            .configure(BrooklynNode.LOCATIONS, "named:mynamedloc"));
        app.start(locs);
        log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());

        URI webConsoleUri = brooklynNode.getAttribute(BrooklynNode.WEB_CONSOLE_URI);

        // Check that "mynamedloc" has been picked up from the brooklyn.properties
        String locsContent = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/locations");
        List<String> locNames = parseJsonList(locsContent, ImmutableList.of("name"), String.class);
        assertTrue(locNames.contains("mynamedloc"), "locNames="+locNames);

        // Find the id of the concrete location instance of the app
        String appsContent = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/applications");
        List<String[]> appLocationIds = parseJsonList(appsContent, ImmutableList.of("spec", "locations"), String[].class);
        String appLocationId = Iterables.getOnlyElement(appLocationIds)[0];  // app.getManagementContext().getLocationRegistry()

        // Check that the concrete location is of the required type
        String locatedLocationsContent = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/locations/usage/LocatedLocations");
        assertEquals(parseJson(locatedLocationsContent, ImmutableList.of(appLocationId, "name"), String.class), "myname");
        assertEquals(parseJson(locatedLocationsContent, ImmutableList.of(appLocationId, "longitude"), Double.class), 45.6, 0.00001);
    }

    @Test(groups="Integration")
    public void testAuthenticationAndHttps() throws Exception {
        String adminPassword = "p4ssw0rd";
        BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest()
            .configure(BrooklynNode.ENABLED_HTTP_PROTOCOLS, ImmutableList.of("https"))
            .configure(BrooklynNode.MANAGEMENT_PASSWORD, adminPassword)
            .configure(BrooklynNode.BROOKLYN_LOCAL_PROPERTIES_CONTENTS,
                Strings.lines(
                    "brooklyn.webconsole.security.https.required=true",
                    "brooklyn.webconsole.security.users=admin",
                    "brooklyn.webconsole.security.user.admin.password="+adminPassword,
                    "brooklyn.location.localhost.enabled=false") )
            );
        app.start(locs);
        log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());

        URI webConsoleUri = brooklynNode.getAttribute(BrooklynNode.WEB_CONSOLE_URI);
        Assert.assertTrue(webConsoleUri.toString().startsWith("https://"), "web console not https: "+webConsoleUri);
        Integer httpsPort = brooklynNode.getAttribute(BrooklynNode.HTTPS_PORT);
        Assert.assertTrue(httpsPort!=null && httpsPort >= 8443 && httpsPort <= 8500);
        Assert.assertTrue(webConsoleUri.toString().contains(""+httpsPort), "web console not using right https port ("+httpsPort+"): "+webConsoleUri);
        HttpTestUtils.assertHttpStatusCodeEquals(webConsoleUri.toString(), 401);

        HttpClient http = HttpTool.httpClientBuilder()
            .trustAll()
            .uri(webConsoleUri)
            .laxRedirect(true)
            .credentials(new UsernamePasswordCredentials("admin", adminPassword))
            .build();
        HttpToolResponse response = HttpTool.httpGet(http, webConsoleUri, MutableMap.<String,String>of());
        Assert.assertEquals(response.getResponseCode(), 200);
    }

    @Test(groups="Integration", expectedExceptions = PropagatedRuntimeException.class)
    public void testStopPlainThrowsException() throws Exception {
        BrooklynNode brooklynNode = setUpBrooklynNodeWithApp();

        brooklynNode.stop();
    }

    @Test(groups="Integration")
    public void testStopAndKillAppsEffector() throws Exception {
        createNodeAndExecStopEffector(BrooklynNode.STOP_NODE_AND_KILL_APPS);
    }

    @Test(groups="Integration")
    public void testStopButLeaveAppsEffector() throws Exception {
        createNodeAndExecStopEffector(BrooklynNode.STOP_NODE_BUT_LEAVE_APPS);
    }

    private void createNodeAndExecStopEffector(Effector<?> eff) throws Exception {
        BrooklynNode brooklynNode = setUpBrooklynNodeWithApp();

        brooklynNode.invoke(eff, Collections.<String, Object>emptyMap()).getUnchecked();

        EntityTestUtils.assertAttributeEqualsEventually(brooklynNode, BrooklynNode.SERVICE_UP, false);
    }

    private BrooklynNode setUpBrooklynNodeWithApp() throws InterruptedException,
            ExecutionException {
        BrooklynNode brooklynNode = app.createAndManageChild(EntitySpec.create(BrooklynNode.class)
                .configure(BrooklynNode.NO_WEB_CONSOLE_AUTHENTICATION, Boolean.TRUE));
        app.start(locs);
        log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());

        EntityTestUtils.assertAttributeEqualsEventually(brooklynNode, BrooklynNode.SERVICE_UP, true);

        final String id = brooklynNode.invoke(BrooklynNode.DEPLOY_BLUEPRINT, ConfigBag.newInstance()
                .configure(DeployBlueprintEffector.BLUEPRINT_TYPE, BasicApplication.class.getName())
                .getAllConfig()).get();

        String baseUrl = brooklynNode.getAttribute(BrooklynNode.WEB_CONSOLE_URI).toString();
        String entityUrl = baseUrl + "v1/applications/" + id + "/entities/" + id;
        
        Entity mirror = brooklynNode.addChild(EntitySpec.create(BrooklynEntityMirror.class)
                .configure(BrooklynEntityMirror.MIRRORED_ENTITY_URL, entityUrl)
                .configure(BrooklynEntityMirror.MIRRORED_ENTITY_ID, id));
        Entities.manage(mirror);

        assertEquals(brooklynNode.getChildren().size(), 1);
        return brooklynNode;
    }

    private <T> T parseJson(String json, List<String> elements, Class<T> clazz) {
        Function<String, T> func = Functionals.chain(
                JsonFunctions.asJson(),
                JsonFunctions.walk(elements),
                JsonFunctions.cast(clazz));
        return func.apply(json);
    }

    private <T> List<T> parseJsonList(String json, List<String> elements, Class<T> clazz) {
        Function<String, List<T>> func = Functionals.chain(
                JsonFunctions.asJson(),
                JsonFunctions.forEach(Functionals.chain(
                        JsonFunctions.walk(elements),
                        JsonFunctions.cast(clazz))));
        return func.apply(json);
    }
}

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
package org.apache.brooklyn.entity.brooklynnode;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.objs.proxy.EntityProxyImpl;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.brooklynnode.BrooklynNode.DeployBlueprintEffector;
import org.apache.brooklyn.entity.brooklynnode.BrooklynNode.ExistingFileBehaviour;
import org.apache.brooklyn.entity.brooklynnode.BrooklynNode.StopNodeAndKillAppsEffector;
import org.apache.brooklyn.entity.software.base.SoftwareProcess.StopSoftwareParameters.StopMode;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.entity.stock.BasicApplicationImpl;
import org.apache.brooklyn.feed.http.JsonFunctions;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.HttpTestUtils;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.brooklyn.util.http.HttpToolResponse;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

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
 * cp ~/.m2/repository/org/apache/brooklyn/brooklyn-dist/0.7.0-SNAPSHOT/brooklyn-dist-0.7.0-SNAPSHOT-dist.tar.gz ~/.brooklyn/repository/BrooklynNode/0.7.0-SNAPSHOT/BrooklynNode-0.7.0-SNAPSHOT.tar.gz
 * rm -rf /tmp/brooklyn-`whoami`/installs/BrooklynNode*
 * </code>
 */
public class BrooklynNodeIntegrationTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(BrooklynNodeIntegrationTest.class);
    
    private File pseudoBrooklynPropertiesFile;
    private File pseudoBrooklynCatalogFile;
    private File persistenceDir;
    private LocalhostMachineProvisioningLocation loc;
    private List<LocalhostMachineProvisioningLocation> locs;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        pseudoBrooklynPropertiesFile = Os.newTempFile("brooklynnode-test", ".properties");
        pseudoBrooklynPropertiesFile.delete();

        pseudoBrooklynCatalogFile = Os.newTempFile("brooklynnode-test", ".catalog");
        pseudoBrooklynCatalogFile.delete();

        loc = app.newLocalhostProvisioningLocation();
        locs = ImmutableList.of(loc);
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        try {
            super.tearDown();
        } finally {
            if (pseudoBrooklynPropertiesFile != null) pseudoBrooklynPropertiesFile.delete();
            if (pseudoBrooklynCatalogFile != null) pseudoBrooklynCatalogFile.delete();
            if (persistenceDir != null) Os.deleteRecursively(persistenceDir);
        }
    }

    protected EntitySpec<BrooklynNode> newBrooklynNodeSpecForTest() {
        // poor man's way to output which test is running
        log.info("Creating entity spec for "+JavaClassNames.callerNiceClassAndMethod(1));
        
        return EntitySpec.create(BrooklynNode.class)
                .configure(BrooklynNode.WEB_CONSOLE_BIND_ADDRESS, Networking.ANY_NIC)
                .configure(BrooklynNode.ON_EXISTING_PROPERTIES_FILE, ExistingFileBehaviour.DO_NOT_USE);
        
        /* yaml equivalent, for testing:

location: localhost
services:
- type: org.apache.brooklyn.entity.brooklynnode.BrooklynNode
  bindAddress: 127.0.0.1
  onExistingProperties: do_not_use

# some other options
  enabledHttpProtocols: [ https ]
  managementPassword: s3cr3t
  brooklynLocalPropertiesContents: |
    brooklyn.webconsole.security.https.required=true
    brooklyn.webconsole.security.users=admin
    brooklyn.webconsole.security.user.admin.password=s3cr3t
    brooklyn.location.localhost.enabled=false

         */
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
        File destDir = new File(new File(tempDir, "lib"), "dropins");
        File expectedFile1 = new File(destDir, classpathEntry1.getName());
        File expectedFile2 = new File(destDir, classpathEntry2.getName());

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
    public void testCopiesClasspathEntriesInConfigKey2() throws Exception {
        String content = "abc=def";
        File classpathEntry1 = File.createTempFile("first", ".properties");
        File classpathEntry2 = File.createTempFile("second", ".properties");
        Files.write(content, classpathEntry1, Charsets.UTF_8);
        Files.write(content, classpathEntry2, Charsets.UTF_8);
        File tempDir = Files.createTempDir();
        String testName1 = "test_" + classpathEntry1.getName();
        File destDir = new File(new File(tempDir, "lib"), "dropins");
        File expectedFile1 = new File(destDir, testName1);
        String testName2 = "test_" + classpathEntry2.getName();
        File expectedFile2 = new File(destDir, testName2);
        Map entry1 = ImmutableMap.of("url", classpathEntry1.getAbsolutePath(), "filename", testName1);
        Map entry2 = ImmutableMap.of("url", classpathEntry2.getAbsolutePath(), "filename", testName2);

        try {
            BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest()
                            .configure(BrooklynNode.RUN_DIR, tempDir.getAbsolutePath())
                            .configure(BrooklynNode.CLASSPATH, ImmutableList.of(entry1, entry2))
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

    /*
        Exception java.io.FileNotFoundException
        
        Message: /tmp/1445824492556-0/lib/first4759470075693094333.properties (No such file or directory)
        Stacktrace:
        
        
        at java.io.FileInputStream.open(Native Method)
        at java.io.FileInputStream.<init>(FileInputStream.java:146)
        at com.google.common.io.Files$FileByteSource.openStream(Files.java:126)
        at com.google.common.io.Files$FileByteSource.openStream(Files.java:116)
        at com.google.common.io.ByteSource$AsCharSource.openStream(ByteSource.java:435)
        at com.google.common.io.CharSource.getInput(CharSource.java:94)
        at com.google.common.io.CharSource.getInput(CharSource.java:65)
        at com.google.common.io.CharStreams.readLines(CharStreams.java:344)
        at com.google.common.io.Files.readLines(Files.java:741)
        at com.google.common.io.Files.readLines(Files.java:712)
        at org.apache.brooklyn.entity.brooklynnode.BrooklynNodeIntegrationTest.testCopiesClasspathEntriesInBrooklynProperties(BrooklynNodeIntegrationTest.java:358)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:606)
        at org.testng.internal.MethodInvocationHelper.invokeMethod(MethodInvocationHelper.java:84)
        at org.testng.internal.Invoker.invokeMethod(Invoker.java:714)
        at org.testng.internal.Invoker.invokeTestMethod(Invoker.java:901)
        at org.testng.internal.Invoker.invokeTestMethods(Invoker.java:1231)
        at org.testng.internal.TestMethodWorker.invokeTestMethods(TestMethodWorker.java:127)
        at org.testng.internal.TestMethodWorker.run(TestMethodWorker.java:111)
        at org.testng.TestRunner.privateRun(TestRunner.java:767)
        at org.testng.TestRunner.run(TestRunner.java:617)
        at org.testng.SuiteRunner.runTest(SuiteRunner.java:348)
        at org.testng.SuiteRunner.runSequentially(SuiteRunner.java:343)
        at org.testng.SuiteRunner.privateRun(SuiteRunner.java:305)
        at org.testng.SuiteRunner.run(SuiteRunner.java:254)
        at org.testng.SuiteRunnerWorker.runSuite(SuiteRunnerWorker.java:52)
        at org.testng.SuiteRunnerWorker.run(SuiteRunnerWorker.java:86)
        at org.testng.TestNG.runSuitesSequentially(TestNG.java:1224)
        at org.testng.TestNG.runSuitesLocally(TestNG.java:1149)
        at org.testng.TestNG.run(TestNG.java:1057)
        at org.apache.maven.surefire.testng.TestNGExecutor.run(TestNGExecutor.java:115)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.executeMulti(TestNGDirectoryTestSuite.java:205)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.execute(TestNGDirectoryTestSuite.java:108)
        at org.apache.maven.surefire.testng.TestNGProvider.invoke(TestNGProvider.java:111)
        at org.apache.maven.surefire.booter.ForkedBooter.invokeProviderInSameClassLoader(ForkedBooter.java:203)
        at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:155)
        at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:103)
    */
    @Test(groups={"Integration","Broken"})
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
        waitForApps(webConsoleUri, 1);
        String apps = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/applications");
        List<String> appType = parseJsonList(apps, ImmutableList.of("spec", "type"), String.class);
        assertEquals(appType, ImmutableList.of(BasicApplication.class.getName()));
    }

    protected static void waitForApps(String webConsoleUri) {
        HttpTestUtils.assertHttpStatusCodeEquals(webConsoleUri+"/v1/applications", 200, 403);
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(webConsoleUri+"/v1/applications", 200);
    }

    // TODO Should introduce startup stages and let the client select which stage it expects to be complete
    protected void waitForApps(final URI webConsoleUri, final int num) {
        waitForApps(webConsoleUri.toString());
        
        // e.g. [{"id":"UnBqPcqg","spec":{"name":"Application (UnBqPcqg)","type":"org.apache.brooklyn.entity.stock.BasicApplication","locations":["pOL4NtiW"]},"status":"RUNNING","links":{"self":"/v1/applications/UnBqPcqg","entities":"/v1/applications/UnBqPcqg/entities"}}]
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                //Wait all apps to become managed
                String appsContent = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/applications");
                List<String> appIds = parseJsonList(appsContent, ImmutableList.of("id"), String.class);
                assertEquals(appIds.size(), num);
                
                // and then to start
                List<String> statuses = parseJsonList(appsContent, ImmutableList.of("status"), String.class);
                for (String status : statuses) {
                    assertEquals(status, Lifecycle.RUNNING.toString().toUpperCase());
                }
            }});
    }

    @Test(groups="Integration")
    public void testStartsAppViaEffector() throws Exception {
        BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest());
        app.start(locs);
        log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());
        
        // note there is also a test for this in DeployApplication
        final URI webConsoleUri = brooklynNode.getAttribute(BrooklynNode.WEB_CONSOLE_URI);
        waitForApps(webConsoleUri.toString());

        final String id = brooklynNode.invoke(BrooklynNode.DEPLOY_BLUEPRINT, ConfigBag.newInstance()
            .configure(DeployBlueprintEffector.BLUEPRINT_TYPE, BasicApplication.class.getName())
            .getAllConfig()).get();
        
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
        waitForApps(webConsoleUri, 1);

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

    @Test(groups="Integration")
    public void testStopPlainThrowsException() throws Exception {
        BrooklynNode brooklynNode = setUpBrooklynNodeWithApp();

        // Not using annotation with `expectedExceptions = PropagatedRuntimeException.class` because want to 
        // ensure exception comes from stop. On jenkins, was seeing setUpBrooklynNodeWithApp fail in 
        // testStopAndKillAppsEffector; so can't tell if this method was really passing!
        try {
            brooklynNode.stop();
            fail("Expected "+brooklynNode+" stop to fail, because has app");
        } catch (Exception e) {
            IllegalStateException ise = Exceptions.getFirstThrowableOfType(e, IllegalStateException.class);
            if (ise != null && ise.toString().contains("Can't stop instance with running applications")) {
                // success
            } else {
                throw e;
            }
        } finally {
            try {
                brooklynNode.invoke(BrooklynNode.STOP_NODE_AND_KILL_APPS, ImmutableMap.of(StopNodeAndKillAppsEffector.TIMEOUT.getName(), Duration.THIRTY_SECONDS)).getUnchecked();
            } catch (Exception e) {
                log.warn("Error in stopNodeAndKillApps for "+brooklynNode+" (continuing)", e);
            }
        }
    }

    @Test(groups="Integration")
    public void testStopAndKillAppsEffector() throws Exception {
        createNodeAndExecStopEffector(BrooklynNode.STOP_NODE_AND_KILL_APPS);
    }

    @Test(groups="Integration")
    public void testStopButLeaveAppsEffector() throws Exception {
        createNodeAndExecStopEffector(BrooklynNode.STOP_NODE_BUT_LEAVE_APPS);
    }
    
    @Test(groups="Integration")
    public void testStopAndRestartProcess() throws Exception {
        persistenceDir = Files.createTempDir();
        BrooklynNode brooklynNode = app.createAndManageChild(newBrooklynNodeSpecForTest()
                .configure(BrooklynNode.EXTRA_LAUNCH_PARAMETERS, "--persist auto --persistenceDir "+persistenceDir.getAbsolutePath())
                .configure(BrooklynNode.APP, BasicApplicationImpl.class.getName()));
        app.start(locs);
        log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());
        File pidFile = new File(getDriver(brooklynNode).getPidFile());
        URI webConsoleUri = brooklynNode.getAttribute(BrooklynNode.WEB_CONSOLE_URI);

        waitForApps(webConsoleUri, 1);

        // Stop just the process; will not have unmanaged entity unless machine was being terminated 
        brooklynNode.invoke(BrooklynNode.STOP, ImmutableMap.<String, Object>of(
                BrooklynNode.StopSoftwareParameters.STOP_MACHINE_MODE.getName(), StopMode.NEVER,
                BrooklynNode.StopSoftwareParameters.STOP_PROCESS_MODE.getName(), StopMode.ALWAYS)).getUnchecked();

        assertTrue(Entities.isManaged(brooklynNode));
        assertFalse(isPidRunning(pidFile), "pid in "+pidFile+" still running");
        
        // Clear the startup app so it's not started second time, in addition to the rebind state
        // TODO remove this once the startup app is created only if no previous persistence state
        brooklynNode.config().set(BrooklynNode.APP, (String)null);
        ((EntityLocal)brooklynNode).sensors().set(BrooklynNode.APP, null);
        
        // Restart the process; expect persisted state to have been restored, so apps still known about
        brooklynNode.invoke(BrooklynNode.RESTART, ImmutableMap.<String, Object>of(
                BrooklynNode.RestartSoftwareParameters.RESTART_MACHINE.getName(), "false")).getUnchecked();

        waitForApps(webConsoleUri.toString());
        String apps = HttpTestUtils.getContent(webConsoleUri.toString()+"/v1/applications");
        List<String> appType = parseJsonList(apps, ImmutableList.of("spec", "type"), String.class);
        assertEquals(appType, ImmutableList.of(BasicApplication.class.getName()));
    }

    private void createNodeAndExecStopEffector(Effector<?> eff) throws Exception {
        BrooklynNode brooklynNode = setUpBrooklynNodeWithApp();
        File pidFile = new File(getDriver(brooklynNode).getPidFile());
        assertTrue(isPidRunning(pidFile));
        
        brooklynNode.invoke(eff, Collections.<String, Object>emptyMap()).getUnchecked();

        // Note can't use driver.isRunning to check shutdown; can't invoke scripts on an unmanaged entity
        EntityTestUtils.assertAttributeEquals(brooklynNode, BrooklynNode.SERVICE_UP, false);
        
        // unmanaged if the machine is destroyed - ie false on localhost (this test by default), but true in the cloud 
//        assertFalse(Entities.isManaged(brooklynNode));
        
        assertFalse(isPidRunning(pidFile), "pid in "+pidFile+" still running");
    }

    private boolean isPidRunning(File pidFile) throws Exception {
        SshMachineLocation machine = loc.obtain();
        try {
            int result = machine.execScript("check-pid", ImmutableList.of(
                    "test -f "+pidFile+" || exit 1",
                    "ps -p `cat "+pidFile+"`"));
            return result == 0;
        } finally {
            loc.release(machine);
            Locations.unmanage(machine);
        }
    }
    
    private BrooklynNodeSshDriver getDriver(BrooklynNode brooklynNode) {
        try {
            EntityProxyImpl entityProxy = (EntityProxyImpl)Proxy.getInvocationHandler(brooklynNode);
            Method getDriver = BrooklynNodeImpl.class.getMethod("getDriver");
            return (BrooklynNodeSshDriver)entityProxy.invoke(brooklynNode, getDriver, new Object[]{});
        } catch (Throwable e) {
            throw Exceptions.propagate(e);
        }
    }

    private BrooklynNode setUpBrooklynNodeWithApp() throws InterruptedException,
            ExecutionException {
        BrooklynNode brooklynNode = app.createAndManageChild(EntitySpec.create(BrooklynNode.class)
                .configure(BrooklynNode.NO_WEB_CONSOLE_AUTHENTICATION, Boolean.TRUE));
        app.start(locs);
        log.info("started "+app+" containing "+brooklynNode+" for "+JavaClassNames.niceClassAndMethod());

        EntityTestUtils.assertAttributeEqualsEventually(brooklynNode, BrooklynNode.SERVICE_UP, true);

        String baseUrl = brooklynNode.getAttribute(BrooklynNode.WEB_CONSOLE_URI).toString();
        waitForApps(baseUrl);
        
        final String id = brooklynNode.invoke(BrooklynNode.DEPLOY_BLUEPRINT, ConfigBag.newInstance()
                .configure(DeployBlueprintEffector.BLUEPRINT_TYPE, BasicApplication.class.getName())
                .getAllConfig()).get();

        String entityUrl = Urls.mergePaths(baseUrl, "v1/applications/", id, "entities", id);
        
        Entity mirror = brooklynNode.addChild(EntitySpec.create(BrooklynEntityMirror.class)
                .configure(BrooklynEntityMirror.MIRRORED_ENTITY_URL, entityUrl)
                .configure(BrooklynEntityMirror.MIRRORED_ENTITY_ID, id));

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

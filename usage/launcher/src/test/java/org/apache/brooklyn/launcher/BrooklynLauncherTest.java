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
package org.apache.brooklyn.launcher;

import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.apache.brooklyn.management.ManagementContext;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Properties;

import org.apache.brooklyn.test.HttpTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.catalog.internal.CatalogInitialization;
import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.entity.Application;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestApplicationImpl;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.exceptions.FatalRuntimeException;
import brooklyn.util.io.FileUtil;
import brooklyn.util.net.Urls;
import brooklyn.util.os.Os;
import brooklyn.util.text.StringFunctions;
import brooklyn.util.text.Strings;

import com.google.api.client.util.Preconditions;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

public class BrooklynLauncherTest {
    
    private BrooklynLauncher launcher;
    private File persistenceDir;
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (launcher != null) launcher.terminate();
        if (persistenceDir != null) RebindTestUtils.deleteMementoDir(persistenceDir);
        launcher = null;
    }
    
    // Integration because takes a few seconds to start web-console
    @Test(groups="Integration")
    public void testStartsWebServerOnExpectectedPort() throws Exception {
        launcher = newLauncherForTests(true)
                .webconsolePort("10000+")
                .start();
        
        String webServerUrlStr = launcher.getServerDetails().getWebServerUrl();
        URI webServerUri = new URI(webServerUrlStr);
        
        assertEquals(launcher.getApplications(), ImmutableList.of());
        assertTrue(webServerUri.getPort() >= 10000 && webServerUri.getPort() < 10100, "port="+webServerUri.getPort()+"; uri="+webServerUri);
        HttpTestUtils.assertUrlReachable(webServerUrlStr);
    }
    
    // Integration because takes a few seconds to start web-console
    @Test(groups="Integration")
    public void testWebServerTempDirRespectsDataDirConfig() throws Exception {
        String dataDirName = ".brooklyn-foo"+Strings.makeRandomId(4);
        String dataDir = "~/"+dataDirName;

        launcher = newLauncherForTests(true)
                .brooklynProperties(BrooklynServerConfig.MGMT_BASE_DIR, dataDir)
                .start();
        
        ManagementContext managementContext = launcher.getServerDetails().getManagementContext();
        String expectedTempDir = Os.mergePaths(Os.home(), dataDirName, "planes", managementContext.getManagementPlaneId(), managementContext.getManagementNodeId(), "jetty");
        
        File webappTempDir = launcher.getServerDetails().getWebServer().getWebappTempDir();
        assertEquals(webappTempDir.getAbsolutePath(), expectedTempDir);
    }
    
    @Test
    public void testCanDisableWebServerStartup() throws Exception {
        launcher = newLauncherForTests(true)
                .webconsole(false)
                .start();
        
        assertNull(launcher.getServerDetails().getWebServer());
        assertNull(launcher.getServerDetails().getWebServerUrl());
        Assert.assertTrue( ((ManagementContextInternal)launcher.getServerDetails().getManagementContext()).errors().isEmpty() );
    }
    
    @Test
    public void testStartsAppInstance() throws Exception {
        launcher = newLauncherForTests(true)
                .webconsole(false)
                .application(new TestApplicationImpl())
                .start();
        
        assertOnlyApp(launcher, TestApplication.class);
    }
    
    @Test
    public void testStartsAppFromSpec() throws Exception {
        launcher = newLauncherForTests(true)
                .webconsole(false)
                .application(EntitySpec.create(TestApplication.class))
                .start();
        
        assertOnlyApp(launcher, TestApplication.class);
    }
    
    @Test
    public void testStartsAppFromBuilder() throws Exception {
        launcher = newLauncherForTests(true)
                .webconsole(false)
                .application(new ApplicationBuilder(EntitySpec.create(TestApplication.class)) {
                        @Override protected void doBuild() {
                        }})
                .start();
        
        assertOnlyApp(launcher, TestApplication.class);
    }

    @Test
    public void testStartsAppFromYAML() throws Exception {
        String yaml = "name: example-app\n" +
                "services:\n" +
                "- serviceType: brooklyn.test.entity.TestEntity\n" +
                "  name: test-app";
        launcher = newLauncherForTests(true)
                .webconsole(false)
                .application(yaml)
                .start();

        assertEquals(launcher.getApplications().size(), 1, "apps="+launcher.getApplications());
        Application app = Iterables.getOnlyElement(launcher.getApplications());
        assertEquals(app.getChildren().size(), 1, "children=" + app.getChildren());
        assertTrue(Iterables.getOnlyElement(app.getChildren()) instanceof TestEntity);
    }
    
    @Test  // may take 2s initializing location if running this test case alone, but noise if running suite 
    public void testStartsAppInSuppliedLocations() throws Exception {
        launcher = newLauncherForTests(true)
                .webconsole(false)
                .location("localhost")
                .application(new ApplicationBuilder(EntitySpec.create(TestApplication.class)) {
                        @Override protected void doBuild() {
                        }})
                .start();
        
        Application app = Iterables.find(launcher.getApplications(), Predicates.instanceOf(TestApplication.class));
        assertOnlyLocation(app, LocalhostMachineProvisioningLocation.class);
    }
    
    @Test
    public void testUsesSuppliedManagementContext() throws Exception {
        LocalManagementContext myManagementContext = LocalManagementContextForTests.newInstance();
        launcher = newLauncherForTests(false)
                .webconsole(false)
                .managementContext(myManagementContext)
                .start();
        
        assertSame(launcher.getServerDetails().getManagementContext(), myManagementContext);
    }
    
    @Test
    public void testUsesSuppliedBrooklynProperties() throws Exception {
        BrooklynProperties props = LocalManagementContextForTests.builder(true).buildProperties();
        props.put("mykey", "myval");
        launcher = newLauncherForTests(false)
                .webconsole(false)
                .brooklynProperties(props)
                .start();
        
        assertEquals(launcher.getServerDetails().getManagementContext().getConfig().getFirst("mykey"), "myval");
    }

    @Test
    public void testUsesSupplementaryBrooklynProperties() throws Exception {
        launcher = newLauncherForTests(true)
                .webconsole(false)
                .brooklynProperties("mykey", "myval")
                .start();
        
        assertEquals(launcher.getServerDetails().getManagementContext().getConfig().getFirst("mykey"), "myval");
    }
    
    @Test
    public void testReloadBrooklynPropertiesRestoresProgrammaticProperties() throws Exception {
        launcher = newLauncherForTests(true)
                .webconsole(false)
                .brooklynProperties("mykey", "myval")
                .start();
        LocalManagementContext managementContext = (LocalManagementContext)launcher.getServerDetails().getManagementContext();
        assertEquals(managementContext.getConfig().getFirst("mykey"), "myval");
        managementContext.getBrooklynProperties().put("mykey", "newval");
        assertEquals(managementContext.getConfig().getFirst("mykey"), "newval");
        managementContext.reloadBrooklynProperties();
        assertEquals(managementContext.getConfig().getFirst("mykey"), "myval");
    }
    
    @Test
    public void testReloadBrooklynPropertiesFromFile() throws Exception {
        File globalPropertiesFile = File.createTempFile("local-brooklyn-properties-test", ".properties");
        FileUtil.setFilePermissionsTo600(globalPropertiesFile);
        try {
            String property = "mykey=myval";
            Files.append(getMinimalLauncherPropertiesString()+property, globalPropertiesFile, Charsets.UTF_8);
            launcher = newLauncherForTests(false)
                    .webconsole(false)
                    .globalBrooklynPropertiesFile(globalPropertiesFile.getAbsolutePath())
                    .start();
            LocalManagementContext managementContext = (LocalManagementContext)launcher.getServerDetails().getManagementContext();
            assertEquals(managementContext.getConfig().getFirst("mykey"), "myval");
            property = "mykey=newval";
            Files.write(getMinimalLauncherPropertiesString()+property, globalPropertiesFile, Charsets.UTF_8);
            managementContext.reloadBrooklynProperties();
            assertEquals(managementContext.getConfig().getFirst("mykey"), "newval");
        } finally {
            globalPropertiesFile.delete();
        }
    }

    @Test(groups="Integration")
    public void testChecksGlobalBrooklynPropertiesPermissionsX00() throws Exception {
        File propsFile = File.createTempFile("testChecksGlobalBrooklynPropertiesPermissionsX00", ".properties");
        propsFile.setReadable(true, false);
        try {
            launcher = newLauncherForTests(false)
                    .webconsole(false)
                    .globalBrooklynPropertiesFile(propsFile.getAbsolutePath())
                    .start();

            Assert.fail("Should have thrown");
        } catch (FatalRuntimeException e) {
            if (!e.toString().contains("Invalid permissions for file")) throw e;
        } finally {
            propsFile.delete();
        }
    }

    @Test(groups="Integration")
    public void testChecksLocalBrooklynPropertiesPermissionsX00() throws Exception {
        File propsFile = File.createTempFile("testChecksLocalBrooklynPropertiesPermissionsX00", ".properties");
        propsFile.setReadable(true, false);
        try {
            launcher = newLauncherForTests(false)
                    .webconsole(false)
                    .localBrooklynPropertiesFile(propsFile.getAbsolutePath())
                    .start();
            
            Assert.fail("Should have thrown");
        } catch (FatalRuntimeException e) {
            if (!e.toString().contains("Invalid permissions for file")) throw e;
        } finally {
            propsFile.delete();
        }
    }

    @Test(groups="Integration")
    public void testStartsWithBrooklynPropertiesPermissionsX00() throws Exception {
        File globalPropsFile = File.createTempFile("testChecksLocalBrooklynPropertiesPermissionsX00_global", ".properties");
        Files.write(getMinimalLauncherPropertiesString()+"key_in_global=1", globalPropsFile, Charset.defaultCharset());
        File localPropsFile = File.createTempFile("testChecksLocalBrooklynPropertiesPermissionsX00_local", ".properties");
        Files.write("key_in_local=2", localPropsFile, Charset.defaultCharset());
        FileUtil.setFilePermissionsTo600(globalPropsFile);
        FileUtil.setFilePermissionsTo600(localPropsFile);
        try {
            launcher = newLauncherForTests(false)
                    .webconsole(false)
                    .localBrooklynPropertiesFile(localPropsFile.getAbsolutePath())
                    .globalBrooklynPropertiesFile(globalPropsFile.getAbsolutePath())
                    .start();
            assertEquals(launcher.getServerDetails().getManagementContext().getConfig().getFirst("key_in_global"), "1");
            assertEquals(launcher.getServerDetails().getManagementContext().getConfig().getFirst("key_in_local"), "2");
        } finally {
            globalPropsFile.delete();
            localPropsFile.delete();
        }
    }
    
    @Test  // takes a bit of time because starts webapp, but also tests rest api so useful
    public void testErrorsCaughtByApiAndRestApiWorks() throws Exception {
        launcher = newLauncherForTests(true)
                .catalogInitialization(new CatalogInitialization(null, false, null, false).addPopulationCallback(new Function<CatalogInitialization, Void>() {
                    @Override
                    public Void apply(CatalogInitialization input) {
                        throw new RuntimeException("deliberate-exception-for-testing");
                    }
                }))
                .start();
        // such an error should be thrown, then caught in this calling thread
        ManagementContext mgmt = launcher.getServerDetails().getManagementContext();
        Assert.assertFalse( ((ManagementContextInternal)mgmt).errors().isEmpty() );
        Assert.assertTrue( ((ManagementContextInternal)mgmt).errors().get(0).toString().contains("deliberate"), ""+((ManagementContextInternal)mgmt).errors() );
        HttpTestUtils.assertContentMatches(
            Urls.mergePaths(launcher.getServerDetails().getWebServerUrl(), "v1/server/up"), 
            "true");
        HttpTestUtils.assertContentMatches(
            Urls.mergePaths(launcher.getServerDetails().getWebServerUrl(), "v1/server/healthy"), 
            "false");
        // TODO test errors api?
    }

    private BrooklynLauncher newLauncherForTests(boolean minimal) {
        Preconditions.checkArgument(launcher==null, "can only be used if no launcher yet");
        BrooklynLauncher launcher = BrooklynLauncher.newInstance();
        if (minimal)
            launcher.brooklynProperties(LocalManagementContextForTests.builder(true).buildProperties());
        return launcher;
    }

    private String getMinimalLauncherPropertiesString() throws IOException {
        BrooklynProperties p1 = LocalManagementContextForTests.builder(true).buildProperties();
        Properties p = new Properties();
        p.putAll(Maps.transformValues(p1.asMapWithStringKeys(), StringFunctions.toStringFunction()));
        Writer w = new StringWriter();
        p.store(w, "test");
        w.close();
        return w.toString()+"\n";
    }

    private void assertOnlyApp(BrooklynLauncher launcher, Class<? extends Application> expectedType) {
        assertEquals(launcher.getApplications().size(), 1, "apps="+launcher.getApplications());
        assertNotNull(Iterables.find(launcher.getApplications(), Predicates.instanceOf(TestApplication.class), null), "apps="+launcher.getApplications());
    }
    
    private void assertOnlyLocation(Application app, Class<? extends Location> expectedType) {
        assertEquals(app.getLocations().size(), 1, "locs="+app.getLocations());
        assertNotNull(Iterables.find(app.getLocations(), Predicates.instanceOf(LocalhostMachineProvisioningLocation.class), null), "locs="+app.getLocations());
    }
}

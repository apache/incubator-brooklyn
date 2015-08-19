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
package org.apache.brooklyn.core.mgmt.osgi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarInputStream;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.test.TestResourceUnavailableException;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.osgi.Osgis;
import org.apache.brooklyn.util.core.osgi.Osgis.ManifestHelper;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.maven.MavenArtifact;
import org.apache.brooklyn.util.maven.MavenRetriever;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.commons.io.FileUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** 
 * Tests some assumptions about OSGi behaviour, in standalone mode (not part of brooklyn).
 * See {@link OsgiTestResources} for description of test resources.
 */
public class OsgiStandaloneTest {

    private static final Logger log = LoggerFactory.getLogger(OsgiStandaloneTest.class);

    public static final String BROOKLYN_OSGI_TEST_A_0_1_0_PATH = OsgiTestResources.BROOKLYN_OSGI_TEST_A_0_1_0_PATH;
    public static final String BROOKLYN_OSGI_TEST_A_0_1_0_URL = "classpath:"+BROOKLYN_OSGI_TEST_A_0_1_0_PATH;

    public static final String BROOKLYN_TEST_OSGI_ENTITIES_PATH = OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_PATH;
    public static final String BROOKLYN_TEST_OSGI_ENTITIES_URL = "classpath:"+BROOKLYN_TEST_OSGI_ENTITIES_PATH;
    public static final String BROOKLYN_TEST_OSGI_ENTITIES_NAME = "org.apache.brooklyn.test.resources.osgi.brooklyn-test-osgi-entities";
    public static final String BROOKLYN_TEST_OSGI_ENTITIES_VERSION = "0.1.0";

    protected Framework framework = null;
    private File storageTempDir;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        storageTempDir = Os.newTempDir("osgi-standalone");
        framework = Osgis.newFrameworkStarted(storageTempDir.getAbsolutePath(), true, null);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws BundleException, IOException, InterruptedException {
        tearDownOsgiFramework(framework, storageTempDir);
    }

    public static void tearDownOsgiFramework(Framework framework, File storageTempDir) throws BundleException, InterruptedException, IOException {
        if (framework!=null) {
            framework.stop();
            Assert.assertEquals(framework.waitForStop(1000).getType(), FrameworkEvent.STOPPED);
            framework = null;
        }
        if (storageTempDir!=null) {
            FileUtils.deleteDirectory(storageTempDir);
            storageTempDir = null;
        }
    }

    protected Bundle install(String url) throws BundleException {
        try {
            return Osgis.install(framework, url);
        } catch (Exception e) {
            throw new IllegalStateException("test resources not available; may be an IDE issue, so try a mvn rebuild of this project", e);
        }
    }

    protected Bundle installFromClasspath(String resourceName) throws BundleException {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), resourceName);
        try {
            return Osgis.install(framework, String.format("classpath:%s", resourceName));
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    @Test
    public void testInstallBundle() throws Exception {
        Bundle bundle = installFromClasspath(BROOKLYN_OSGI_TEST_A_0_1_0_PATH);
        checkMath(bundle, 3, 6);
    }

    @Test
    public void testBootBundle() throws Exception {
        Bundle bundle = installFromClasspath(BROOKLYN_TEST_OSGI_ENTITIES_PATH);
        Class<?> bundleCls = bundle.loadClass("brooklyn.osgi.tests.SimpleEntity");
        Assert.assertEquals(Entity.class,  bundle.loadClass(Entity.class.getName()));
        Assert.assertEquals(Entity.class, bundleCls.getClassLoader().loadClass(Entity.class.getName()));
    }

    @Test
    public void testDuplicateBundle() throws Exception {
        MavenArtifact artifact = new MavenArtifact("org.apache.brooklyn", "brooklyn-api", "jar", "0.8.0-SNAPSHOT"); // BROOKLYN_VERSION
        String localUrl = MavenRetriever.localUrl(artifact);
        if ("file".equals(Urls.getProtocol(localUrl))) {
            helperDuplicateBundle(localUrl);
        } else {
            log.warn("Skipping test OsgiStandaloneTest.testDuplicateBundle due to " + artifact + " not available in local repo.");
        }
    }

    @Test(groups="Integration")
    public void testRemoteDuplicateBundle() throws Exception {
        helperDuplicateBundle(MavenRetriever.hostedUrl(new MavenArtifact("org.apache.brooklyn", "brooklyn-api", "jar", "0.8.0-SNAPSHOT"))); // BROOKLYN_VERSION
    }

    public void helperDuplicateBundle(String url) throws Exception {
        //The bundle is already installed from the boot path.
        //Make sure that we still get the initially loaded
        //bundle after trying to install the same version.
        Bundle bundle = install(url);
        Assert.assertTrue(Osgis.isExtensionBundle(bundle));
    }

    @Test
    public void testAMultiplier() throws Exception {
        Bundle bundle = installFromClasspath(BROOKLYN_OSGI_TEST_A_0_1_0_PATH);
        checkMath(bundle, 3, 6);
        setAMultiplier(bundle, 5);
        checkMath(bundle, 3, 15);
    }

    /** run two multiplier tests to ensure that irrespective of order the tests run in, 
     * on a fresh install the multiplier is reset */
    @Test
    public void testANOtherMultiple() throws Exception {
        Bundle bundle = installFromClasspath(BROOKLYN_OSGI_TEST_A_0_1_0_PATH);
        checkMath(bundle, 3, 6);
        setAMultiplier(bundle, 14);
        checkMath(bundle, 3, 42);
    }

    @Test
    public void testGetBundle() throws Exception {
        Bundle bundle = installFromClasspath(BROOKLYN_OSGI_TEST_A_0_1_0_PATH);
        setAMultiplier(bundle, 3);

        // can look it up based on the same location string (no other "location identifier" reference string seems to work here, however) 
        Bundle bundle2 = installFromClasspath(BROOKLYN_OSGI_TEST_A_0_1_0_PATH);
        checkMath(bundle2, 3, 9);
    }

    @Test
    public void testUninstallAndReinstallBundle() throws Exception {
        Bundle bundle = installFromClasspath(BROOKLYN_OSGI_TEST_A_0_1_0_PATH);
        checkMath(bundle, 3, 6);
        setAMultiplier(bundle, 3);
        checkMath(bundle, 3, 9);
        bundle.uninstall();
        
        Bundle bundle2 = installFromClasspath(BROOKLYN_OSGI_TEST_A_0_1_0_PATH);
        checkMath(bundle2, 3, 6);
    }

    protected void checkMath(Bundle bundle, int input, int output) throws Exception {
        Assert.assertNotNull(bundle);
        Class<?> aClass = bundle.loadClass("brooklyn.test.osgi.TestA");
        Object aInst = aClass.newInstance();
        Object result = aClass.getMethod("times", int.class).invoke(aInst, input);
        Assert.assertEquals(result, output);
    }

    protected void setAMultiplier(Bundle bundle, int newMultiplier) throws Exception {
        Assert.assertNotNull(bundle);
        Class<?> aClass = bundle.loadClass("brooklyn.test.osgi.TestA");
        aClass.getField("multiplier").set(null, newMultiplier);
    }

    @Test
    public void testReadAManifest() throws Exception {
        Enumeration<URL> manifests = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
        log.info("Bundles and exported packages:");
        MutableSet<String> allPackages = MutableSet.of();
        while (manifests.hasMoreElements()) {
            ManifestHelper mf = Osgis.ManifestHelper.forManifestContents(Streams.readFullyString( manifests.nextElement().openStream() ));
            List<String> mfPackages = mf.getExportedPackages();
            log.info("  "+mf.getSymbolicNameVersion()+": "+mfPackages);
            allPackages.addAll(mfPackages);
        }
        log.info("Total export package count: "+allPackages.size());
        Assert.assertTrue(allPackages.size()>20, "did not find enough packages"); // probably much larger
        Assert.assertTrue(allPackages.contains(Osgis.class.getPackage().getName()));
    }
    
    @Test
    public void testReadKnownManifest() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), BROOKLYN_TEST_OSGI_ENTITIES_PATH);
        InputStream in = this.getClass().getResourceAsStream(BROOKLYN_TEST_OSGI_ENTITIES_PATH);
        JarInputStream jarIn = new JarInputStream(in);
        ManifestHelper helper = Osgis.ManifestHelper.forManifest(jarIn.getManifest());
        jarIn.close();
        Assert.assertEquals(helper.getVersion().toString(), "0.1.0");
        Assert.assertTrue(helper.getExportedPackages().contains("brooklyn.osgi.tests"));
    }
    
    @Test
    public void testLoadOsgiBundleDependencies() throws Exception {
        Bundle bundle = installFromClasspath(BROOKLYN_TEST_OSGI_ENTITIES_PATH);
        Assert.assertNotNull(bundle);
        Class<?> aClass = bundle.loadClass("brooklyn.osgi.tests.SimpleApplicationImpl");
        Object aInst = aClass.newInstance();
        Assert.assertNotNull(aInst);
    }
    
    @Test
    public void testLoadAbsoluteWindowsResourceWithInstalledOSGi() {
        //Felix installs an additional URL to the system classloader
        //which throws an IllegalArgumentException when passed a
        //windows path. See ExtensionManager.java static initializer.
        String context = "mycontext";
        String dummyPath = "C:\\dummypath";
        ResourceUtils utils = ResourceUtils.create(this, context);
        try {
            utils.getResourceFromUrl(dummyPath);
            Assert.fail("Non-reachable, should throw an exception for non-existing resource.");
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().startsWith("Error getting resource '"+dummyPath+"' for "+context));
        }
    }
    
}

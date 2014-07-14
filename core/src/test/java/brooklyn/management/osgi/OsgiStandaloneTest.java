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
package brooklyn.management.osgi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarInputStream;

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

import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.os.Os;
import brooklyn.util.osgi.Osgis;
import brooklyn.util.osgi.Osgis.ManifestHelper;
import brooklyn.util.stream.Streams;

/** tests some assumptions about OSGi behaviour, in standalone mode (not part of brooklyn).
 * 
 * relies on the following bundles, which exist in the classpath (and contain their sources):
 * 
 * <li>brooklyn-osgi-test-a_0.1.0 -
 *     defines TestA which has a "times" method and a static multiplier field;
 *     we set the multiplier to determine when we are sharing versions and when not
 *     
 *  */
public class OsgiStandaloneTest {


    private static final Logger log = LoggerFactory.getLogger(OsgiStandaloneTest.class);
    
    public static final String BROOKLYN_OSGI_TEST_A_0_1_0_URL = "classpath:/brooklyn/osgi/brooklyn-osgi-test-a_0.1.0.jar";
    
    public static final String BROOKLYN_TEST_OSGI_ENTITIES_PATH = "/brooklyn/osgi/brooklyn-test-osgi-entities.jar";
    public static final String BROOKLYN_TEST_OSGI_ENTITIES_URL = "classpath:"+BROOKLYN_TEST_OSGI_ENTITIES_PATH;
    
    protected Framework framework = null;
    private File storageTempDir;

    @BeforeMethod
    public void setUp() throws Exception {
        storageTempDir = Os.newTempDir("osgi-standalone");
        framework = Osgis.newFrameworkStarted(storageTempDir.getAbsolutePath(), true, null);
    }

    @AfterMethod
    public void tearDown() throws BundleException, IOException, InterruptedException {
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
    
    @Test
    public void testInstallBundle() throws Exception {
        Bundle bundle = install(BROOKLYN_OSGI_TEST_A_0_1_0_URL);
        checkMath(bundle, 3, 6);
    }

    @Test
    public void testAMultiplier() throws Exception {
        Bundle bundle = install(BROOKLYN_OSGI_TEST_A_0_1_0_URL);
        checkMath(bundle, 3, 6);
        setAMultiplier(bundle, 5);
        checkMath(bundle, 3, 15);
    }

    /** run two multiplier tests to ensure that irrespective of order the tests run in, 
     * on a fresh install the multiplier is reset */
    @Test
    public void testANOtherMultiple() throws Exception {
        Bundle bundle = install(BROOKLYN_OSGI_TEST_A_0_1_0_URL);
        checkMath(bundle, 3, 6);
        setAMultiplier(bundle, 14);
        checkMath(bundle, 3, 42);
    }

    @Test
    public void testGetBundle() throws Exception {
        Bundle bundle = install(BROOKLYN_OSGI_TEST_A_0_1_0_URL);
        setAMultiplier(bundle, 3);

        // can look it up based on the same location string (no other "location identifier" reference string seems to work here, however) 
        Bundle bundle2 = install(BROOKLYN_OSGI_TEST_A_0_1_0_URL);
        checkMath(bundle2, 3, 9);
    }

    @Test
    public void testUninstallAndReinstallBundle() throws Exception {
        Bundle bundle = install(BROOKLYN_OSGI_TEST_A_0_1_0_URL);
        checkMath(bundle, 3, 6);
        setAMultiplier(bundle, 3);
        checkMath(bundle, 3, 9);
        bundle.uninstall();
        
        Bundle bundle2 = install(BROOKLYN_OSGI_TEST_A_0_1_0_URL);
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
        InputStream in = this.getClass().getResourceAsStream(BROOKLYN_TEST_OSGI_ENTITIES_PATH);
        JarInputStream jarIn = new JarInputStream(in);
        ManifestHelper helper = Osgis.ManifestHelper.forManifest(jarIn.getManifest());
        jarIn.close();
        Assert.assertEquals(helper.getVersion().toString(), "0.1.0");
        Assert.assertTrue(helper.getExportedPackages().contains("brooklyn.osgi.tests"));
    }
    
    @Test
    public void testLoadOsgiBundleDependencies() throws Exception {
        Bundle bundle = install(BROOKLYN_TEST_OSGI_ENTITIES_URL);
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

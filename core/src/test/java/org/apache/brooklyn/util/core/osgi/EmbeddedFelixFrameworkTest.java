/*
 * Copyright 2015 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.brooklyn.util.core.osgi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarInputStream;

import org.apache.brooklyn.core.mgmt.osgi.OsgiStandaloneTest;
import org.apache.brooklyn.core.mgmt.osgi.OsgiTestResources;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.osgi.ManifestHelper;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.stream.Streams;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/**
 *
 * @author Ciprian Ciubotariu <cheepeero@gmx.net>
 */
public class EmbeddedFelixFrameworkTest {

    private static final Logger log = LoggerFactory.getLogger(OsgiStandaloneTest.class);

    public static final String BROOKLYN_TEST_OSGI_ENTITIES_PATH = OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_PATH;

    protected Framework framework = null;
    private File storageTempDir;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        storageTempDir = Os.newTempDir("osgi-standalone");
        framework = Osgis.getFramework(storageTempDir.getAbsolutePath(), false);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws BundleException, IOException, InterruptedException {
        OsgiStandaloneTest.tearDownOsgiFramework(framework, storageTempDir);
    }

    @org.testng.annotations.Test
    public void testReadAManifest() throws Exception {
        Enumeration<URL> manifests = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
        log.info("Bundles and exported packages:");
        MutableSet<String> allPackages = MutableSet.of();
        while (manifests.hasMoreElements()) {
            ManifestHelper mf = ManifestHelper.forManifestContents(Streams.readFullyString(manifests.nextElement().openStream()));
            List<String> mfPackages = mf.getExportedPackages();
            log.info("  " + mf.getSymbolicNameVersion() + ": " + mfPackages);
            allPackages.addAll(mfPackages);
        }
        log.info("Total export package count: " + allPackages.size());
        Assert.assertTrue(allPackages.size() > 20, "did not find enough packages"); // probably much larger
        Assert.assertTrue(allPackages.contains(Osgis.class.getPackage().getName()));
    }

    @org.testng.annotations.Test
    public void testReadKnownManifest() throws Exception {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), BROOKLYN_TEST_OSGI_ENTITIES_PATH);
        InputStream in = this.getClass().getResourceAsStream(BROOKLYN_TEST_OSGI_ENTITIES_PATH);
        JarInputStream jarIn = new JarInputStream(in);
        ManifestHelper helper = ManifestHelper.forManifest(jarIn.getManifest());
        jarIn.close();
        Assert.assertEquals(helper.getVersion().toString(), "0.1.0");
        Assert.assertTrue(helper.getExportedPackages().contains("org.apache.brooklyn.test.osgi.entities"));
    }

}

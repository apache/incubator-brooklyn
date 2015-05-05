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
package brooklyn;

import static org.testng.Assert.assertEquals;

import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.text.Strings;

public class BrooklynVersionTest {

    private static final Logger log = LoggerFactory.getLogger(BrooklynVersionTest.class);

    @Test
    public void testGetVersion() {
        assertEquals(BrooklynVersion.get(), BrooklynVersion.INSTANCE.getVersion());
    }

    @Test
    public void testGetHardcodedClasspathVersion() {
        @SuppressWarnings("deprecation")
        String v = BrooklynVersion.INSTANCE.getVersionFromClasspath();
        Assert.assertTrue(BrooklynVersion.get().equals(v) || "0.0.0-SNAPSHOT".equals(v), v);
    }

    @Test
    public void testGetFromMaven() {
        String v = BrooklynVersion.INSTANCE.getVersionFromMavenProperties();
        Assert.assertTrue(v == null || BrooklynVersion.get().equals(v), v);
    }

    @Test
    public void testGetFromOsgi() {
        String v = BrooklynVersion.INSTANCE.getVersionFromOsgiManifest();
        Assert.assertTrue(v == null || BrooklynVersion.get().equals(v), v);
    }

    @Test
    public void testGetOsgiSha1() {
        String sha1 = BrooklynVersion.INSTANCE.getSha1FromOsgiManifest();
        log.info("sha1: " + sha1);
        if (Strings.isNonBlank(sha1) || BrooklynVersion.isDevelopmentEnvironment())
            return;
        // we might not have a SHA1 if it's a standalone (non-git) source build; just log warn in that case
        log.warn("This build does not have git SHA1 information.");
        // (can't assert anything, except that sha1 lookup doesn't NPE)
    }

    @Test
    public void testDevEnv() {
        URL sp = getClass().getClassLoader().getResource("brooklyn/config/sample.properties");
        if (sp == null) Assert.fail("Can't find test resources");

        log.info("Test for dev env: " + "Dev env? " + BrooklynVersion.isDevelopmentEnvironment() + "; path " + sp);
        boolean testResourcePathInClasses = sp.getPath().endsWith("classes/brooklyn/config/sample.properties");
        Assert.assertEquals(testResourcePathInClasses, BrooklynVersion.isDevelopmentEnvironment(),
                "Dev env? " + BrooklynVersion.isDevelopmentEnvironment() + "; but resource path: " + sp);
    }

}

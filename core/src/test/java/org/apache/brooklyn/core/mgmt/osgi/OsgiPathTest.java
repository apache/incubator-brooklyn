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

import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.server.BrooklynServerConfig;
import org.apache.brooklyn.core.server.BrooklynServerPaths;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.text.Identifiers;
import org.osgi.framework.BundleException;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;


/** 
 * Tests that OSGi entities load correctly and have the right catalog information set.
 * Note further tests done elsewhere using CAMP YAML (referring to methods in this class).
 */
public class OsgiPathTest {
   
    protected LocalManagementContext mgmt = null;

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws BundleException, IOException, InterruptedException {
        if (mgmt!=null) Entities.destroyAll(mgmt);
    }
    
    @Test(groups="Integration") // integration only because OSGi takes ~200ms
    public void testOsgiPathDefault() {
        mgmt = LocalManagementContextForTests.builder(true).disableOsgi(false).build();
        String path = BrooklynServerPaths.getOsgiCacheDir(mgmt).getAbsolutePath();
        Assert.assertTrue(path.startsWith(BrooklynServerPaths.getMgmtBaseDir(mgmt)), path);
        Assert.assertTrue(path.contains(mgmt.getManagementNodeId()), path);
        
        assertExistsThenIsCleaned(path);
    }

    @Test(groups="Integration") // integration only because OSGi takes ~200ms
    public void testOsgiPathCustom() {
        BrooklynProperties bp = BrooklynProperties.Factory.newEmpty();
        String randomSeg = "osgi-test-"+Identifiers.makeRandomId(4);
        bp.put(BrooklynServerConfig.OSGI_CACHE_DIR, "${brooklyn.os.tmpdir}"+"/"+randomSeg+"/"+"${brooklyn.mgmt.node.id}");
        mgmt = LocalManagementContextForTests.builder(true).disableOsgi(false).useProperties(bp).build();
        String path = BrooklynServerPaths.getOsgiCacheDir(mgmt).getAbsolutePath();
        Os.deleteOnExitRecursivelyAndEmptyParentsUpTo(new File(path), new File(Os.tmp()+"/"+randomSeg));
        
        Assert.assertTrue(path.startsWith(Os.tmp()), path);
        Assert.assertTrue(path.contains(mgmt.getManagementNodeId()), path);
        
        assertExistsThenIsCleaned(path);
    }

    @Test(groups="Integration") // integration only because OSGi takes ~200ms
    public void testOsgiPathCustomWithoutNodeIdNotCleaned() {
        BrooklynProperties bp = BrooklynProperties.Factory.newEmpty();
        String randomSeg = "osgi-test-"+Identifiers.makeRandomId(4);
        bp.put(BrooklynServerConfig.OSGI_CACHE_DIR, "${brooklyn.os.tmpdir}"+"/"+randomSeg+"/"+"sample");
        mgmt = LocalManagementContextForTests.builder(true).disableOsgi(false).useProperties(bp).build();
        String path = BrooklynServerPaths.getOsgiCacheDir(mgmt).getAbsolutePath();
        Os.deleteOnExitRecursivelyAndEmptyParentsUpTo(new File(path), new File(Os.tmp()+"/"+randomSeg));
        
        Assert.assertTrue(path.startsWith(Os.tmp()), path);
        Assert.assertFalse(path.contains(mgmt.getManagementNodeId()), path);
        
        assertExistsThenCorrectCleanedBehaviour(path, false);
    }

    private void assertExistsThenIsCleaned(String path) {
        assertExistsThenCorrectCleanedBehaviour(path, true);
    }
    private void assertExistsThenCorrectCleanedBehaviour(String path, boolean shouldBeCleanAfterwards) {
        Assert.assertTrue(new File(path).exists(), "OSGi cache "+path+" should exist when in use");
        Entities.destroyAll(mgmt);
        mgmt = null;
        if (shouldBeCleanAfterwards)
            Assert.assertFalse(new File(path).exists(), "OSGi cache "+path+" should be cleaned after");
        else
            Assert.assertTrue(new File(path).exists(), "OSGi cache "+path+" should NOT be cleaned after");
    }

}

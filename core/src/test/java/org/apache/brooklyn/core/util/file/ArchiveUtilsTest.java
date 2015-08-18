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
package org.apache.brooklyn.core.util.file;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.Map;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;

import org.apache.brooklyn.core.util.ResourceUtils;
import org.apache.brooklyn.core.util.file.ArchiveBuilder;
import org.apache.brooklyn.core.util.file.ArchiveUtils;
import org.apache.brooklyn.location.basic.SshMachineLocation;
import org.apache.brooklyn.util.os.Os;

import com.google.api.client.repackaged.com.google.common.base.Joiner;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

// Test are integration, because relies on ssh/scp via SshMachineLocation
public class ArchiveUtilsTest extends BrooklynAppUnitTestSupport {
    
    private SshMachineLocation machine;
    private ResourceUtils resourceUtils;

    private Map<String, String> archiveContents = ImmutableMap.of("a.txt", "mya");
    private File destDir;
    private File origZip;
    private File origJar;

    @BeforeClass(alwaysRun=true)
    public void setUpClass() throws Exception {
        origZip = newZip(archiveContents);
        origJar = Os.newTempFile(ArchiveUtilsTest.class, ".jar");
        Files.copy(origZip, origJar);
    }
    
    @AfterClass(alwaysRun=true)
    public void tearDownClass() throws Exception {
        if (origZip != null) origZip.delete();
        if (origJar != null) origJar.delete();
    }

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        machine = app.newLocalhostProvisioningLocation().obtain();
        resourceUtils = ResourceUtils.create(ArchiveUtilsTest.class);
        destDir = Os.newTempDir(getClass().getSimpleName());
    }
    
    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (destDir != null) Os.deleteRecursively(destDir);
    }
    
    @Test(groups="Integration")
    public void testDeployZipWithNoOptionalArgsSupplied() throws Exception {
        boolean result = ArchiveUtils.deploy(resourceUtils, ImmutableMap.<String, Object>of(), origZip.getAbsolutePath(), machine, destDir.getAbsolutePath(), true, null, null);
        assertTrue(result);
        assertFilesEqual(new File(destDir, origZip.getName()), origZip);
        assertSubFilesEqual(destDir, archiveContents);
    }
    
    @Test(groups="Integration")
    public void testDeployZipDeletingArchiveAfterUnpack() throws Exception {
        boolean result = ArchiveUtils.deploy(resourceUtils, ImmutableMap.<String, Object>of(), origZip.getAbsolutePath(), machine, destDir.getAbsolutePath(), false, null, null);
        assertTrue(result);
        assertFalse(new File(destDir, origZip.getName()).exists());
        assertSubFilesEqual(destDir, archiveContents);
    }
    
    @Test(groups="Integration")
    public void testDeployJarNotUnpacked() throws Exception {
        ArchiveUtils.deploy(origJar.getAbsolutePath(), machine, destDir.getAbsolutePath());
        assertFilesEqual(new File(destDir, origJar.getName()), origJar);
    }
    
    @Test(groups="Integration")
    public void testDeployExplicitDestFile() throws Exception {
        String destFile = "custom-destFile.jar";
        ArchiveUtils.deploy(origJar.getAbsolutePath(), machine, destDir.getAbsolutePath(), destFile);
        assertFilesEqual(new File(destDir, destFile), origJar);
    }
    
    private File newZip(Map<String, String> files) throws Exception {
        File parentDir = Os.newTempDir(getClass().getSimpleName()+"-archive");
        for (Map.Entry<String, String> entry : files.entrySet()) {
            File subFile = new File(Os.mergePaths(parentDir.getAbsolutePath(), entry.getKey()));
            subFile.getParentFile().mkdirs();
            Files.write(entry.getValue(), subFile, Charsets.UTF_8);
        }
        return ArchiveBuilder.zip().addDirContentsAt(parentDir, ".").create();
    }
    
    private void assertFilesEqual(File f1, File f2) throws Exception {
        byte[] bytes1 = Files.asByteSource(f1).read();
        byte[] bytes2 = Files.asByteSource(f1).read();
        assertEquals(bytes1, bytes2, "f1="+f1+"; f2="+f2);
    }
    
    private void assertSubFilesEqual(File parentDir, Map<String, String> files) throws Exception {
        for (Map.Entry<String, String> entry : archiveContents.entrySet()) {
            File subFile = new File(Os.mergePaths(parentDir.getAbsolutePath(), entry.getKey()));
            assertEquals(Joiner.on("\n").join(Files.readLines(subFile, Charsets.UTF_8)), entry.getValue());
        }
    }
}

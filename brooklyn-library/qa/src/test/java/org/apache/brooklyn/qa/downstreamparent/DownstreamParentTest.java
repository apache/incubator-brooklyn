/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.qa.downstreamparent;

import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.brooklyn.util.net.Networking;
import org.apache.maven.it.Verifier;
import org.apache.maven.shared.utils.io.FileUtils;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

public class DownstreamParentTest {

    private static final String PROJECTS_DIR = "src/test/projects";
    private static final String WORK_DIR = "target/ut/";

    /**
     * Asserts that a trivial project using brooklyn-downstream-parent can be
     * loaded into Brooklyn's catalogue and its entities deployed.
     */
    @Test(groups = "Integration")
    public void testDownstreamProjectsCanBeLoadedIntoBrooklynCatalogByDefault() throws Exception {
        int port = Networking.nextAvailablePort(57000);
        File dir = getBasedir("downstream-parent-test");
        Verifier verifier = new Verifier(dir.getAbsolutePath());
        verifier.setMavenDebug(true);
        verifier.executeGoal("post-integration-test", ImmutableMap.of(
                "bindPort", String.valueOf(port)));
        verifier.verifyErrorFreeLog();
        verifier.verifyTextInLog("Hello from the init method of the HelloEntity");
    }

    /** Replicates the behaviour of getBasedir in JUnit's TestResources class */
    public File getBasedir(String project) throws IOException {
        File src = (new File(PROJECTS_DIR, project)).getCanonicalFile();
        assertTrue(src.isDirectory(), "Test project directory does not exist: " + src.getPath());
        File basedir = (new File(WORK_DIR, getClass().getSimpleName() + "_" + project)).getCanonicalFile();
        FileUtils.deleteDirectory(basedir);
        assertTrue(basedir.mkdirs(), "Test project working directory created");
        FileUtils.copyDirectoryStructure(src, basedir);
        return basedir;
    }
}

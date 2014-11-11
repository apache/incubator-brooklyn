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
package brooklyn.launcher;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;

import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServerPaths;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.FileBasedObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.os.Os;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Joiner;
import com.google.common.io.Files;

public class BrooklynLauncherRebindTestToFiles extends BrooklynLauncherRebindTestFixture {

    protected String newTempPersistenceContainerName() {
        File persistenceDirF = Files.createTempDir();
        Os.deleteOnExitRecursively(persistenceDirF);
        return persistenceDirF.getAbsolutePath();
    }
    
    protected String badContainerName() {
        return "/path/does/not/exist/"+Identifiers.makeRandomId(4);
    }
    
    protected void checkPersistenceContainerNameIs(String expected) {
        String expectedFqp = new File(Os.tidyPath(expected)).getAbsolutePath();
        assertEquals(getPersistenceDir(lastMgmt()).getAbsolutePath(), expectedFqp);
    }

    static File getPersistenceDir(ManagementContext managementContext) {
        BrooklynMementoPersisterToObjectStore persister = (BrooklynMementoPersisterToObjectStore)managementContext.getRebindManager().getPersister();
        FileBasedObjectStore store = (FileBasedObjectStore)persister.getObjectStore();
        return store.getBaseDir();
    }

    protected void checkPersistenceContainerNameIsDefault() {
        String expected = BrooklynServerPaths.newMainPersistencePathResolver(BrooklynProperties.Factory.newEmpty()).location(null).dir(null).resolve();
        checkPersistenceContainerNameIs(expected);
    }

    @Test
    public void testPersistenceFailsIfIsFile() throws Exception {
        File tempF = File.createTempFile("test-"+JavaClassNames.niceClassAndMethod(), ".not_dir");
        tempF.deleteOnExit();
        String tempFileName = tempF.getAbsolutePath();
        
        try {
            runRebindFails(PersistMode.AUTO, tempFileName, "must not be a file");
            runRebindFails(PersistMode.REBIND, tempFileName, "must not be a file");
            runRebindFails(PersistMode.CLEAN, tempFileName, "must not be a file");
        } finally {
            new File(tempFileName).delete();
        }
    }
    
    @Test
    public void testPersistenceFailsIfNotWritable() throws Exception {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class);
        populatePersistenceDir(persistenceDir, appSpec);
        new File(persistenceDir).setWritable(false);
        try {
            runRebindFails(PersistMode.AUTO, persistenceDir, "not writable");
            runRebindFails(PersistMode.REBIND, persistenceDir, "not writable");
            runRebindFails(PersistMode.CLEAN, persistenceDir, "not writable");
        } finally {
            new File(persistenceDir).setWritable(true);
        }
    }

    @Test
    public void testPersistenceFailsIfNotReadable() throws Exception {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class);
        populatePersistenceDir(persistenceDir, appSpec);
        new File(persistenceDir).setReadable(false);
        try {
            runRebindFails(PersistMode.AUTO, persistenceDir, "not readable");
            runRebindFails(PersistMode.REBIND, persistenceDir, "not readable");
            runRebindFails(PersistMode.CLEAN, persistenceDir, "not readable");
        } finally {
            new File(persistenceDir).setReadable(true);
        }
    }

    @Test(groups="Integration")
    public void testCopyPersistedState() throws Exception {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class);
        populatePersistenceDir(persistenceDir, appSpec);

        File destinationDir = Files.createTempDir();
        String destination = destinationDir.getAbsolutePath();
        String destinationLocation = null; // i.e. file system, rather than object store
        try {
            // Auto will rebind if the dir exists
            BrooklynLauncher launcher = newLauncherDefault(PersistMode.AUTO)
                    .highAvailabilityMode(HighAvailabilityMode.MASTER)
                    .webconsole(false);
            launcher.copyPersistedState(destination, destinationLocation);
            launcher.terminate();
            
            File entities = new File(Os.mergePaths(destination), "entities");
            assertTrue(entities.isDirectory(), "entities directory should exist");
            assertEquals(entities.listFiles().length, 1, "entities directory should contain one file (contained: "+
                    Joiner.on(", ").join(entities.listFiles()) +")");

            File nodes = new File(Os.mergePaths(destination, "nodes"));
            assertTrue(nodes.isDirectory(), "nodes directory should exist");
            assertNotEquals(nodes.listFiles().length, 0, "nodes directory should not be empty");

            // Should now have a usable copy in the destinationDir
            // Auto will rebind if the dir exists
            newLauncherDefault(PersistMode.AUTO)
                    .webconsole(false)
                    .persistenceDir(destinationDir)
                    .start();
            assertOnlyApp(lastMgmt(), TestApplication.class);
            
        } finally {
            Os.deleteRecursively(destinationDir);
        }
    }
}

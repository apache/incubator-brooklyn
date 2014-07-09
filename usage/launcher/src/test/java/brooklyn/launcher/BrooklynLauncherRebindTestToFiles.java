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

import java.io.File;

import org.testng.annotations.Test;

import brooklyn.config.BrooklynServerConfig;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.FileBasedObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.os.Os;
import brooklyn.util.text.Identifiers;

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
        assertEquals(getPersistenceDir(lastMgmt()).getAbsolutePath(), expected);
    }

    static File getPersistenceDir(ManagementContext managementContext) {
        BrooklynMementoPersisterToObjectStore persister = (BrooklynMementoPersisterToObjectStore)managementContext.getRebindManager().getPersister();
        FileBasedObjectStore store = (FileBasedObjectStore)persister.getObjectStore();
        return store.getBaseDir();
    }

    protected void checkPersistenceContainerNameIsDefault() {
        checkPersistenceContainerNameIs(BrooklynServerConfig.DEFAULT_PERSISTENCE_DIR_FOR_FILESYSTEM);
    }

    @Test
    public void testPersistenceFailsIfIsFile() throws Exception {
        File tempF = File.createTempFile("test-"+JavaClassNames.niceClassAndMethod(), ".not_dir");
        tempF.deleteOnExit();
        String tempFileName = tempF.getAbsolutePath();
        
        try {
            runRebindFails(PersistMode.AUTO, tempFileName, "not a directory");
            runRebindFails(PersistMode.REBIND, tempFileName, "not a directory");
            runRebindFails(PersistMode.CLEAN, tempFileName, "not a directory");
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

}

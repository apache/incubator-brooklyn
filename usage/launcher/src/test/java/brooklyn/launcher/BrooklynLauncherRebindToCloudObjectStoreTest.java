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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServerPaths;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.jclouds.BlobStoreTest;
import brooklyn.entity.rebind.persister.jclouds.JcloudsBlobStoreBasedObjectStore;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.BrooklynMementoRawData;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.os.Os;
import brooklyn.util.text.Identifiers;

@Test(groups="Live")
public class BrooklynLauncherRebindToCloudObjectStoreTest extends BrooklynLauncherRebindTestFixture {

    // FIXME BrooklynLauncherRebindToCloudObjectStoreTest.testCleanDoesNotRebindToExistingApp failed:
    //     apps=[Application[mDNfOA7w]] expected [true] but found [false]
    // Should it really delete everything in the bucket?! Only if we can back up first!

    // FIXME brooklyn.util.exceptions.FatalRuntimeException: Error rebinding to persisted state: Writes not allowed in brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore@7d2f7563
    //     at brooklyn.launcher.BrooklynLauncher.persistState(BrooklynLauncher.java:502)
    //     at brooklyn.launcher.BrooklynLauncherRebindToCloudObjectStoreTest.testCopyPersistedState(BrooklynLauncherRebindToCloudObjectStoreTest.java:144)
    // Presumably a previous run wasn't tearing down properly, so it joined as a standby rather than being master?! 
    
    { persistenceLocationSpec = BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC; }

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        persistenceDir = newTempPersistenceContainerName();
    }

    @Override
    protected BrooklynLauncher newLauncherBase() {
        return super.newLauncherBase().persistenceLocation(persistenceLocationSpec);
    }
    
    protected LocalManagementContextForTests newManagementContextForTests(BrooklynProperties props) {
        BrooklynProperties p2 = BrooklynProperties.Factory.newDefault();
        if (props!=null) p2.putAll(props);
        return new LocalManagementContextForTests(p2);
    }

    @Override
    protected String newTempPersistenceContainerName() {
        return "test-"+JavaClassNames.callerStackElement(0).getClassName()+"-"+Identifiers.makeRandomId(4);
    }
    
    protected String badContainerName() {
        return "container-does-not-exist-"+Identifiers.makeRandomId(4);
    }
    
    protected void checkPersistenceContainerNameIs(String expected) {
        assertEquals(getPersistenceContainerName(lastMgmt()), expected);
    }

    static String getPersistenceContainerName(ManagementContext managementContext) {
        BrooklynMementoPersisterToObjectStore persister = (BrooklynMementoPersisterToObjectStore)managementContext.getRebindManager().getPersister();
        JcloudsBlobStoreBasedObjectStore store = (JcloudsBlobStoreBasedObjectStore)persister.getObjectStore();
        return store.getContainerName();
    }

    protected void checkPersistenceContainerNameIsDefault() {
        checkPersistenceContainerNameIs(BrooklynServerPaths.DEFAULT_PERSISTENCE_CONTAINER_NAME);
    }

    @Override @Test(groups="Live")
    public void testRebindsToExistingApp() throws Exception {
        super.testRebindsToExistingApp();
    }

    @Override @Test(groups="Live")
    public void testRebindCanAddNewApps() throws Exception {
        super.testRebindCanAddNewApps();
    }

    @Override @Test(groups="Live")
    public void testAutoRebindsToExistingApp() throws Exception {
        super.testAutoRebindsToExistingApp();
    }

    // TODO Marked as work-in-progress because "clean" does not backup and then clean out the existing
    // object store's bucket. Unclear what best behaviour there should be: should we really delete
    // the data?! We better be confident about our backup!
    @Override @Test(groups={"Live", "WIP"})
    public void testCleanDoesNotRebindToExistingApp() throws Exception {
        super.testCleanDoesNotRebindToExistingApp();
    }

    @Override @Test(groups="Live")
    public void testAutoRebindCreatesNewIfEmptyDir() throws Exception {
        super.testAutoRebindCreatesNewIfEmptyDir();
    }

    @Override @Test(groups="Live")
    public void testRebindRespectsPersistenceDirSetInProperties() throws Exception {
        super.testRebindRespectsPersistenceDirSetInProperties();
    }

    @Override @Test(groups="Live")
    public void testRebindRespectsDefaultPersistenceDir() throws Exception {
        super.testRebindRespectsDefaultPersistenceDir();
    }

    @Override @Test(groups="Live")
    public void testPersistenceFailsIfNoDir() throws Exception {
        super.testPersistenceFailsIfNoDir();
    }

    @Override @Test(groups="Live")
    public void testExplicitRebindFailsIfEmpty() throws Exception {
        super.testExplicitRebindFailsIfEmpty();
    }

    // TODO Remove duplication from BrooklynLauncherRebindTestToFiles.testCopyPersistedState()
    @Test(groups="Live")
    public void testCopyPersistedState() throws Exception {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class);
        populatePersistenceDir(persistenceDir, appSpec);
        
        String destinationDir = newTempPersistenceContainerName();
        String destinationLocation = persistenceLocationSpec;
        try {
            // Auto will rebind if the dir exists
            BrooklynLauncher launcher = newLauncherDefault(PersistMode.AUTO)
                    .webconsole(false)
                    .persistenceLocation(persistenceLocationSpec);
            BrooklynMementoRawData memento = launcher.retrieveState();
            launcher.persistState(memento, destinationDir, destinationLocation);
            launcher.terminate();
            
            assertEquals(memento.getEntities().size(), 1, "entityMementos="+memento.getEntities().keySet());
            
            // Should now have a usable copy in the destionationDir
            // Auto will rebind if the dir exists
            newLauncherDefault(PersistMode.AUTO)
                    .webconsole(false)
                    .persistenceDir(destinationDir)
                    .persistenceLocation(destinationLocation)
                    .start();
            assertOnlyApp(lastMgmt(), TestApplication.class);
            
        } finally {
            Os.deleteRecursively(destinationDir);
        }
    }
}

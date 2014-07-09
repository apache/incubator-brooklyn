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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.entity.Application;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.test.Asserts;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableList;
import brooklyn.util.exceptions.FatalConfigurationRuntimeException;
import brooklyn.util.time.Duration;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public abstract class BrooklynLauncherRebindTestFixture {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(BrooklynLauncherRebindTestFixture.class);
    
    protected String persistenceDir;
    protected String persistenceLocationSpec;
    protected List<BrooklynLauncher> launchers = MutableList.of();
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        persistenceDir = newTempPersistenceContainerName();
    }
    
    protected abstract String newTempPersistenceContainerName();

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        for (BrooklynLauncher l: launchers) {
            l.terminate();
            PersistenceObjectStore store = getPersistenceStore(l.getServerDetails().getManagementContext());
            if (store!=null) store.deleteCompletely();
        }
    }

    protected BrooklynLauncher newLauncherBase() {
        BrooklynLauncher l = BrooklynLauncher.newInstance()
            .webconsole(false);
        launchers.add(l);
        return l;
    }
    protected BrooklynLauncher newLauncherDefault(PersistMode mode) {
        return newLauncherBase()
                .managementContext(newManagementContextForTests(null))
                .persistMode(mode)
                .persistenceDir(persistenceDir)
                .persistPeriod(Duration.millis(10));
    }
    protected LocalManagementContextForTests newManagementContextForTests(BrooklynProperties props) {
        if (props==null)
            return new LocalManagementContextForTests();
        else
            return new LocalManagementContextForTests(props);
    }

    protected ManagementContext lastMgmt() {
        return Iterables.getLast(launchers).getServerDetails().getManagementContext();
    }
    
    @Test
    public void testRebindsToExistingApp() throws Exception {
        populatePersistenceDir(persistenceDir, EntitySpec.create(TestApplication.class).displayName("myorig"));
        
        // Rebind to the app we just started last time
        
        newLauncherDefault(PersistMode.REBIND).start();
        
        assertOnlyApp(lastMgmt(), TestApplication.class);
        assertNotNull(Iterables.find(lastMgmt().getApplications(), EntityPredicates.displayNameEqualTo("myorig"), null), "apps="+lastMgmt().getApplications());
    }

    @Test
    public void testRebindCanAddNewApps() throws Exception {
        populatePersistenceDir(persistenceDir, EntitySpec.create(TestApplication.class).displayName("myorig"));
        
        // Rebind to the app we started last time
        newLauncherDefault(PersistMode.REBIND)
                .application(EntitySpec.create(TestApplication.class).displayName("mynew"))
                .start();
        
        // New app was added, and orig app was rebound
        assertEquals(lastMgmt().getApplications().size(), 2, "apps="+lastMgmt().getApplications());
        assertNotNull(Iterables.find(lastMgmt().getApplications(), EntityPredicates.displayNameEqualTo("mynew"), null), "apps="+lastMgmt().getApplications());

        // And subsequently can create new apps
        StartableApplication app3 = lastMgmt().getEntityManager().createEntity(
                EntitySpec.create(TestApplication.class).displayName("mynew2"));
        app3.start(ImmutableList.<Location>of());
    }

    @Test
    public void testAutoRebindsToExistingApp() throws Exception {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class);
        populatePersistenceDir(persistenceDir, appSpec);
        
        // Auto will rebind if the dir exists
        newLauncherDefault(PersistMode.AUTO).start();
        
        assertOnlyApp(lastMgmt(), TestApplication.class);
    }

    @Test
    public void testCleanDoesNotRebindToExistingApp() throws Exception {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class);
        populatePersistenceDir(persistenceDir, appSpec);
        
        // Auto will rebind if the dir exists
        newLauncherDefault(PersistMode.CLEAN).start();
        
        assertTrue(lastMgmt().getApplications().isEmpty(), "apps="+lastMgmt().getApplications());
    }

    @Test
    public void testAutoRebindCreatesNewIfEmptyDir() throws Exception {
        // Auto will rebind if the dir exists
        newLauncherDefault(PersistMode.AUTO)
                .application(EntitySpec.create(TestApplication.class))
                .start();
        
        assertOnlyApp(lastMgmt(), TestApplication.class);
        assertMementoContainerNonEmptyForTypeEventually("entities");
    }

    @Test
    public void testRebindRespectsPersistenceDirSetInProperties() throws Exception {
        String persistenceDir2 = newTempPersistenceContainerName();
        
        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newDefault();
        brooklynProperties.put(BrooklynServerConfig.PERSISTENCE_DIR, persistenceDir2);
        LocalManagementContextForTests mgmt = newManagementContextForTests(brooklynProperties);
        
        // Rebind to the app we started last time
        newLauncherBase()
                .persistMode(PersistMode.AUTO)
                .persistPeriod(Duration.millis(10))
                .managementContext(mgmt)
                .start();
        
        checkPersistenceContainerNameIs(persistenceDir2);
    }

    // assumes default persistence dir is rebindable
    @Test(groups="Integration")
    public void testRebindRespectsDefaultPersistenceDir() throws Exception {
        newLauncherDefault(PersistMode.AUTO)
                .persistenceDir((String)null)
                .start();
        
        checkPersistenceContainerNameIsDefault();
    }
    
    protected abstract void checkPersistenceContainerNameIsDefault();
    protected abstract void checkPersistenceContainerNameIs(String expected);

    @Test
    public void testPersistenceFailsIfNoDir() throws Exception {
        runRebindFails(PersistMode.REBIND, badContainerName(), "does not exist");
    }

    protected abstract String badContainerName();

    @Test
    public void testExplicitRebindFailsIfEmpty() throws Exception {
        runRebindFails(PersistMode.REBIND, persistenceDir, "directory is empty");
    }

    protected void runRebindFails(PersistMode persistMode, String dir, String errmsg) throws Exception {
        try {
            newLauncherDefault(persistMode)
                    .persistenceDir(dir)
                    .start();
        } catch (FatalConfigurationRuntimeException e) {
            if (!e.toString().contains(errmsg)) {
                throw e;
            }
        }
    }

    protected void populatePersistenceDir(String dir, EntitySpec<? extends StartableApplication> appSpec) throws Exception {
        BrooklynLauncher launcher = newLauncherDefault(PersistMode.CLEAN)
                .persistenceDir(dir)
                .application(appSpec)
                .start();
        launcher.terminate();
        launcher = null;
        assertMementoContainerNonEmptyForTypeEventually("entities");
    }
    
    protected void assertOnlyApp(ManagementContext managementContext, Class<? extends Application> expectedType) {
        assertEquals(managementContext.getApplications().size(), 1, "apps="+managementContext.getApplications());
        assertNotNull(Iterables.find(managementContext.getApplications(), Predicates.instanceOf(TestApplication.class), null), "apps="+managementContext.getApplications());
    }
    
    protected void assertMementoContainerNonEmptyForTypeEventually(final String type) {
        Asserts.succeedsEventually(ImmutableMap.of("timeout", Duration.TEN_SECONDS), new Runnable() {
            @Override public void run() {
                getPersistenceStore(lastMgmt()).listContentsWithSubPath(type);
            }});
    }

    static PersistenceObjectStore getPersistenceStore(ManagementContext managementContext) {
        if (managementContext==null) return null;
        BrooklynMementoPersisterToObjectStore persister = (BrooklynMementoPersisterToObjectStore)managementContext.getRebindManager().getPersister();
        if (persister==null) return null;
        return persister.getObjectStore();
    }

}

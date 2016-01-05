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
package org.apache.brooklyn.launcher;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.ha.HighAvailabilityMode;
import org.apache.brooklyn.api.mgmt.ha.ManagementPlaneSyncRecordPersister;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.mgmt.persist.PersistMode;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestUtils;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.time.Duration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class BrooklynLauncherHighAvailabilityTest {

    private static final Logger log = LoggerFactory.getLogger(BrooklynLauncherHighAvailabilityTest.class);
    
    private static final Duration TIMEOUT = Duration.THIRTY_SECONDS;
    
    private BrooklynLauncher primary;
    private BrooklynLauncher secondary;
    private BrooklynLauncher tertiary;
    private File persistenceDir;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        persistenceDir = Files.createTempDir();
        Os.deleteOnExitRecursively(persistenceDir);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (primary != null) primary.terminate();
        primary = null;
        if (secondary != null) secondary.terminate();
        secondary = null;
        if (tertiary != null) tertiary.terminate();
        tertiary = null;
        if (persistenceDir != null) RebindTestUtils.deleteMementoDir(persistenceDir);
        persistenceDir = null;
    }
    
    @Test
    public void testStandbyTakesOverWhenPrimaryTerminatedGracefully() throws Exception {
        doTestStandbyTakesOver(true);
    }

    @Test(invocationCount=10, groups="Integration")
    /** test issues with termination and promotion; 
     * previously we got FileNotFound errors, though these should be fixed with
     * the various PersistenceObjectStore prepare methods */
    public void testStandbyTakesOverWhenPrimaryTerminatedGracefullyManyTimes() throws Exception {
        testStandbyTakesOverWhenPrimaryTerminatedGracefully();
    }

    @Test(groups="Integration") // because slow waiting for timeouts to promote standbys
    public void testStandbyTakesOverWhenPrimaryFails() throws Exception {
        doTestStandbyTakesOver(false);
    }
    
    protected void doTestStandbyTakesOver(boolean stopGracefully) throws Exception {
        log.info("STARTING standby takeover test");
        primary = BrooklynLauncher.newInstance();
        primary.webconsole(false)
                .brooklynProperties(LocalManagementContextForTests.setEmptyCatalogAsDefault(BrooklynProperties.Factory.newEmpty()))
                .highAvailabilityMode(HighAvailabilityMode.AUTO)
                .persistMode(PersistMode.AUTO)
                .persistenceDir(persistenceDir)
                .persistPeriod(Duration.millis(10))
                .haHeartbeatPeriod(Duration.millis(10))
                .haHeartbeatTimeout(Duration.millis(1000))
                .application(EntitySpec.create(TestApplication.class))
                .start();
        ManagementContext primaryManagementContext = primary.getServerDetails().getManagementContext();
        log.info("started mgmt primary "+primaryManagementContext);
        
        assertOnlyApp(primary.getServerDetails().getManagementContext(), TestApplication.class);
        primaryManagementContext.getRebindManager().getPersister().waitForWritesCompleted(TIMEOUT);
        
        // Secondary will come up as standby
        secondary = BrooklynLauncher.newInstance();
        secondary.webconsole(false)
                .brooklynProperties(LocalManagementContextForTests.setEmptyCatalogAsDefault(BrooklynProperties.Factory.newEmpty()))
                .highAvailabilityMode(HighAvailabilityMode.AUTO)
                .persistMode(PersistMode.AUTO)
                .persistenceDir(persistenceDir)
                .persistPeriod(Duration.millis(10))
                .haHeartbeatPeriod(Duration.millis(10))
                .haHeartbeatTimeout(Duration.millis(1000))
                .start();
        ManagementContext secondaryManagementContext = secondary.getServerDetails().getManagementContext();
        log.info("started mgmt secondary "+secondaryManagementContext);
        
        // TODO can assert it sees the apps read only
//        assertNoApps(secondary.getServerDetails().getManagementContext());

        // Terminate primary; expect secondary to take over
        if (stopGracefully) {
            ((ManagementContextInternal)primaryManagementContext).terminate();
        } else {
            ManagementPlaneSyncRecordPersister planePersister = ((ManagementContextInternal)primaryManagementContext).getHighAvailabilityManager().getPersister();
            planePersister.stop(); // can no longer write heartbeats
            ((ManagementContextInternal)primaryManagementContext).terminate();
        }
        
        assertOnlyAppEventually(secondaryManagementContext, TestApplication.class);
        
        // Start tertiary (force up as standby)
        tertiary = BrooklynLauncher.newInstance();
        tertiary.webconsole(false)
                .brooklynProperties(LocalManagementContextForTests.setEmptyCatalogAsDefault(BrooklynProperties.Factory.newEmpty()))
                .highAvailabilityMode(HighAvailabilityMode.STANDBY)
                .persistMode(PersistMode.AUTO)
                .persistenceDir(persistenceDir)
                .persistPeriod(Duration.millis(10))
                .haHeartbeatPeriod(Duration.millis(10))
                .haHeartbeatTimeout(Duration.millis(1000))
                .start();
        ManagementContext tertiaryManagementContext = tertiary.getServerDetails().getManagementContext();
        log.info("started mgmt tertiary "+primaryManagementContext);
        
        assertNoApps(tertiary.getServerDetails().getManagementContext());

        // Terminate secondary; expect tertiary to take over
        if (stopGracefully) {
            ((ManagementContextInternal)secondaryManagementContext).terminate();
        } else {
            ManagementPlaneSyncRecordPersister planePersister = ((ManagementContextInternal)secondaryManagementContext).getHighAvailabilityManager().getPersister();
            planePersister.stop(); // can no longer write heartbeats
            ((ManagementContextInternal)secondaryManagementContext).terminate();
        }
        
        assertOnlyAppEventually(tertiaryManagementContext, TestApplication.class);
    }
    
    public void testHighAvailabilityMasterModeFailsIfAlreadyHasMaster() throws Exception {
        primary = BrooklynLauncher.newInstance();
        primary.webconsole(false)
                .brooklynProperties(LocalManagementContextForTests.setEmptyCatalogAsDefault(BrooklynProperties.Factory.newEmpty()))
                .highAvailabilityMode(HighAvailabilityMode.AUTO)
                .persistMode(PersistMode.AUTO)
                .persistenceDir(persistenceDir)
                .persistPeriod(Duration.millis(10))
                .application(EntitySpec.create(TestApplication.class))
                .start();

        try {
            // Secondary will come up as standby
            secondary = BrooklynLauncher.newInstance();
            secondary.webconsole(false)
                    .brooklynProperties(LocalManagementContextForTests.setEmptyCatalogAsDefault(BrooklynProperties.Factory.newEmpty()))
                    .highAvailabilityMode(HighAvailabilityMode.MASTER)
                    .persistMode(PersistMode.AUTO)
                    .persistenceDir(persistenceDir)
                    .persistPeriod(Duration.millis(10))
                    .start();
            fail();
        } catch (IllegalStateException e) {
            // success
        }
    }
    
    @Test
    public void testHighAvailabilityStandbyModeFailsIfNoExistingMaster() throws Exception {
        try {
            primary = BrooklynLauncher.newInstance();
            primary.webconsole(false)
                    .brooklynProperties(LocalManagementContextForTests.setEmptyCatalogAsDefault(BrooklynProperties.Factory.newEmpty()))
                    .highAvailabilityMode(HighAvailabilityMode.STANDBY)
                    .persistMode(PersistMode.AUTO)
                    .persistenceDir(persistenceDir)
                    .persistPeriod(Duration.millis(10))
                    .ignorePersistenceErrors(false)
                    .application(EntitySpec.create(TestApplication.class))
                    .start();
            fail();
        } catch (IllegalStateException e) {
            // success
        }
    }
    
    @Test
    public void testHighAvailabilityHotStandbyModeFailsIfNoExistingMaster() throws Exception {
        try {
            primary = BrooklynLauncher.newInstance();
            primary.webconsole(false)
                    .brooklynProperties(LocalManagementContextForTests.setEmptyCatalogAsDefault(BrooklynProperties.Factory.newEmpty()))
                    .highAvailabilityMode(HighAvailabilityMode.HOT_STANDBY)
                    .persistMode(PersistMode.AUTO)
                    .persistenceDir(persistenceDir)
                    .persistPeriod(Duration.millis(10))
                    .ignorePersistenceErrors(false)
                    .application(EntitySpec.create(TestApplication.class))
                    .start();
            fail();
        } catch (IllegalStateException e) {
            // success
        }
    }
    
    private void assertOnlyApp(ManagementContext managementContext, Class<? extends Application> expectedType) {
        assertEquals(managementContext.getApplications().size(), 1, "apps="+managementContext.getApplications());
        assertNotNull(Iterables.find(managementContext.getApplications(), Predicates.instanceOf(TestApplication.class), null), "apps="+managementContext.getApplications());
    }
    
    private void assertNoApps(ManagementContext managementContext) {
        if (!managementContext.getApplications().isEmpty())
            log.warn("FAILED assertion (rethrowing), apps="+managementContext.getApplications());
        assertTrue(managementContext.getApplications().isEmpty(), "apps="+managementContext.getApplications());
    }
    
    private void assertOnlyAppEventually(final ManagementContext managementContext, final Class<? extends Application> expectedType) {
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertOnlyApp(managementContext, expectedType);
            }});
    }
}

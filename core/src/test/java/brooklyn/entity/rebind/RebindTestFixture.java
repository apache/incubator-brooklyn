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
package brooklyn.entity.rebind;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.FileBasedObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.internal.BrooklynFeatureEnablement;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.mementos.BrooklynMementoManifest;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.os.Os;
import brooklyn.util.time.Duration;

public abstract class RebindTestFixture<T extends StartableApplication> {

    private static final Logger LOG = LoggerFactory.getLogger(RebindTestFixture.class);

    protected static final Duration TIMEOUT_MS = Duration.TEN_SECONDS;

    protected ClassLoader classLoader = getClass().getClassLoader();
    protected LocalManagementContext origManagementContext;
    protected File mementoDir;
    
    protected T origApp;
    protected T newApp;
    protected ManagementContext newManagementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mementoDir = Os.newTempDir(getClass());
        origManagementContext = createOrigManagementContext();
        origApp = createApp();
        
        LOG.info("Test "+getClass()+" persisting to "+mementoDir);
    }

    protected LocalManagementContext createOrigManagementContext() {
        return RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, getPersistPeriodMillis());
    }
    
    protected int getPersistPeriodMillis() {
        return 1;
    }
    
    /** optionally, create the app as part of every test; can be no-op if tests wish to set origApp themselves */
    protected abstract T createApp();

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (origApp != null) Entities.destroyAll(origApp.getManagementContext());
        if (newApp != null) Entities.destroyAll(newApp.getManagementContext());
        if (newManagementContext != null) Entities.destroyAll(newManagementContext);
        origApp = null;
        newApp = null;
        newManagementContext = null;

        if (origManagementContext != null) Entities.destroyAll(origManagementContext);
        if (mementoDir != null) FileBasedObjectStore.deleteCompletely(mementoDir);
        origManagementContext = null;
    }

    /** rebinds, and sets newApp */
    protected T rebind() throws Exception {
        if (newApp!=null || newManagementContext!=null) throw new IllegalStateException("already rebinded");
        newApp = rebind(true);
        newManagementContext = newApp.getManagementContext();
        return newApp;
    }

    protected T rebind(boolean checkSerializable) throws Exception {
        // TODO What are sensible defaults?!
        return rebind(checkSerializable, false);
    }
    
    @SuppressWarnings("unchecked")
    protected T rebind(boolean checkSerializable, boolean terminateOrigManagementContext) throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        if (checkSerializable) {
            RebindTestUtils.checkCurrentMementoSerializable(origApp);
        }
        if (terminateOrigManagementContext) {
            origManagementContext.terminate();
        }
        return (T) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }

    @SuppressWarnings("unchecked")
    protected T rebind(RebindExceptionHandler exceptionHandler) throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (T) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader(), exceptionHandler);
    }

    @SuppressWarnings("unchecked")
    protected T rebind(ManagementContext newManagementContext, RebindExceptionHandler exceptionHandler) throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (T) RebindTestUtils.rebind(newManagementContext, mementoDir, getClass().getClassLoader(), exceptionHandler);
    }
    
    protected BrooklynMementoManifest loadMementoManifest() throws Exception {
        newManagementContext = new LocalManagementContextForTests();
        FileBasedObjectStore objectStore = new FileBasedObjectStore(mementoDir);
        objectStore.injectManagementContext(newManagementContext);
        objectStore.prepareForSharedUse(PersistMode.AUTO, HighAvailabilityMode.DISABLED);
        BrooklynMementoPersisterToObjectStore persister = new BrooklynMementoPersisterToObjectStore(
                objectStore,
                ((ManagementContextInternal)newManagementContext).getBrooklynProperties(),
                classLoader);
        RebindExceptionHandler exceptionHandler = new RecordingRebindExceptionHandler(RebindManager.RebindFailureMode.FAIL_AT_END, RebindManager.RebindFailureMode.FAIL_AT_END);
        BrooklynMementoManifest mementoManifest = persister.loadMementoManifest(exceptionHandler);
        persister.stop(false);
        return mementoManifest;
    }
}

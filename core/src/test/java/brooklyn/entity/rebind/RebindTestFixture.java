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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.apache.brooklyn.catalog.BrooklynCatalog;
import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.Task;
import org.apache.brooklyn.management.ha.HighAvailabilityMode;
import org.apache.brooklyn.mementos.BrooklynMementoManifest;

import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.entity.Application;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFunctions;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.FileBasedObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.trait.Startable;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.os.Os;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Duration;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public abstract class RebindTestFixture<T extends StartableApplication> {

    private static final Logger LOG = LoggerFactory.getLogger(RebindTestFixture.class);

    protected static final Duration TIMEOUT_MS = Duration.TEN_SECONDS;

    protected ClassLoader classLoader = getClass().getClassLoader();
    protected LocalManagementContext origManagementContext;
    protected File mementoDir;
    protected File mementoDirBackup;
    
    protected T origApp;
    protected T newApp;
    protected ManagementContext newManagementContext;


    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mementoDir = Os.newTempDir(getClass());
        File mementoDirParent = mementoDir.getParentFile();
        mementoDirBackup = new File(mementoDirParent, mementoDir.getName()+"."+Identifiers.makeRandomId(4)+".bak");

        origManagementContext = createOrigManagementContext();
        origApp = createApp();
        
        LOG.info("Test "+getClass()+" persisting to "+mementoDir);
    }

    /** @return A started management context */
    protected LocalManagementContext createOrigManagementContext() {
        return RebindTestUtils.managementContextBuilder(mementoDir, classLoader)
                .persistPeriodMillis(getPersistPeriodMillis())
                .forLive(useLiveManagementContext())
                .emptyCatalog(useEmptyCatalog())
                .buildStarted();
    }

    /** As {@link #createNewManagementContext(File)} using the default memento dir */
    protected LocalManagementContext createNewManagementContext() {
        return createNewManagementContext(mementoDir);
    }
    
    /** @return An unstarted management context using the specified mementoDir (or default if null) */
    protected LocalManagementContext createNewManagementContext(File mementoDir) {
        if (mementoDir==null) mementoDir = this.mementoDir;
        return RebindTestUtils.managementContextBuilder(mementoDir, classLoader)
                .forLive(useLiveManagementContext())
                .emptyCatalog(useEmptyCatalog())
                .buildUnstarted();
    }

    /** terminates the original management context (not destroying items) and points it at the new one (and same for apps); 
     * then clears the variables for the new one, so you can re-rebind */
    protected void switchOriginalToNewManagementContext() {
        origManagementContext.getRebindManager().stopPersistence();
        for (Application e: origManagementContext.getApplications()) ((Startable)e).stop();
        waitForTaskCountToBecome(origManagementContext, 0, true);
        origManagementContext.terminate();
        origManagementContext = (LocalManagementContext) newManagementContext;
        origApp = newApp;
        newManagementContext = null;
        newApp = null;
    }

    public static void waitForTaskCountToBecome(final ManagementContext mgmt, final int allowedMax) {
        waitForTaskCountToBecome(mgmt, allowedMax, false);
    }
    
    public static void waitForTaskCountToBecome(final ManagementContext mgmt, final int allowedMax, final boolean skipKnownBackgroundTasks) {
        Repeater.create().every(Duration.millis(20)).limitTimeTo(Duration.TEN_SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                ((LocalManagementContext)mgmt).getGarbageCollector().gcIteration();
                long taskCountAfterAtOld = ((BasicExecutionManager)mgmt.getExecutionManager()).getNumIncompleteTasks();
                List<Task<?>> tasks = ((BasicExecutionManager)mgmt.getExecutionManager()).getAllTasks();
                int unendedTasks = 0, extraAllowedMax = 0;
                for (Task<?> t: tasks) {
                    if (!t.isDone()) {
                        if (skipKnownBackgroundTasks) {
                            if (t.toString().indexOf("ssh-location cache cleaner")>=0) {
                                extraAllowedMax++;
                            }
                        }
                        unendedTasks++;
                    }
                }
                LOG.info("Count of incomplete tasks now "+taskCountAfterAtOld+", "+unendedTasks+" unended"
                    + (extraAllowedMax>0 ? " ("+extraAllowedMax+" allowed)" : "")
                    + "; tasks remembered are: "+
                    tasks);
                return taskCountAfterAtOld<=allowedMax+extraAllowedMax;
            }
        }).runRequiringTrue();
    }

    protected boolean useLiveManagementContext() {
        return false;
    }

    protected boolean useEmptyCatalog() {
        return true;
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
        if (mementoDirBackup != null) FileBasedObjectStore.deleteCompletely(mementoDir);
        origManagementContext = null;
    }

    /** rebinds, and sets newApp */
    protected T rebind() throws Exception {
        return rebind(RebindOptions.create());
    }

    /**
     * Checking serializable is overly strict.
     * State only needs to be xstream-serializable, which does not require `implements Serializable`. 
     * Also, the xstream serializer has some special hooks that replaces an entity reference with 
     * a marker for that entity, etc.
     * 
     * @deprecated since 0.7.0; use {@link #rebind()} or {@link #rebind(RebindOptions)})
     */
    @Deprecated
    protected T rebind(boolean checkSerializable) throws Exception {
        return rebind(RebindOptions.create().checkSerializable(checkSerializable));
    }
    
    /**
     * Checking serializable is overly strict.
     * State only needs to be xstream-serializable, which does not require `implements Serializable`. 
     * Also, the xstream serializer has some special hooks that replaces an entity reference with 
     * a marker for that entity, etc.
     * 
     * @deprecated since 0.7.0; use {@link #rebind(RebindOptions)})
     */
    @Deprecated
    protected T rebind(boolean checkSerializable, boolean terminateOrigManagementContext) throws Exception {
        return rebind(RebindOptions.create()
                .checkSerializable(checkSerializable)
                .terminateOrigManagementContext(terminateOrigManagementContext));
    }

    /**
     * @deprecated since 0.7.0; use {@link #rebind(RebindOptions)})
     */
    @Deprecated
    protected T rebind(RebindExceptionHandler exceptionHandler) throws Exception {
        return rebind(RebindOptions.create().exceptionHandler(exceptionHandler));
    }

    /**
     * @deprecated since 0.7.0; use {@link #rebind(RebindOptions)})
     */
    @Deprecated
    protected T rebind(ManagementContext newManagementContext, RebindExceptionHandler exceptionHandler) throws Exception {
        return rebind(RebindOptions.create()
                .newManagementContext(newManagementContext)
                .exceptionHandler(exceptionHandler));
    }
    
    @SuppressWarnings("unchecked")
    protected T rebind(RebindOptions options) throws Exception {
        if (newApp != null || newManagementContext != null) {
            throw new IllegalStateException("already rebound - use switchOriginalToNewManagementContext() if you are trying to rebind multiple times");
        }
        
        options = RebindOptions.create(options);
        if (options.classLoader == null) options.classLoader(classLoader);
        if (options.mementoDir == null) options.mementoDir(mementoDir);
        if (options.origManagementContext == null) options.origManagementContext(origManagementContext);
        if (options.newManagementContext == null) options.newManagementContext(createNewManagementContext(options.mementoDir));
        
        RebindTestUtils.waitForPersisted(origApp);
        
        newManagementContext = options.newManagementContext;
        newApp = (T) RebindTestUtils.rebind(options);
        return newApp;
    }

    /**
     * Dumps out the persisted mementos that are at the given directory.
     * 
     * @param dir The directory containing the persisted state (e.g. {@link #mementoDir} or {@link #mementoDirBackup})
     */
    protected void dumpMementoDir(File dir) {
        RebindTestUtils.dumpMementoDir(dir);
    }
    
    protected BrooklynMementoManifest loadMementoManifest() throws Exception {
        newManagementContext = createNewManagementContext();
        FileBasedObjectStore objectStore = new FileBasedObjectStore(mementoDir);
        objectStore.injectManagementContext(newManagementContext);
        objectStore.prepareForSharedUse(PersistMode.AUTO, HighAvailabilityMode.DISABLED);
        BrooklynMementoPersisterToObjectStore persister = new BrooklynMementoPersisterToObjectStore(
                objectStore,
                ((ManagementContextInternal)newManagementContext).getBrooklynProperties(),
                classLoader);
        RebindExceptionHandler exceptionHandler = new RecordingRebindExceptionHandler(RebindManager.RebindFailureMode.FAIL_AT_END, RebindManager.RebindFailureMode.FAIL_AT_END);
        BrooklynMementoManifest mementoManifest = persister.loadMementoManifest(null, exceptionHandler);
        persister.stop(false);
        return mementoManifest;
    }

    protected void assertCatalogsEqual(BrooklynCatalog actual, BrooklynCatalog expected) {
        Set<String> actualIds = getCatalogItemIds(actual.getCatalogItems());
        Set<String> expectedIds = getCatalogItemIds(expected.getCatalogItems());
        assertEquals(actualIds.size(), Iterables.size(actual.getCatalogItems()), "id keyset size != size of catalog. Are there duplicates in the catalog?");
        assertEquals(actualIds, expectedIds);
        for (String versionedId : actualIds) {
            String id = CatalogUtils.getIdFromVersionedId(versionedId);
            String version = CatalogUtils.getVersionFromVersionedId(versionedId);
            assertCatalogItemsEqual(actual.getCatalogItem(id, version), expected.getCatalogItem(id, version));
        }
    }

    private Set<String> getCatalogItemIds(Iterable<CatalogItem<Object, Object>> catalogItems) {
        return FluentIterable.from(catalogItems)
                .transform(EntityFunctions.id())
                .copyInto(Sets.<String>newHashSet());
   }

    protected void assertCatalogItemsEqual(CatalogItem<?, ?> actual, CatalogItem<?, ?> expected) {
        assertEquals(actual.getClass(), expected.getClass());
        assertEquals(actual.getId(), expected.getId());
        assertEquals(actual.getDisplayName(), expected.getDisplayName());
        assertEquals(actual.getVersion(), expected.getVersion());
        assertEquals(actual.getJavaType(), expected.getJavaType());
        assertEquals(actual.getDescription(), expected.getDescription());
        assertEquals(actual.getIconUrl(), expected.getIconUrl());
        assertEquals(actual.getVersion(), expected.getVersion());
        assertEquals(actual.getCatalogItemJavaType(), expected.getCatalogItemJavaType());
        assertEquals(actual.getCatalogItemType(), expected.getCatalogItemType());
        assertEquals(actual.getSpecType(), expected.getSpecType());
        assertEquals(actual.getSymbolicName(), expected.getSymbolicName());
        assertEquals(actual.getLibraries(), expected.getLibraries());
    }
    
    protected void assertCatalogContains(BrooklynCatalog catalog, CatalogItem<?, ?> item) {
        CatalogItem<?, ?> found = catalog.getCatalogItem(item.getSymbolicName(), item.getVersion());
        assertNotNull(found);
        assertCatalogItemsEqual(found, item);
    }
    
    protected void assertCatalogDoesNotContain(BrooklynCatalog catalog, String symbolicName, String version) {
        CatalogItem<?, ?> found = catalog.getCatalogItem(symbolicName, version);
        assertNull(found);
    }
}

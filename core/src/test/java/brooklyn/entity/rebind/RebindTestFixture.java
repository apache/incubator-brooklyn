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

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFunctions;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.FileBasedObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.mementos.BrooklynMementoManifest;
import brooklyn.mementos.BrooklynMementoRawData;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.io.FileUtil;
import brooklyn.util.os.Os;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Duration;

import com.google.api.client.util.Sets;
import com.google.common.annotations.Beta;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

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

    /** @return An unstarted management context */
    protected LocalManagementContext createNewManagementContext() {
        return RebindTestUtils.managementContextBuilder(mementoDir, classLoader)
                .forLive(useLiveManagementContext())
                .emptyCatalog(useEmptyCatalog())
                .buildUnstarted();
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

    /**
     * Dumps out the persisted mementos that are at the given directory.
     * 
     * Binds to the persisted state (as a "hot standby") to load the raw data (as strings), and to write out the
     * entity, location, policy, enricher, feed and catalog-item data.
     * 
     * @param dir The directory containing the persisted state (e.g. {@link #mementoDir} or {@link #mementoDirBackup})
     */
    protected void dumpMementoDir(File dir) {
        LocalManagementContextForTests mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newEmpty());
        FileBasedObjectStore store = null;
        BrooklynMementoPersisterToObjectStore persister = null;
        try {
            store = new FileBasedObjectStore(dir);
            store.injectManagementContext(mgmt);
            store.prepareForSharedUse(PersistMode.AUTO, HighAvailabilityMode.HOT_STANDBY);
            persister = new BrooklynMementoPersisterToObjectStore(store, BrooklynProperties.Factory.newEmpty(), classLoader);
            BrooklynMementoRawData data = persister.loadMementoRawData(RebindExceptionHandlerImpl.builder().build());
            List<BrooklynObjectType> types = ImmutableList.of(BrooklynObjectType.ENTITY, BrooklynObjectType.LOCATION, 
                    BrooklynObjectType.POLICY, BrooklynObjectType.ENRICHER, BrooklynObjectType.FEED, 
                    BrooklynObjectType.CATALOG_ITEM);
            for (BrooklynObjectType type : types) {
                LOG.info(type+" ("+data.getObjectsOfType(type).keySet()+"):");
                for (Map.Entry<String, String> entry : data.getObjectsOfType(type).entrySet()) {
                    LOG.info("\t"+type+" "+entry.getKey()+": "+entry.getValue());
                }
            }
        } finally {
            if (persister != null) persister.stop(false);
            if (store != null) store.close();
            mgmt.terminate();
        }
    }
    
    /** rebinds, and sets newApp */
    protected T rebind() throws Exception {
        return rebind(true);
    }

    /**
     * TODO We should (probably?!) change everywhere from asserting that they are serializable. 
     * They only need to be xstream-serializable, which does not require `implements Serializable`. 
     * Also, the xstream serializer has some special hooks that replaces an entity reference with 
     * a marker for that entity, etc. Suggest we change the default {@link #rebind()} to use 
     * {@code checkSerializable==false}, and deprecate this + the other overloaded methods?
     */
    protected T rebind(boolean checkSerializable) throws Exception {
        // TODO What are sensible defaults?!
        return rebind(checkSerializable, false);
    }

    protected T rebind(boolean checkSerializable, boolean terminateOrigManagementContext) throws Exception {
        return rebind(checkSerializable, terminateOrigManagementContext, (File)null);
    }
    
    @Beta // temporary method while debugging; Aled will refactor all of this soon!
    @SuppressWarnings("unchecked")
    protected T rebind(boolean checkSerializable, boolean terminateOrigManagementContext, File backupDir) throws Exception {
        if (newApp!=null || newManagementContext!=null) throw new IllegalStateException("already rebound");
        
        RebindTestUtils.waitForPersisted(origApp);
        if (checkSerializable) {
            RebindTestUtils.checkCurrentMementoSerializable(origApp);
        }
        if (terminateOrigManagementContext) {
            origManagementContext.terminate();
        }

        if (backupDir != null) {
            FileUtil.copyDir(mementoDir, backupDir);
            FileUtil.setFilePermissionsTo700(backupDir);
        }

        newManagementContext = createNewManagementContext();
        newApp = (T) RebindTestUtils.rebind((LocalManagementContext)newManagementContext, classLoader);
        return newApp;
    }

    @SuppressWarnings("unchecked")
    protected T rebind(RebindExceptionHandler exceptionHandler) throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (T) RebindTestUtils.rebind(mementoDir, classLoader, exceptionHandler);
    }

    @SuppressWarnings("unchecked")
    protected T rebind(ManagementContext newManagementContext, RebindExceptionHandler exceptionHandler) throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (T) RebindTestUtils.rebind(newManagementContext, mementoDir, classLoader, exceptionHandler);
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
}

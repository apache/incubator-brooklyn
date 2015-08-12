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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.ha.HighAvailabilityMode;
import org.apache.brooklyn.management.ha.ManagementNodeState;
import org.apache.brooklyn.mementos.BrooklynMemento;
import org.apache.brooklyn.mementos.BrooklynMementoRawData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.rebind.Dumpers.Pointer;
import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.FileBasedObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.entity.trait.Identifiable;
import brooklyn.location.Location;
import brooklyn.management.ha.ManagementPlaneSyncRecordPersisterToObjectStore;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.io.FileUtil;
import brooklyn.util.javalang.Serializers;
import brooklyn.util.javalang.Serializers.ObjectReplacer;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

public class RebindTestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(RebindTestUtils.class);

    private static final Duration TIMEOUT = Duration.seconds(20);

    public static <T> T serializeAndDeserialize(T memento) throws Exception {
        ObjectReplacer replacer = new ObjectReplacer() {
            private final Map<Pointer, Object> replaced = Maps.newLinkedHashMap();

            @Override public Object replace(Object toserialize) {
                if (toserialize instanceof Location || toserialize instanceof Entity) {
                    Pointer pointer = new Pointer(((Identifiable)toserialize).getId());
                    replaced.put(pointer, toserialize);
                    return pointer;
                }
                return toserialize;
            }
            @Override public Object resolve(Object todeserialize) {
                if (todeserialize instanceof Pointer) {
                    return checkNotNull(replaced.get(todeserialize), todeserialize);
                }
                return todeserialize;
            }
        };

        try {
            return Serializers.reconstitute(memento, replacer);
        } catch (Exception e) {
            try {
                Dumpers.logUnserializableChains(memento, replacer);
                //Dumpers.deepDumpSerializableness(memento);
            } catch (Throwable t) {
                LOG.warn("Error logging unserializable chains for memento "+memento+" (propagating original exception)", t);
            }
            throw e;
        }
    }
    
    public static void deleteMementoDir(String path) {
        deleteMementoDir(new File(path));
    }

    public static void deleteMementoDir(File f) {
        FileBasedObjectStore.deleteCompletely(f);
    }

    public static void checkMementoSerializable(Application app) throws Exception {
        BrooklynMemento memento = MementosGenerators.newBrooklynMemento(app.getManagementContext());
        checkMementoSerializable(memento);
    }

    public static void checkMementoSerializable(BrooklynMemento memento) throws Exception {
        serializeAndDeserialize(memento);
    }

    public static LocalManagementContext newPersistingManagementContext(File mementoDir, ClassLoader classLoader) {
        return managementContextBuilder(mementoDir, classLoader).buildStarted();
    }
    public static LocalManagementContext newPersistingManagementContext(File mementoDir, ClassLoader classLoader, long persistPeriodMillis) {
        return managementContextBuilder(mementoDir, classLoader)
                .persistPeriodMillis(persistPeriodMillis)
                .buildStarted();
    }
    
    public static LocalManagementContext newPersistingManagementContextUnstarted(File mementoDir, ClassLoader classLoader) {
        return managementContextBuilder(mementoDir, classLoader).buildUnstarted();
    }

    public static ManagementContextBuilder managementContextBuilder(File mementoDir, ClassLoader classLoader) {
        return new ManagementContextBuilder(classLoader, mementoDir);
    }
    public static ManagementContextBuilder managementContextBuilder(ClassLoader classLoader, File mementoDir) {
        return new ManagementContextBuilder(classLoader, mementoDir);
    }
    public static ManagementContextBuilder managementContextBuilder(ClassLoader classLoader, PersistenceObjectStore objectStore) {
        return new ManagementContextBuilder(classLoader, objectStore);
    }

    public static class ManagementContextBuilder {
        final ClassLoader classLoader;
        BrooklynProperties properties;
        PersistenceObjectStore objectStore;
        Duration persistPeriod = Duration.millis(100);
        boolean forLive;
        boolean enableOsgi = false;
        boolean emptyCatalog;
        
        ManagementContextBuilder(File mementoDir, ClassLoader classLoader) {
            this(classLoader, new FileBasedObjectStore(mementoDir));
        }
        ManagementContextBuilder(ClassLoader classLoader, File mementoDir) {
            this(classLoader, new FileBasedObjectStore(mementoDir));
        }
        ManagementContextBuilder(ClassLoader classLoader, PersistenceObjectStore objStore) {
            this.classLoader = checkNotNull(classLoader, "classLoader");
            this.objectStore = checkNotNull(objStore, "objStore");
        }
        
        public ManagementContextBuilder persistPeriodMillis(long persistPeriodMillis) {
            checkArgument(persistPeriodMillis > 0, "persistPeriodMillis must be greater than 0; was "+persistPeriodMillis);
            return persistPeriod(Duration.millis(persistPeriodMillis));
        }
        public ManagementContextBuilder persistPeriod(Duration persistPeriod) {
            checkNotNull(persistPeriod);
            this.persistPeriod = persistPeriod;
            return this;
        }

        public ManagementContextBuilder properties(BrooklynProperties properties) {
            this.properties = checkNotNull(properties, "properties");
            return this;
        }

        public ManagementContextBuilder forLive(boolean val) {
            this.forLive = val;
            return this;
        }

        public ManagementContextBuilder enableOsgi(boolean val) {
            this.enableOsgi = val;
            return this;
        }

        public ManagementContextBuilder emptyCatalog() {
            this.emptyCatalog = true;
            return this;
        }

        public ManagementContextBuilder emptyCatalog(boolean val) {
            this.emptyCatalog = val;
            return this;
        }

        public LocalManagementContext buildUnstarted() {
            LocalManagementContext unstarted;
            BrooklynProperties properties = this.properties != null
                    ? this.properties
                    : BrooklynProperties.Factory.newDefault();
            if (this.emptyCatalog) {
                properties.putIfAbsent(BrooklynServerConfig.BROOKLYN_CATALOG_URL, ManagementContextInternal.EMPTY_CATALOG_URL);
            }
            if (forLive) {
                unstarted = new LocalManagementContext(properties);
            } else {
                unstarted = LocalManagementContextForTests.builder(true).useProperties(properties).disableOsgi(!enableOsgi).build();
            }
            
            objectStore.injectManagementContext(unstarted);
            objectStore.prepareForSharedUse(PersistMode.AUTO, HighAvailabilityMode.DISABLED);
            BrooklynMementoPersisterToObjectStore newPersister = new BrooklynMementoPersisterToObjectStore(
                    objectStore, 
                    unstarted.getBrooklynProperties(), 
                    classLoader);
            ((RebindManagerImpl) unstarted.getRebindManager()).setPeriodicPersistPeriod(persistPeriod);
            unstarted.getRebindManager().setPersister(newPersister, PersistenceExceptionHandlerImpl.builder().build());
            // set the HA persister, in case any children want to use HA
            unstarted.getHighAvailabilityManager().setPersister(new ManagementPlaneSyncRecordPersisterToObjectStore(unstarted, objectStore, classLoader));
            return unstarted;
        }

        public LocalManagementContext buildStarted() {
            LocalManagementContext unstarted = buildUnstarted();
            unstarted.getHighAvailabilityManager().disabled();
            unstarted.getRebindManager().startPersistence();
            return unstarted;
        }

    }

    /**
     * Convenience for common call; delegates to {@link #rebind(RebindOptions)}
     */
    public static Application rebind(File mementoDir, ClassLoader classLoader) throws Exception {
        return rebind(RebindOptions.create()
                .mementoDir(mementoDir)
                .classLoader(classLoader));
    }

    /**
     * @deprecated since 0.7.0; use {@link #rebind(RebindOptions)}
     */
    @Deprecated
    public static Application rebind(File mementoDir, ClassLoader classLoader, RebindExceptionHandler exceptionHandler) throws Exception {
        return rebind(RebindOptions.create()
                .mementoDir(mementoDir)
                .classLoader(classLoader)
                .exceptionHandler(exceptionHandler));
    }

    /**
     * @deprecated since 0.7.0; use {@link #rebind(RebindOptions)}
     */
    @Deprecated
    public static Application rebind(ManagementContext newManagementContext, ClassLoader classLoader) throws Exception {
        return rebind(RebindOptions.create()
                .newManagementContext(newManagementContext)
                .classLoader(classLoader));
    }

    /**
     * @deprecated since 0.7.0; use {@link #rebind(RebindOptions)}
     */
    @Deprecated
    public static Application rebind(ManagementContext newManagementContext, ClassLoader classLoader, RebindExceptionHandler exceptionHandler) throws Exception {
        return rebind(RebindOptions.create()
                .newManagementContext(newManagementContext)
                .classLoader(classLoader)
                .exceptionHandler(exceptionHandler));
    }

    /**
     * @deprecated since 0.7.0; use {@link #rebind(RebindOptions)}
     */
    @Deprecated
    public static Application rebind(ManagementContext newManagementContext, File mementoDir, ClassLoader classLoader) throws Exception {
        return rebind(RebindOptions.create()
                .newManagementContext(newManagementContext)
                .mementoDir(mementoDir)
                .classLoader(classLoader));
    }

    /**
     * @deprecated since 0.7.0; use {@link #rebind(RebindOptions)}
     */
    @Deprecated
    public static Application rebind(ManagementContext newManagementContext, File mementoDir, ClassLoader classLoader, RebindExceptionHandler exceptionHandler) throws Exception {
        return rebind(RebindOptions.create()
                .newManagementContext(newManagementContext)
                .mementoDir(mementoDir)
                .classLoader(classLoader)
                .exceptionHandler(exceptionHandler));
    }
    
    /**
     * @deprecated since 0.7.0; use {@link #rebind(RebindOptions)}
     */
    @Deprecated
    public static Application rebind(ManagementContext newManagementContext, File mementoDir,
            ClassLoader classLoader, RebindExceptionHandler exceptionHandler, PersistenceObjectStore objectStore) throws Exception {
        return rebind(RebindOptions.create()
                .newManagementContext(newManagementContext)
                .mementoDir(mementoDir)
                .classLoader(classLoader)
                .exceptionHandler(exceptionHandler)
                .objectStore(objectStore));
    }
    
    public static Application rebind(RebindOptions options) throws Exception {
        Collection<Application> newApps = rebindAll(options);
        if (newApps.isEmpty()) throw new IllegalStateException("Application could not be rebinded; serialization probably failed");
        return Iterables.getFirst(newApps, null);
    }


    /**
     * @deprecated since 0.7.0; use {@link #rebindAll(RebindOptions)}
     */
    @Deprecated
    public static Collection<Application> rebindAll(File mementoDir, ClassLoader classLoader) throws Exception {
        return rebindAll(RebindOptions.create()
                .mementoDir(mementoDir)
                .classLoader(classLoader));
    }

    /**
     * @deprecated since 0.7.0; use {@link #rebind(RebindOptions)}
     */
    @Deprecated
    public static Collection<Application> rebindAll(File mementoDir, ClassLoader classLoader, RebindExceptionHandler exceptionHandler) throws Exception {
        return rebindAll(RebindOptions.create()
                .mementoDir(mementoDir)
                .classLoader(classLoader)
                .exceptionHandler(exceptionHandler));
    }
    
    /**
     * @deprecated since 0.7.0; use {@link #rebind(RebindOptions)}
     */
    @Deprecated
    public static Collection<Application> rebindAll(LocalManagementContext newManagementContext, ClassLoader classLoader, RebindExceptionHandler exceptionHandler) throws Exception {
        return rebindAll(RebindOptions.create()
                .newManagementContext(newManagementContext)
                .classLoader(classLoader)
                .exceptionHandler(exceptionHandler));
    }

    /**
     * @deprecated since 0.7.0; use {@link #rebind(RebindOptions)}
     */
    @Deprecated
    public static Collection<Application> rebindAll(ManagementContext newManagementContext, File mementoDir, ClassLoader classLoader) throws Exception {
        return rebindAll(RebindOptions.create()
                .newManagementContext(newManagementContext)
                .mementoDir(mementoDir)
                .classLoader(classLoader));
    }

    /**
     * @deprecated since 0.7.0; use {@link #rebind(RebindOptions)}
     */
    @Deprecated
    public static Collection<Application> rebindAll(ManagementContext newManagementContext, File mementoDir, ClassLoader classLoader, RebindExceptionHandler exceptionHandler, PersistenceObjectStore objectStore) throws Exception {
        return rebindAll(RebindOptions.create()
                .newManagementContext(newManagementContext)
                .mementoDir(mementoDir)
                .classLoader(classLoader)
                .exceptionHandler(exceptionHandler)
                .objectStore(objectStore));
    }

    public static Collection<Application> rebindAll(RebindOptions options) throws Exception {
        File mementoDir = options.mementoDir;
        File mementoDirBackup = options.mementoDirBackup;
        ClassLoader classLoader = checkNotNull(options.classLoader, "classLoader");
        ManagementContextInternal origManagementContext = (ManagementContextInternal) options.origManagementContext;
        ManagementContextInternal newManagementContext = (ManagementContextInternal) options.newManagementContext;
        PersistenceObjectStore objectStore = options.objectStore;
        RebindExceptionHandler exceptionHandler = options.exceptionHandler;
        boolean hasPersister = newManagementContext != null && newManagementContext.getRebindManager().getPersister() != null;
        boolean checkSerializable = options.checkSerializable;
        boolean terminateOrigManagementContext = options.terminateOrigManagementContext;
        
        LOG.info("Rebinding app, using mementoDir " + mementoDir + "; object store " + objectStore);

        if (newManagementContext == null) {
            // TODO Could use empty properties, to save reading brooklyn.properties file.
            // Would that affect any tests?
            newManagementContext = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());
        }
        if (!hasPersister) {
            if (objectStore == null) {
                objectStore = new FileBasedObjectStore(checkNotNull(mementoDir, "mementoDir and objectStore must not both be null"));
            }
            objectStore.injectManagementContext(newManagementContext);
            objectStore.prepareForSharedUse(PersistMode.AUTO, HighAvailabilityMode.DISABLED);
            
            BrooklynMementoPersisterToObjectStore newPersister = new BrooklynMementoPersisterToObjectStore(
                    objectStore,
                    newManagementContext.getBrooklynProperties(),
                    classLoader);
            newManagementContext.getRebindManager().setPersister(newPersister, PersistenceExceptionHandlerImpl.builder().build());
        } else {
            if (objectStore != null) throw new IllegalStateException("Must not supply ManagementContext with persister and an object store");
        }
        
        if (checkSerializable) {
            checkNotNull(origManagementContext, "must supply origManagementContext with checkSerializable");
            RebindTestUtils.checkCurrentMementoSerializable(origManagementContext);
        }

        if (terminateOrigManagementContext) {
            checkNotNull(origManagementContext, "must supply origManagementContext with terminateOrigManagementContext");
            origManagementContext.terminate();
        }
        
        if (mementoDirBackup != null) {
            FileUtil.copyDir(mementoDir, mementoDirBackup);
            FileUtil.setFilePermissionsTo700(mementoDirBackup);
        }
        
        List<Application> newApps = newManagementContext.getRebindManager().rebind(classLoader, exceptionHandler, ManagementNodeState.MASTER);
        newManagementContext.getRebindManager().startPersistence();
        return newApps;
    }

    public static void waitForPersisted(Application origApp) throws InterruptedException, TimeoutException {
        waitForPersisted(origApp.getManagementContext());
    }

    public static void waitForPersisted(ManagementContext managementContext) throws InterruptedException, TimeoutException {
        managementContext.getRebindManager().waitForPendingComplete(TIMEOUT, true);
    }

    public static void checkCurrentMementoSerializable(Application app) throws Exception {
        checkCurrentMementoSerializable(app.getManagementContext());
    }
    
    public static void checkCurrentMementoSerializable(ManagementContext mgmt) throws Exception {
        BrooklynMemento memento = MementosGenerators.newBrooklynMemento(mgmt);
        serializeAndDeserialize(memento);
    }
    
    /**
     * Dumps out the persisted mementos that are at the given directory.
     * 
     * Binds to the persisted state (as a "hot standby") to load the raw data (as strings), and to write out the
     * entity, location, policy, enricher, feed and catalog-item data.
     * 
     * @param dir The directory containing the persisted state
     */
    public static void dumpMementoDir(File dir) {
        LocalManagementContextForTests mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newEmpty());
        FileBasedObjectStore store = null;
        BrooklynMementoPersisterToObjectStore persister = null;
        try {
            store = new FileBasedObjectStore(dir);
            store.injectManagementContext(mgmt);
            store.prepareForSharedUse(PersistMode.AUTO, HighAvailabilityMode.HOT_STANDBY);
            persister = new BrooklynMementoPersisterToObjectStore(store, BrooklynProperties.Factory.newEmpty(), RebindTestUtils.class.getClassLoader());
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
}

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
package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.BrooklynObject;

import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.mementos.BrooklynMemento;
import org.apache.brooklyn.mementos.BrooklynMementoManifest;
import org.apache.brooklyn.mementos.BrooklynMementoPersister;
import org.apache.brooklyn.mementos.BrooklynMementoRawData;
import org.apache.brooklyn.mementos.CatalogItemMemento;
import org.apache.brooklyn.mementos.Memento;

import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.config.ConfigKey;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.rebind.BrooklynObjectType;
import brooklyn.entity.rebind.PeriodicDeltaChangeListener;
import brooklyn.entity.rebind.PersistenceExceptionHandler;
import brooklyn.entity.rebind.PersisterDeltaImpl;
import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.entity.rebind.dto.BrooklynMementoImpl;
import brooklyn.entity.rebind.dto.BrooklynMementoManifestImpl;
import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessor;
import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessorWithLock;
import brooklyn.management.classloading.ClassLoaderFromBrooklynClassLoadingContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.CompoundRuntimeException;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;
import brooklyn.util.xstream.XmlUtil;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/** Implementation of the {@link BrooklynMementoPersister} backed by a pluggable
 * {@link PersistenceObjectStore} such as a file system or a jclouds object store */
public class BrooklynMementoPersisterToObjectStore implements BrooklynMementoPersister {

    // TODO Crazy amount of duplication between handling entity, location, policy, enricher + feed;
    // Need to remove that duplication.

    // TODO Should stop() take a timeout, and shutdown the executor gracefully?
    
    private static final Logger LOG = LoggerFactory.getLogger(BrooklynMementoPersisterToObjectStore.class);

    public static final ConfigKey<Integer> PERSISTER_MAX_THREAD_POOL_SIZE = ConfigKeys.newIntegerConfigKey(
            "persister.threadpool.maxSize",
            "Maximum number of concurrent operations for persistence (reads/writes/deletes of *different* objects)", 
            10);

    public static final ConfigKey<Integer> PERSISTER_MAX_SERIALIZATION_ATTEMPTS = ConfigKeys.newIntegerConfigKey(
            "persister.maxSerializationAttempts",
            "Maximum number of attempts to serialize a memento (e.g. if first attempts fail because of concurrent modifications of an entity)", 
            5);

    private final PersistenceObjectStore objectStore;
    private final MementoSerializer<Object> serializerWithStandardClassLoader;

    private final Map<String, StoreObjectAccessorWithLock> writers = new LinkedHashMap<String, PersistenceObjectStore.StoreObjectAccessorWithLock>();

    private final ListeningExecutorService executor;

    private volatile boolean writesAllowed = false;
    private volatile boolean writesShuttingDown = false;
    private StringConfigMap brooklynProperties;
    
    private List<Delta> queuedDeltas = new CopyOnWriteArrayList<BrooklynMementoPersister.Delta>();
    
    /**
     * Lock used on writes (checkpoint + delta) so that {@link #waitForWritesCompleted(Duration)} can block
     * for any concurrent call to complete.
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock(true);

    public BrooklynMementoPersisterToObjectStore(PersistenceObjectStore objectStore, StringConfigMap brooklynProperties, ClassLoader classLoader) {
        this.objectStore = checkNotNull(objectStore, "objectStore");
        this.brooklynProperties = brooklynProperties;
        
        int maxSerializationAttempts = brooklynProperties.getConfig(PERSISTER_MAX_SERIALIZATION_ATTEMPTS);
        MementoSerializer<Object> rawSerializer = new XmlMementoSerializer<Object>(classLoader);
        this.serializerWithStandardClassLoader = new RetryingMementoSerializer<Object>(rawSerializer, maxSerializationAttempts);

        int maxThreadPoolSize = brooklynProperties.getConfig(PERSISTER_MAX_THREAD_POOL_SIZE);

        objectStore.createSubPath("entities");
        objectStore.createSubPath("locations");
        objectStore.createSubPath("policies");
        objectStore.createSubPath("enrichers");
        objectStore.createSubPath("feeds");
        objectStore.createSubPath("catalog");

        // FIXME does it belong here or to ManagementPlaneSyncRecordPersisterToObjectStore ?
        objectStore.createSubPath("plane");
        
        executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(maxThreadPoolSize, new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                // Note: Thread name referenced in logback-includes' ThreadNameDiscriminator
                return new Thread(r, "brooklyn-persister");
            }}));
    }

    public MementoSerializer<Object> getMementoSerializer() {
        return getSerializerWithStandardClassLoader();
    }
    
    protected MementoSerializer<Object> getSerializerWithStandardClassLoader() {
        return serializerWithStandardClassLoader;
    }
    
    protected MementoSerializer<Object> getSerializerWithCustomClassLoader(LookupContext lookupContext, BrooklynObjectType type, String objectId) {
        ClassLoader cl = getCustomClassLoaderForBrooklynObject(lookupContext, type, objectId);
        if (cl==null) return serializerWithStandardClassLoader;
        return getSerializerWithCustomClassLoader(lookupContext, cl);
    }
    
    protected MementoSerializer<Object> getSerializerWithCustomClassLoader(LookupContext lookupContext, ClassLoader classLoader) {
        int maxSerializationAttempts = brooklynProperties.getConfig(PERSISTER_MAX_SERIALIZATION_ATTEMPTS);
        MementoSerializer<Object> rawSerializer = new XmlMementoSerializer<Object>(classLoader);
        MementoSerializer<Object> result = new RetryingMementoSerializer<Object>(rawSerializer, maxSerializationAttempts);
        result.setLookupContext(lookupContext);
        return result;
    }
    
    @Nullable protected ClassLoader getCustomClassLoaderForBrooklynObject(LookupContext lookupContext, BrooklynObjectType type, String objectId) {
        BrooklynObject item = lookupContext.peek(type, objectId);
        String catalogItemId = (item == null) ? null : item.getCatalogItemId();
        // TODO enrichers etc aren't yet known -- would need to backtrack to the entity to get them from bundles
        if (catalogItemId == null) {
            return null;
        }
        // See RebindIteration.BrooklynObjectInstantiator.load(), for handling where catalog item is missing;
        // similar logic here.
        CatalogItem<?, ?> catalogItem = CatalogUtils.getCatalogItemOptionalVersion(lookupContext.lookupManagementContext(), catalogItemId);
        if (catalogItem == null) {
            // TODO do we need to only log once, rather than risk log.warn too often? I think this only happens on rebind, so ok.
            LOG.warn("Unable to load catalog item "+catalogItemId+" for custom class loader of "+type+" "+objectId+"; will use default class loader");
            return null;
        } else {
            return ClassLoaderFromBrooklynClassLoadingContext.of(CatalogUtils.newClassLoadingContext(lookupContext.lookupManagementContext(), catalogItem));
        }
    }
    
    @Override public void enableWriteAccess() {
        writesAllowed = true;
    }
    
    @Override
    public void disableWriteAccess(boolean graceful) {
        writesShuttingDown = true;
        try {
            writesAllowed = false;
            // a very long timeout to ensure we don't lose state. 
            // If persisting thousands of entities over slow network to Object Store, could take minutes.
            waitForWritesCompleted(Duration.ONE_HOUR);
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        } finally {
            writesShuttingDown = false;
        }
    }
    
    @Override 
    public void stop(boolean graceful) {
        disableWriteAccess(graceful);
        
        if (executor != null) {
            if (graceful) {
                executor.shutdown();
                try {
                    // should be quick because we've just turned off writes, waiting for their completion
                    executor.awaitTermination(1, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    throw Exceptions.propagate(e);
                }
            } else {
                executor.shutdownNow();
            }
        }
    }

    public PersistenceObjectStore getObjectStore() {
        return objectStore;
    }

    protected StoreObjectAccessorWithLock getWriter(String path) {
        String id = path.substring(path.lastIndexOf('/')+1);
        synchronized (writers) {
            StoreObjectAccessorWithLock writer = writers.get(id);
            if (writer == null) {
                writer = new StoreObjectAccessorLocking( objectStore.newAccessor(path) );
                writers.put(id, writer);
            }
            return writer;
        }
    }

    private Map<String,String> makeIdSubPathMap(Iterable<String> subPathLists) {
        Map<String,String> result = MutableMap.of();
        for (String subpath: subPathLists) {
            String id = subpath;
            id = id.substring(id.lastIndexOf('/')+1);
            id = id.substring(id.lastIndexOf('\\')+1);
            // assumes id is the filename; should work even if not, as id is later read from xpath
            // but you'll get warnings (and possibility of loss if there is a collision)
            result.put(id, subpath);
        }
        return result;
    }
    
    protected BrooklynMementoRawData listMementoSubPathsAsData(final RebindExceptionHandler exceptionHandler) {
        final BrooklynMementoRawData.Builder subPathDataBuilder = BrooklynMementoRawData.builder();

        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            for (BrooklynObjectType type: BrooklynPersistenceUtils.STANDARD_BROOKLYN_OBJECT_TYPE_PERSISTENCE_ORDER)
                subPathDataBuilder.putAll(type, makeIdSubPathMap(objectStore.listContentsWithSubPath(type.getSubPathName())));
            
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            exceptionHandler.onLoadMementoFailed(BrooklynObjectType.UNKNOWN, "Failed to list files", e);
            throw new IllegalStateException("Failed to list memento files in "+objectStore, e);
        }

        BrooklynMementoRawData subPathData = subPathDataBuilder.build();
        LOG.debug("Loaded rebind lists; took {}: {} entities, {} locations, {} policies, {} enrichers, {} feeds, {} catalog items; from {}", new Object[]{
            Time.makeTimeStringRounded(stopwatch),
            subPathData.getEntities().size(), subPathData.getLocations().size(), subPathData.getPolicies().size(), subPathData.getEnrichers().size(), 
            subPathData.getFeeds().size(), subPathData.getCatalogItems().size(),
            objectStore.getSummaryName() });
        
        return subPathData;
    }
    
    public BrooklynMementoRawData loadMementoRawData(final RebindExceptionHandler exceptionHandler) {
        BrooklynMementoRawData subPathData = listMementoSubPathsAsData(exceptionHandler);
        
        final BrooklynMementoRawData.Builder builder = BrooklynMementoRawData.builder();
        
        Visitor loaderVisitor = new Visitor() {
            @Override
            public void visit(BrooklynObjectType type, String id, String contentsSubpath) throws Exception {
                String contents = null;
                try {
                    contents = read(contentsSubpath);
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    exceptionHandler.onLoadMementoFailed(type, "memento "+id+" read error", e);
                }
                
                String xmlId = (String) XmlUtil.xpath(contents, "/"+type.toCamelCase()+"/id");
                String safeXmlId = Strings.makeValidFilename(xmlId);
                if (!Objects.equal(id, safeXmlId))
                    LOG.warn("ID mismatch on "+type.toCamelCase()+", "+id+" from path, "+safeXmlId+" from xml");
                
                builder.put(type, xmlId, contents);
            }
        };

        Stopwatch stopwatch = Stopwatch.createStarted();

        visitMemento("loading raw", subPathData, loaderVisitor, exceptionHandler);
        
        BrooklynMementoRawData result = builder.build();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Loaded rebind raw data; took {}; {} entities, {} locations, {} policies, {} enrichers, {} feeds, {} catalog items, from {}", new Object[]{
                     Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS)), result.getEntities().size(), 
                     result.getLocations().size(), result.getPolicies().size(), result.getEnrichers().size(),
                     result.getFeeds().size(), result.getCatalogItems().size(),
                     objectStore.getSummaryName() });
        }

        return result;
    }

    @Override
    public BrooklynMementoManifest loadMementoManifest(BrooklynMementoRawData mementoData, final RebindExceptionHandler exceptionHandler) throws IOException {
        if (mementoData==null)
            mementoData = loadMementoRawData(exceptionHandler);
        
        final BrooklynMementoManifestImpl.Builder builder = BrooklynMementoManifestImpl.builder();

        Visitor visitor = new Visitor() {
            @Override
            public void visit(BrooklynObjectType type, String objectId, final String contents) throws Exception {
                final String prefix = "/"+type.toCamelCase()+"/";

                class XPathHelper {
                    private String get(String innerPath) {
                        return (String) XmlUtil.xpath(contents, prefix+innerPath);
                    }
                }
                XPathHelper x = new XPathHelper();
                
                switch (type) {
                    case ENTITY:
                        builder.entity(x.get("id"), x.get("type"), 
                            Strings.emptyToNull(x.get("parent")), Strings.emptyToNull(x.get("catalogItemId")));
                        break;
                    case LOCATION:
                    case POLICY:
                    case ENRICHER:
                    case FEED:
                        builder.putType(type, x.get("id"), x.get("type"));
                        break;
                    case CATALOG_ITEM:
                        try {
                            CatalogItemMemento memento = (CatalogItemMemento) getSerializerWithStandardClassLoader().fromString(contents);
                            if (memento == null) {
                                LOG.warn("No "+type.toCamelCase()+"-memento deserialized from " + objectId + "; ignoring and continuing");
                            } else {
                                builder.catalogItem(memento);
                            }
                        } catch (Exception e) {
                            exceptionHandler.onLoadMementoFailed(type, "memento "+objectId+" early catalog deserialization error", e);
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unexpected brooklyn type: "+type);
                }
            }
        };

        Stopwatch stopwatch = Stopwatch.createStarted();

        visitMemento("manifests", mementoData, visitor, exceptionHandler);
        
        BrooklynMementoManifest result = builder.build();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Loaded rebind manifests; took {}: {} entities, {} locations, {} policies, {} enrichers, {} feeds, {} catalog items; from {}", new Object[]{
                     Time.makeTimeStringRounded(stopwatch), 
                     result.getEntityIdToManifest().size(), result.getLocationIdToType().size(), 
                     result.getPolicyIdToType().size(), result.getEnricherIdToType().size(), result.getFeedIdToType().size(), 
                     result.getCatalogItemMementos().size(),
                     objectStore.getSummaryName() });
        }

        return result;
    }
    
    @Override
    public BrooklynMemento loadMemento(BrooklynMementoRawData mementoData, final LookupContext lookupContext, final RebindExceptionHandler exceptionHandler) throws IOException {
        if (mementoData==null)
            mementoData = loadMementoRawData(exceptionHandler);

        Stopwatch stopwatch = Stopwatch.createStarted();

        final BrooklynMementoImpl.Builder builder = BrooklynMementoImpl.builder();
        
        Visitor visitor = new Visitor() {
            @Override
            public void visit(BrooklynObjectType type, String objectId, String contents) throws Exception {
                try {
                    Memento memento = (Memento) getSerializerWithCustomClassLoader(lookupContext, type, objectId).fromString(contents);
                    if (memento == null) {
                        LOG.warn("No "+type.toCamelCase()+"-memento deserialized from " + objectId + "; ignoring and continuing");
                    } else {
                        builder.memento(memento);
                    }
                } catch (Exception e) {
                    exceptionHandler.onLoadMementoFailed(type, "memento "+objectId+" deserialization error", e);
                }
            }

        };

        // TODO not convinced this is single threaded on reads; maybe should get a new one each time?
        getSerializerWithStandardClassLoader().setLookupContext(lookupContext);
        try {
            visitMemento("deserialization", mementoData, visitor, exceptionHandler);
        } finally {
            getSerializerWithStandardClassLoader().unsetLookupContext();
        }

        BrooklynMemento result = builder.build();
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Loaded rebind mementos; took {}: {} entities, {} locations, {} policies, {} enrichers, {} feeds, {} catalog items, from {}", new Object[]{
                      Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS)), result.getEntityIds().size(), 
                      result.getLocationIds().size(), result.getPolicyIds().size(), result.getEnricherIds().size(), 
                      result.getFeedIds().size(), result.getCatalogItemIds().size(),
                      objectStore.getSummaryName() });
        }
        
        return result;
    }
    
    protected interface Visitor {
        public void visit(BrooklynObjectType type, String id, String contents) throws Exception;
    }
    
    protected void visitMemento(final String phase, final BrooklynMementoRawData rawData, final Visitor visitor, final RebindExceptionHandler exceptionHandler) {
        List<ListenableFuture<?>> futures = Lists.newArrayList();
        
        class VisitorWrapper implements Runnable {
            private final BrooklynObjectType type;
            private final Map.Entry<String,String> objectIdAndData;
            public VisitorWrapper(BrooklynObjectType type, Map.Entry<String,String> objectIdAndData) {
                this.type = type;
                this.objectIdAndData = objectIdAndData;
            }
            public void run() {
                try {
                    visitor.visit(type, objectIdAndData.getKey(), objectIdAndData.getValue());
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    exceptionHandler.onLoadMementoFailed(type, "memento "+objectIdAndData.getKey()+" "+phase+" error", e);
                }
            }
        }
        
        for (BrooklynObjectType type: BrooklynPersistenceUtils.STANDARD_BROOKLYN_OBJECT_TYPE_PERSISTENCE_ORDER) {
            for (final Map.Entry<String,String> entry : rawData.getObjectsOfType(type).entrySet()) {
                futures.add(executor.submit(new VisitorWrapper(type, entry)));
            }
        }

        try {
            // Wait for all, failing fast if any exceptions.
            Futures.allAsList(futures).get();
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            
            List<Exception> exceptions = Lists.newArrayList();
            
            for (ListenableFuture<?> future : futures) {
                if (future.isDone()) {
                    try {
                        future.get();
                    } catch (InterruptedException e2) {
                        throw Exceptions.propagate(e2);
                    } catch (ExecutionException e2) {
                        LOG.warn("Problem loading memento ("+phase+"): "+e2, e2);
                        exceptions.add(e2);
                    }
                    future.cancel(true);
                }
            }
            if (exceptions.isEmpty()) {
                throw Exceptions.propagate(e);
            } else {
                // Normally there should be at lesat one failure; otherwise all.get() would not have failed.
                throw new CompoundRuntimeException("Problem loading mementos ("+phase+")", exceptions);
            }
        }
    }

    protected void checkWritesAllowed() {
        if (!writesAllowed && !writesShuttingDown) {
            throw new IllegalStateException("Writes not allowed in "+this);
        }
    }
    
    /** See {@link BrooklynPersistenceUtils} for conveniences for using this method. */
    @Override
    @Beta
    public void checkpoint(BrooklynMementoRawData newMemento, PersistenceExceptionHandler exceptionHandler) {
        checkWritesAllowed();
        try {
            lock.writeLock().lockInterruptibly();
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
        
        try {
            objectStore.prepareForMasterUse();
            
            Stopwatch stopwatch = Stopwatch.createStarted();
            List<ListenableFuture<?>> futures = Lists.newArrayList();
            
            for (BrooklynObjectType type: BrooklynPersistenceUtils.STANDARD_BROOKLYN_OBJECT_TYPE_PERSISTENCE_ORDER) {
                for (Map.Entry<String, String> entry : newMemento.getObjectsOfType(type).entrySet()) {
                    futures.add(asyncPersist(type.getSubPathName(), type, entry.getKey(), entry.getValue(), exceptionHandler));
                }
            }
            
            try {
                // Wait for all the tasks to complete or fail, rather than aborting on the first failure.
                // But then propagate failure if any fail. (hence the two calls).
                Futures.successfulAsList(futures).get();
                Futures.allAsList(futures).get();
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
            if (LOG.isDebugEnabled()) LOG.debug("Checkpointed entire memento in {}", Time.makeTimeStringRounded(stopwatch));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delta(Delta delta, PersistenceExceptionHandler exceptionHandler) {
        checkWritesAllowed();

        while (!queuedDeltas.isEmpty()) {
            Delta extraDelta = queuedDeltas.remove(0);
            doDelta(extraDelta, exceptionHandler, true);
        }

        doDelta(delta, exceptionHandler, false);
    }
    
    protected void doDelta(Delta delta, PersistenceExceptionHandler exceptionHandler, boolean previouslyQueued) {
        Stopwatch stopwatch = deltaImpl(delta, exceptionHandler);
        
        if (LOG.isDebugEnabled()) LOG.debug("Checkpointed "+(previouslyQueued ? "previously queued " : "")+"delta of memento in {}: "
                + "updated {} entities, {} locations, {} policies, {} enrichers, {} catalog items; "
                + "removed {} entities, {} locations, {} policies, {} enrichers, {} catalog items",
                    new Object[] {Time.makeTimeStringRounded(stopwatch),
                        delta.entities().size(), delta.locations().size(), delta.policies().size(), delta.enrichers().size(), delta.catalogItems().size(),
                        delta.removedEntityIds().size(), delta.removedLocationIds().size(), delta.removedPolicyIds().size(), delta.removedEnricherIds().size(), delta.removedCatalogItemIds().size()});
    }
    
    @Override
    public void queueDelta(Delta delta) {
        queuedDeltas.add(delta);
    }
    
    /**
     * Concurrent calls will queue-up (the lock is "fair", which means an "approximately arrival-order policy").
     * Current usage is with the {@link PeriodicDeltaChangeListener} so we expect only one call at a time.
     * 
     * TODO Longer term, if we care more about concurrent calls we could merge the queued deltas so that we
     * don't do unnecessary repeated writes of an entity.
     */
    private Stopwatch deltaImpl(Delta delta, PersistenceExceptionHandler exceptionHandler) {
        try {
            lock.writeLock().lockInterruptibly();
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
        try {
            objectStore.prepareForMasterUse();
            
            Stopwatch stopwatch = Stopwatch.createStarted();
            List<ListenableFuture<?>> futures = Lists.newArrayList();
            
            for (BrooklynObjectType type: BrooklynPersistenceUtils.STANDARD_BROOKLYN_OBJECT_TYPE_PERSISTENCE_ORDER) {
                for (Memento entity : delta.getObjectsOfType(type)) {
                    futures.add(asyncPersist(type.getSubPathName(), entity, exceptionHandler));
                }
            }
            for (BrooklynObjectType type: BrooklynPersistenceUtils.STANDARD_BROOKLYN_OBJECT_TYPE_PERSISTENCE_ORDER) {
                for (String id : delta.getRemovedIdsOfType(type)) {
                    futures.add(asyncDelete(type.getSubPathName(), id, exceptionHandler));
                }
            }
            
            try {
                // Wait for all the tasks to complete or fail, rather than aborting on the first failure.
                // But then propagate failure if any fail. (hence the two calls).
                Futures.successfulAsList(futures).get();
                Futures.allAsList(futures).get();
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
            
            return stopwatch;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void waitForWritesCompleted(Duration timeout) throws InterruptedException, TimeoutException {
        boolean locked = lock.readLock().tryLock(timeout.toMillisecondsRoundingUp(), TimeUnit.MILLISECONDS);
        if (locked) {
            ImmutableSet<StoreObjectAccessorWithLock> wc;
            synchronized (writers) {
                wc = ImmutableSet.copyOf(writers.values());
            }
            lock.readLock().unlock();
            
            // Belt-and-braces: the lock above should be enough to ensure no outstanding writes, because
            // each writer is now synchronous.
            for (StoreObjectAccessorWithLock writer : wc) {
                writer.waitForCurrentWrites(timeout);
            }
        } else {
            throw new TimeoutException("Timeout waiting for writes to "+objectStore);
        }
    }

    private String read(String subPath) {
        StoreObjectAccessor objectAccessor = objectStore.newAccessor(subPath);
        return objectAccessor.get();
    }

    private void persist(String subPath, Memento memento, PersistenceExceptionHandler exceptionHandler) {
        try {
            getWriter(getPath(subPath, memento.getId())).put(getSerializerWithStandardClassLoader().toString(memento));
        } catch (Exception e) {
            exceptionHandler.onPersistMementoFailed(memento, e);
        }
    }
    
    private void persist(String subPath, BrooklynObjectType type, String id, String content, PersistenceExceptionHandler exceptionHandler) {
        try {
            if (content==null) {
                LOG.warn("Null content for "+type+" "+id);
            }
            getWriter(getPath(subPath, id)).put(content);
        } catch (Exception e) {
            exceptionHandler.onPersistRawMementoFailed(type, id, e);
        }
    }
    
    private void delete(String subPath, String id, PersistenceExceptionHandler exceptionHandler) {
        try {
            StoreObjectAccessorWithLock w = getWriter(getPath(subPath, id));
            w.delete();
            synchronized (writers) {
                writers.remove(id);
            }
        } catch (Exception e) {
            exceptionHandler.onDeleteMementoFailed(id, e);
        }
    }

    private ListenableFuture<?> asyncPersist(final String subPath, final Memento memento, final PersistenceExceptionHandler exceptionHandler) {
        return executor.submit(new Runnable() {
            public void run() {
                persist(subPath, memento, exceptionHandler);
            }});
    }

    private ListenableFuture<?> asyncPersist(final String subPath, final BrooklynObjectType type, final String id, final String content, final PersistenceExceptionHandler exceptionHandler) {
        return executor.submit(new Runnable() {
            public void run() {
                persist(subPath, type, id, content, exceptionHandler);
            }});
    }

    private ListenableFuture<?> asyncDelete(final String subPath, final String id, final PersistenceExceptionHandler exceptionHandler) {
        return executor.submit(new Runnable() {
            public void run() {
                delete(subPath, id, exceptionHandler);
            }});
    }
    
    private String getPath(String subPath, String id) {
        return subPath+"/"+Strings.makeValidFilename(id);
    }

    @Override
    public String getBackingStoreDescription() {
        return getObjectStore().getSummaryName();
    }
}

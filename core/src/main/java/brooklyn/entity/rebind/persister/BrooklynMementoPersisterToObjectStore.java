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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.ConfigKey;
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
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoManifest;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.BrooklynMementoRawData;
import brooklyn.mementos.CatalogItemMemento;
import brooklyn.mementos.EnricherMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.FeedMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.Memento;
import brooklyn.mementos.PolicyMemento;
import brooklyn.util.exceptions.CompoundRuntimeException;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;
import brooklyn.util.xstream.XmlUtil;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
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
    private final MementoSerializer<Object> serializer;

    private final Map<String, StoreObjectAccessorWithLock> writers = new LinkedHashMap<String, PersistenceObjectStore.StoreObjectAccessorWithLock>();

    private final ListeningExecutorService executor;

    private volatile boolean writesAllowed = false;
    private volatile boolean writesShuttingDown = false;

    /**
     * Lock used on writes (checkpoint + delta) so that {@link #waitForWritesCompleted(Duration)} can block
     * for any concurrent call to complete.
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock(true);

    public BrooklynMementoPersisterToObjectStore(PersistenceObjectStore objectStore, BrooklynProperties brooklynProperties, ClassLoader classLoader) {
        this.objectStore = checkNotNull(objectStore, "objectStore");
        
        int maxSerializationAttempts = brooklynProperties.getConfig(PERSISTER_MAX_SERIALIZATION_ATTEMPTS);
        int maxThreadPoolSize = brooklynProperties.getConfig(PERSISTER_MAX_THREAD_POOL_SIZE);
                
        MementoSerializer<Object> rawSerializer = new XmlMementoSerializer<Object>(classLoader);
        this.serializer = new RetryingMementoSerializer<Object>(rawSerializer, maxSerializationAttempts);

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
                return new Thread(r, "brooklyn-persister");
            }}));
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

    @Beta
    public BrooklynMementoRawData loadMementoRawData(final RebindExceptionHandler exceptionHandler) throws IOException {
        final BrooklynMementoRawData.Builder builder = BrooklynMementoRawData.builder();

        Visitor visitor = new Visitor() {
            @Override
            public void visit(String contents, BrooklynObjectType type, String subPath) throws Exception {
                switch (type) {
                    case ENTITY:
                        builder.entity((String) XmlUtil.xpath(contents, "/entity/id"), contents);
                        break;
                    case LOCATION:
                        builder.location((String) XmlUtil.xpath(contents, "/location/id"), contents);
                        break;
                    case POLICY:
                        builder.policy((String) XmlUtil.xpath(contents, "/policy/id"), contents);
                        break;
                    case ENRICHER:
                        builder.enricher((String) XmlUtil.xpath(contents, "/enricher/id"), contents);
                        break;
                    case FEED:
                        builder.feed((String) XmlUtil.xpath(contents, "/feed/id"), contents);
                        break;
                    case CATALOG_ITEM:
                        builder.catalogItem((String) XmlUtil.xpath(contents, "/catalogItem/id"), contents);
                        break;
                    default:
                        throw new IllegalStateException("Unexpected brooklyn type: "+type);
                }
            }
        };

        Stopwatch stopwatch = Stopwatch.createStarted();

        visitMemento(visitor, exceptionHandler);
        
        BrooklynMementoRawData result = builder.build();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Loaded memento manifest; took {}; {} entities, {} locations, {} policies, {} enrichers, {} feeds, {} catalog items, from {}", new Object[]{
                     Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS)), result.getEntities().size(), 
                     result.getLocations().size(), result.getPolicies().size(), result.getEnrichers().size(),
                     result.getFeeds().size(), result.getCatalogItems().size(),
                     objectStore.getSummaryName() });
        }

        return result;
    }

    @Override
    public BrooklynMementoManifest loadMementoManifest(final RebindExceptionHandler exceptionHandler) throws IOException {
        final BrooklynMementoManifestImpl.Builder builder = BrooklynMementoManifestImpl.builder();

        Visitor visitor = new Visitor() {
            @Override
            public void visit(String contents, BrooklynObjectType type, String subPath) throws Exception {
                switch (type) {
                    case ENTITY:
                        String id = (String) XmlUtil.xpath(contents, "/entity/id");
                        String objType = (String) XmlUtil.xpath(contents, "/entity/type");
                        String parentId = (String) XmlUtil.xpath(contents, "/entity/parent");
                        String catalogItemId = (String) XmlUtil.xpath(contents, "/entity/catalogItemId");
                        builder.entity(id, objType, Strings.emptyToNull(parentId), Strings.emptyToNull(catalogItemId));
                        break;
                    case LOCATION:
                        id = (String) XmlUtil.xpath(contents, "/location/id");
                        objType = (String) XmlUtil.xpath(contents, "/location/type");
                        builder.location(id, objType);
                        break;
                    case POLICY:
                        id = (String) XmlUtil.xpath(contents, "/policy/id");
                        objType = (String) XmlUtil.xpath(contents, "/policy/type");
                        builder.policy(id, objType);
                        break;
                    case ENRICHER:
                        id = (String) XmlUtil.xpath(contents, "/enricher/id");
                        objType = (String) XmlUtil.xpath(contents, "/enricher/type");
                        builder.enricher(id, objType);
                        break;
                    case FEED:
                        id = (String) XmlUtil.xpath(contents, "/feed/id");
                        objType = (String) XmlUtil.xpath(contents, "/feed/type");
                        builder.feed(id, objType);
                        break;
                    case CATALOG_ITEM:
                        try {
                            CatalogItemMemento memento = (CatalogItemMemento) serializer.fromString(contents);
                            if (memento == null) {
                                LOG.warn("No "+type.toString().toLowerCase()+"-memento deserialized from " + subPath + "; ignoring and continuing");
                            } else {
                                builder.catalogItem(memento);
                            }
                        } catch (Exception e) {
                            exceptionHandler.onLoadMementoFailed(type, "Memento "+subPath, e);
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unexpected brooklyn type: "+type);
                }
            }
        };

        Stopwatch stopwatch = Stopwatch.createStarted();

        visitMemento(visitor, exceptionHandler);
        
        BrooklynMementoManifest result = builder.build();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Loaded memento manifest; took {}; {} entities, {} locations, {} policies, {} enrichers, {} feeds, {} catalog items, from {}", new Object[]{
                     Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS)), result.getEntityIdToManifest().size(), 
                     result.getLocationIdToType().size(), result.getPolicyIdToType().size(), result.getEnricherIdToType().size(), 
                     result.getFeedIdToType().size(), result.getCatalogItemMementos().size(),
                     objectStore.getSummaryName() });
        }

        return result;
    }

    @Override
    public BrooklynMemento loadMemento(LookupContext lookupContext, final RebindExceptionHandler exceptionHandler) throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();

        final BrooklynMementoImpl.Builder builder = BrooklynMementoImpl.builder();
        
        Visitor visitor = new Visitor() {
            @Override
            public void visit(String contents, BrooklynObjectType type, String subPath) throws Exception {
                try {
                    Memento memento = (Memento) serializer.fromString(contents);
                    if (memento == null) {
                        LOG.warn("No "+type.toString().toLowerCase()+"-memento deserialized from " + subPath + "; ignoring and continuing");
                    } else {
                        builder.memento(memento);
                    }
                } catch (Exception e) {
                    exceptionHandler.onLoadMementoFailed(type, "Memento "+subPath, e);
                }
            }
        };

        serializer.setLookupContext(lookupContext);
        try {
            visitMemento(visitor, exceptionHandler);
        } finally {
            serializer.unsetLookupContext();
        }

        BrooklynMemento result = builder.build();
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Loaded memento; took {}; {} entities, {} locations, {} policies, {} enrichers, {} feeds, {} catalog items, from {}", new Object[]{
                      Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS)), result.getEntityIds().size(), 
                      result.getLocationIds().size(), result.getPolicyIds().size(), result.getEnricherIds().size(), 
                      result.getFeedIds().size(), result.getCatalogItemIds().size(),
                      objectStore.getSummaryName() });
        }
        
        return result;
    }
    
    protected interface Visitor {
        public void visit(String contents, BrooklynObjectType type, String subPath) throws Exception;
    }
    protected void visitMemento(final Visitor visitor, final RebindExceptionHandler exceptionHandler) throws IOException {
        List<String> entitySubPathList;
        List<String> locationSubPathList;
        List<String> policySubPathList;
        List<String> enricherSubPathList;
        List<String> feedSubPathList;
        List<String> catalogSubPathList;
        
        try {
            entitySubPathList = objectStore.listContentsWithSubPath("entities");
            locationSubPathList = objectStore.listContentsWithSubPath("locations");
            policySubPathList = objectStore.listContentsWithSubPath("policies");
            enricherSubPathList = objectStore.listContentsWithSubPath("enrichers");
            feedSubPathList = objectStore.listContentsWithSubPath("feeds");
            catalogSubPathList = objectStore.listContentsWithSubPath("catalog");
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            exceptionHandler.onLoadMementoFailed(BrooklynObjectType.UNKNOWN, "Failed to list files", e);
            throw new IllegalStateException("Failed to list memento files in "+objectStore, e);
        }

        LOG.debug("Scanning persisted state: {} entities, {} locations, {} policies, {} enrichers, {} feeds, {} catalog items from {}", new Object[]{
            entitySubPathList.size(), locationSubPathList.size(), policySubPathList.size(), enricherSubPathList.size(), 
            feedSubPathList.size(), catalogSubPathList.size(),
            objectStore.getSummaryName() });

        List<ListenableFuture<?>> futures = Lists.newArrayList();
        
        class VisitorWrapper implements Runnable {
            private final String subPath;
            private final BrooklynObjectType type;
            public VisitorWrapper(String subPath, BrooklynObjectType type) {
                this.subPath = subPath;
                this.type = type;
            }
            public void run() {
                try {
                    String contents = read(subPath);
                    visitor.visit(contents, type, subPath);
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    exceptionHandler.onLoadMementoFailed(type, "Memento "+subPath, e);
                }
            }
        }
        
        for (final String subPath : entitySubPathList) {
            futures.add(executor.submit(new VisitorWrapper(subPath, BrooklynObjectType.ENTITY)));
        }
        for (final String subPath : locationSubPathList) {
            futures.add(executor.submit(new VisitorWrapper(subPath, BrooklynObjectType.LOCATION)));
        }
        for (final String subPath : policySubPathList) {
            futures.add(executor.submit(new VisitorWrapper(subPath, BrooklynObjectType.POLICY)));
        }
        for (final String subPath : enricherSubPathList) {
            futures.add(executor.submit(new VisitorWrapper(subPath, BrooklynObjectType.ENRICHER)));
        }
        for (final String subPath : feedSubPathList) {
            futures.add(executor.submit(new VisitorWrapper(subPath, BrooklynObjectType.FEED)));
        }
        for (final String subPath : catalogSubPathList) {
            futures.add(executor.submit(new VisitorWrapper(subPath, BrooklynObjectType.CATALOG_ITEM)));
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
                        LOG.warn("Problem loading memento manifest", e2);
                        exceptions.add(e2);
                    }
                    future.cancel(true);
                }
            }
            if (exceptions.isEmpty()) {
                throw Exceptions.propagate(e);
            } else {
                // Normally there should be at lesat one failure; otherwise all.get() would not have failed.
                throw new CompoundRuntimeException("Problem loading mementos", exceptions);
            }
        }
    }

    protected void checkWritesAllowed() {
        if (!writesAllowed && !writesShuttingDown) {
            throw new IllegalStateException("Writes not allowed in "+this);
        }
    }
    
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
            
            for (Map.Entry<String, String> entry : newMemento.getEntities().entrySet()) {
                futures.add(asyncPersist("entities", BrooklynObjectType.ENTITY, entry.getKey(), entry.getValue(), exceptionHandler));
            }
            for (Map.Entry<String, String> entry : newMemento.getLocations().entrySet()) {
                futures.add(asyncPersist("locations", BrooklynObjectType.LOCATION, entry.getKey(), entry.getValue(), exceptionHandler));
            }
            for (Map.Entry<String, String> entry : newMemento.getPolicies().entrySet()) {
                futures.add(asyncPersist("policies", BrooklynObjectType.POLICY, entry.getKey(), entry.getValue(), exceptionHandler));
            }
            for (Map.Entry<String, String> entry : newMemento.getEnrichers().entrySet()) {
                futures.add(asyncPersist("enrichers", BrooklynObjectType.ENRICHER, entry.getKey(), entry.getValue(), exceptionHandler));
            }
            for (Map.Entry<String, String> entry : newMemento.getFeeds().entrySet()) {
                futures.add(asyncPersist("feeds", BrooklynObjectType.FEED, entry.getKey(), entry.getValue(), exceptionHandler));
            }
            for (Map.Entry<String, String> entry : newMemento.getCatalogItems().entrySet()) {
                futures.add(asyncPersist("catalog", BrooklynObjectType.CATALOG_ITEM, entry.getKey(), entry.getValue(), exceptionHandler));
            }
            
            try {
                // Wait for all the tasks to complete or fail, rather than aborting on the first failure.
                // But then propagate failure if any fail. (hence the two calls).
                Futures.successfulAsList(futures).get();
                Futures.allAsList(futures).get();
            } catch (Exception e) {
                // TODO is the logging here as good as it was prior to https://github.com/apache/incubator-brooklyn/pull/177/files ?
                throw Exceptions.propagate(e);
            }
            
            if (LOG.isDebugEnabled()) LOG.debug("Checkpointed entire memento in {}", Time.makeTimeStringRounded(stopwatch));
        } finally {
            lock.writeLock().unlock();
        }
    }


    @Override
    public void checkpoint(BrooklynMemento newMemento, PersistenceExceptionHandler exceptionHandler) {
        checkWritesAllowed();

        Delta delta = PersisterDeltaImpl.builder()
                .entities(newMemento.getEntityMementos().values())
                .locations(newMemento.getLocationMementos().values())
                .policies(newMemento.getPolicyMementos().values())
                .enrichers(newMemento.getEnricherMementos().values())
                .feeds(newMemento.getFeedMementos().values())
                .catalogItems(newMemento.getCatalogItemMementos().values())
                .build();
        Stopwatch stopwatch = deltaImpl(delta, exceptionHandler);
        
        if (LOG.isDebugEnabled()) LOG.debug("Checkpointed entire memento in {}", Time.makeTimeStringRounded(stopwatch));
    }

    @Override
    public void delta(Delta delta, PersistenceExceptionHandler exceptionHandler) {
        checkWritesAllowed();

        Stopwatch stopwatch = deltaImpl(delta, exceptionHandler);
        
        if (LOG.isDebugEnabled()) LOG.debug("Checkpointed delta of memento in {}: "
                + "updated {} entities, {} locations, {} policies, {} enrichers, {} catalog items; "
                + "removed {} entities, {} locations, {} policies, {} enrichers, {} catalog items",
                    new Object[] {Time.makeTimeStringRounded(stopwatch),
                        delta.entities().size(), delta.locations().size(), delta.policies().size(), delta.enrichers().size(), delta.catalogItems().size(),
                        delta.removedEntityIds().size(), delta.removedLocationIds().size(), delta.removedPolicyIds().size(), delta.removedEnricherIds().size(), delta.removedCatalogItemIds().size()});
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
            
            for (EntityMemento entity : delta.entities()) {
                futures.add(asyncPersist("entities", entity, exceptionHandler));
            }
            for (LocationMemento location : delta.locations()) {
                futures.add(asyncPersist("locations", location, exceptionHandler));
            }
            for (PolicyMemento policy : delta.policies()) {
                futures.add(asyncPersist("policies", policy, exceptionHandler));
            }
            for (EnricherMemento enricher : delta.enrichers()) {
                futures.add(asyncPersist("enrichers", enricher, exceptionHandler));
            }
            for (FeedMemento feed : delta.feeds()) {
                futures.add(asyncPersist("feeds", feed, exceptionHandler));
            }
            for (CatalogItemMemento catalogItem : delta.catalogItems()) {
                futures.add(asyncPersist("catalog", catalogItem, exceptionHandler));
            }
            
            for (String id : delta.removedEntityIds()) {
                futures.add(asyncDelete("entities", id, exceptionHandler));
            }
            for (String id : delta.removedLocationIds()) {
                futures.add(asyncDelete("locations", id, exceptionHandler));
            }
            for (String id : delta.removedPolicyIds()) {
                futures.add(asyncDelete("policies", id, exceptionHandler));
            }
            for (String id : delta.removedEnricherIds()) {
                futures.add(asyncDelete("enrichers", id, exceptionHandler));
            }
            for (String id : delta.removedFeedIds()) {
                futures.add(asyncDelete("feeds", id, exceptionHandler));
            }
            for (String id : delta.removedCatalogItemIds()) {
                futures.add(asyncDelete("catalog", id, exceptionHandler));
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
    @VisibleForTesting
    public void waitForWritesCompleted(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        waitForWritesCompleted(Duration.of(timeout, unit));
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
            getWriter(getPath(subPath, memento.getId())).put(serializer.toString(memento));
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
        return subPath+"/"+id;
    }

    @Override
    public String getBackingStoreDescription() {
        return getObjectStore().getSummaryName();
    }
}

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
import brooklyn.mementos.EnricherMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.Memento;
import brooklyn.mementos.PolicyMemento;
import brooklyn.util.exceptions.CompoundRuntimeException;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;
import brooklyn.util.xstream.XmlUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/** Implementation of the {@link BrooklynMementoPersister} backed by a pluggable
 * {@link PersistenceObjectStore} such as a file system or a jclouds object store */
public class BrooklynMementoPersisterToObjectStore implements BrooklynMementoPersister {

    // TODO Crazy amount of duplication between handling entity, location, policy + enricher;
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

    private volatile boolean running = true;

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

        // FIXME does it belong here or to ManagementPlaneSyncRecordPersisterToObjectStore ?
        objectStore.createSubPath("plane");
        
        executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(maxThreadPoolSize, new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                return new Thread(r, "brooklyn-persister");
            }}));
    }

    @Override
    public void stop(boolean graceful) {
        running = false;
        if (executor != null) {
            if (graceful) {
                // a very long timeout to ensure we don't lose state. 
                // If persisting thousands of entities over slow network to Object Store, could take minutes.
                executor.shutdown();
                try {
                    executor.awaitTermination(1, TimeUnit.HOURS);
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

    @Override
    public BrooklynMementoManifest loadMementoManifest(final RebindExceptionHandler exceptionHandler) throws IOException {
        if (!running) {
            throw new IllegalStateException("Persister not running; cannot load memento manifest from " + objectStore.getSummaryName());
        }

        List<String> entitySubPathList;
        List<String> locationSubPathList;
        List<String> policySubPathList;
        List<String> enricherSubPathList;
        try {
            entitySubPathList = objectStore.listContentsWithSubPath("entities");
            locationSubPathList = objectStore.listContentsWithSubPath("locations");
            policySubPathList = objectStore.listContentsWithSubPath("policies");
            enricherSubPathList = objectStore.listContentsWithSubPath("enrichers");
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            exceptionHandler.onLoadBrooklynMementoFailed("Failed to list files", e);
            throw new IllegalStateException("Failed to list memento files in "+objectStore, e);
        }

        Stopwatch stopwatch = Stopwatch.createStarted();

        LOG.debug("Scanning persisted state: {} entities, {} locations, {} policies, {} enrichers, from {}", new Object[]{
            entitySubPathList.size(), locationSubPathList.size(), policySubPathList.size(), enricherSubPathList.size(),
            objectStore.getSummaryName() });

        final BrooklynMementoManifestImpl.Builder builder = BrooklynMementoManifestImpl.builder();

        List<ListenableFuture<?>> futures = Lists.newArrayList();
        
        for (final String subPath : entitySubPathList) {
            futures.add(executor.submit(new Runnable() {
                public void run() {
                    try {
                        String contents = read(subPath);
                        String id = (String) XmlUtil.xpath(contents, "/entity/id");
                        String type = (String) XmlUtil.xpath(contents, "/entity/type");
                        builder.entity(id, type);
                    } catch (Exception e) {
                        Exceptions.propagateIfFatal(e);
                        exceptionHandler.onLoadEntityMementoFailed("Memento "+subPath, e);
                    }
                }}));
        }
        for (final String subPath : locationSubPathList) {
            futures.add(executor.submit(new Runnable() {
                public void run() {
                    try {
                        String contents = read(subPath);
                        String id = (String) XmlUtil.xpath(contents, "/location/id");
                        String type = (String) XmlUtil.xpath(contents, "/location/type");
                        builder.location(id, type);
                    } catch (Exception e) {
                        Exceptions.propagateIfFatal(e);
                        exceptionHandler.onLoadLocationMementoFailed("Memento "+subPath, e);
                    }
                }}));
        }
        for (final String subPath : policySubPathList) {
            futures.add(executor.submit(new Runnable() {
                public void run() {
                    try {
                        String contents = read(subPath);
                        String id = (String) XmlUtil.xpath(contents, "/policy/id");
                        String type = (String) XmlUtil.xpath(contents, "/policy/type");
                        builder.policy(id, type);
                    } catch (Exception e) {
                        Exceptions.propagateIfFatal(e);
                        exceptionHandler.onLoadPolicyMementoFailed("Memento "+subPath, e);
                    }
                }}));
        }
        for (final String subPath : enricherSubPathList) {
            futures.add(executor.submit(new Runnable() {
                public void run() {
                    try {
                        String contents = read(subPath);
                        String id = (String) XmlUtil.xpath(contents, "/enricher/id");
                        String type = (String) XmlUtil.xpath(contents, "/enricher/type");
                        builder.enricher(id, type);
                    } catch (Exception e) {
                        Exceptions.propagateIfFatal(e);
                        exceptionHandler.onLoadEnricherMementoFailed("Memento "+subPath, e);
                    }
                }}));
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

        BrooklynMementoManifest result = builder.build();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Loaded memento manifest; took {}; {} entities, {} locations, {} policies, {} enrichers, from {}", new Object[]{
                     Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS)), result.getEntityIdToType().size(), 
                     result.getLocationIdToType().size(), result.getPolicyIdToType().size(), result.getEnricherIdToType().size(),
                     objectStore.getSummaryName() });
        }

        if (result.getEntityIdToType().size() != entitySubPathList.size()) {
            LOG.error("Lost an entity?!");
        }
        
        return result;
    }

    @Override
    public BrooklynMemento loadMemento(LookupContext lookupContext, final RebindExceptionHandler exceptionHandler) throws IOException {
        if (!running) {
            throw new IllegalStateException("Persister not running; cannot load memento from " + objectStore.getSummaryName());
        }
        Stopwatch stopwatch = Stopwatch.createStarted();

        List<String> entitySubPathList;
        List<String> locationSubPathList;
        List<String> policySubPathList;
        List<String> enricherSubPathList;
        try {
            entitySubPathList = objectStore.listContentsWithSubPath("entities");
            locationSubPathList = objectStore.listContentsWithSubPath("locations");
            policySubPathList = objectStore.listContentsWithSubPath("policies");
            enricherSubPathList = objectStore.listContentsWithSubPath("enrichers");
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            exceptionHandler.onLoadBrooklynMementoFailed("Failed to list files", e);
            throw new IllegalStateException("Failed to list memento files in "+objectStore+": "+e, e);
        }
        
        LOG.debug("Loading persisted state: {} entities, {} locations, {} policies, {} enrichers, from {}", new Object[]{
            entitySubPathList.size(), locationSubPathList.size(), policySubPathList.size(), enricherSubPathList.size(),
            objectStore.getSummaryName() });

        final BrooklynMementoImpl.Builder builder = BrooklynMementoImpl.builder();
        serializer.setLookupContext(lookupContext);
        
        List<ListenableFuture<?>> futures = Lists.newArrayList();
        
        try {
            for (final String subPath : entitySubPathList) {
                futures.add(executor.submit(new Runnable() {
                    public void run() {
                        try {
                            EntityMemento memento = (EntityMemento) serializer.fromString(read(subPath));
                            if (memento == null) {
                                LOG.warn("No entity-memento deserialized from " + subPath + "; ignoring and continuing");
                            } else {
                                builder.entity(memento);
                                if (memento.isTopLevelApp()) {
                                    builder.applicationId(memento.getId());
                                }
                            }
                        } catch (Exception e) {
                            exceptionHandler.onLoadEntityMementoFailed("Memento "+subPath, e);
                        }
                    }}));
            }
            for (final String subPath : locationSubPathList) {
                futures.add(executor.submit(new Runnable() {
                    public void run() {
                        try {
                            LocationMemento memento = (LocationMemento) serializer.fromString(read(subPath));
                            if (memento == null) {
                                LOG.warn("No location-memento deserialized from " + subPath + "; ignoring and continuing");
                            } else {
                                builder.location(memento);
                            }
                        } catch (Exception e) {
                            exceptionHandler.onLoadLocationMementoFailed("Memento "+subPath, e);
                        }
                    }}));
            }
            for (final String subPath : policySubPathList) {
                futures.add(executor.submit(new Runnable() {
                    public void run() {
                        try {
                            StoreObjectAccessor objectAccessor = objectStore.newAccessor(subPath);
                            PolicyMemento memento = (PolicyMemento) serializer.fromString(objectAccessor.get());
                            if (memento == null) {
                                LOG.warn("No policy-memento deserialized from " + subPath + "; ignoring and continuing");
                            } else {
                                builder.policy(memento);
                            }
                        } catch (Exception e) {
                            exceptionHandler.onLoadPolicyMementoFailed("Memento "+subPath, e);
                        }
                    }}));
            }
            for (final String subPath : enricherSubPathList) {
                futures.add(executor.submit(new Runnable() {
                    public void run() {
                        try {
                            StoreObjectAccessor objectAccessor = objectStore.newAccessor(subPath);
                            EnricherMemento memento = (EnricherMemento) serializer.fromString(objectAccessor.get());
                            if (memento == null) {
                                LOG.warn("No enricher-memento deserialized from " + subPath + "; ignoring and continuing");
                            } else {
                                builder.enricher(memento);
                            }
                        } catch (Exception e) {
                            exceptionHandler.onLoadEnricherMementoFailed("Memento "+subPath, e);
                        }
                    }}));
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
                            LOG.warn("Problem loading memento", e2);
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

        } finally {
            serializer.unsetLookupContext();
        }

        BrooklynMemento result = builder.build();
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Loaded memento; took {}; {} entities, {} locations, {} policies, {} enrichers, from {}", new Object[]{
                      Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS)), result.getEntityIds().size(), 
                      result.getLocationIds().size(), result.getPolicyIds().size(), result.getEnricherIds().size(),
                      objectStore.getSummaryName() });
        }
        
        return result;
    }
    
    @Override
    public void checkpoint(BrooklynMemento newMemento, PersistenceExceptionHandler exceptionHandler) {
        if (!running) {
            if (LOG.isDebugEnabled()) LOG.debug("Ignoring checkpointing entire memento, because not running");
            return;
        }
        
        Delta delta = PersisterDeltaImpl.builder()
                .entities(newMemento.getEntityMementos().values())
                .locations(newMemento.getLocationMementos().values())
                .policies(newMemento.getPolicyMementos().values())
                .enrichers(newMemento.getEnricherMementos().values())
                .build();
        Stopwatch stopwatch = deltaImpl(delta, exceptionHandler);
        
        if (LOG.isDebugEnabled()) LOG.debug("Checkpointed entire memento in {}", Time.makeTimeStringRounded(stopwatch));
    }

    @Override
    public void delta(Delta delta, PersistenceExceptionHandler exceptionHandler) {
        if (!running) {
            if (LOG.isDebugEnabled()) LOG.debug("Ignoring checkpointed delta of memento, because not running");
            return;
        }
        Stopwatch stopwatch = deltaImpl(delta, exceptionHandler);
        
        if (LOG.isDebugEnabled()) LOG.debug("Checkpointed delta of memento in {}; updated {} entities, {} locations and {} policies; " +
                "removing {} entities, {} locations and {} policies", 
                new Object[] {Time.makeTimeStringRounded(stopwatch), delta.entities(), delta.locations(), delta.policies(),
                delta.removedEntityIds(), delta.removedLocationIds(), delta.removedPolicyIds()});
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
            lock.readLock().unlock();
            
            // Belt-and-braces: the lock above should be enough to ensure no outstanding writes, because
            // each writer is now synchronous.
            for (StoreObjectAccessorWithLock writer : writers.values())
                writer.waitForCurrentWrites(timeout);
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

    private ListenableFuture<?> asyncDelete(final String subPath, final String id, final PersistenceExceptionHandler exceptionHandler) {
        return executor.submit(new Runnable() {
            public void run() {
                delete(subPath, id, exceptionHandler);
            }});
    }
    
    private String getPath(String subPath, String id) {
        return subPath+"/"+id;
    }
    
}

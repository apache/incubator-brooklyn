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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.rebind.BrooklynObjectType;
import brooklyn.entity.rebind.PersistenceExceptionHandler;
import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.entity.rebind.dto.BrooklynMementoImpl;
import brooklyn.entity.rebind.dto.BrooklynMementoManifestImpl;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoManifest;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.EnricherMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;
import brooklyn.util.xstream.XmlUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * @deprecated since 0.7.0 use {@link BrooklynMementoPersisterToObjectStore} instead;
 * it has a multi-file filesystem backend for equivalent functionality, but is pluggable
 * to support other storage backends 
 */
@Deprecated
public class BrooklynMementoPersisterToMultiFile implements BrooklynMementoPersister {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynMementoPersisterToMultiFile.class);

    private static final int SHUTDOWN_TIMEOUT_MS = 10*1000;
    
    private final File dir;
    
    private final File entitiesDir;
    private final File locationsDir;
    private final File policiesDir;
    private final File enrichersDir;

    private final ConcurrentMap<String, MementoFileWriter<EntityMemento>> entityWriters = new ConcurrentHashMap<String, MementoFileWriter<EntityMemento>>();
    private final ConcurrentMap<String, MementoFileWriter<LocationMemento>> locationWriters = new ConcurrentHashMap<String, MementoFileWriter<LocationMemento>>();
    private final ConcurrentMap<String, MementoFileWriter<PolicyMemento>> policyWriters = new ConcurrentHashMap<String, MementoFileWriter<PolicyMemento>>();
    private final ConcurrentMap<String, MementoFileWriter<EnricherMemento>> enricherWriters = new ConcurrentHashMap<String, MementoFileWriter<EnricherMemento>>();
    
    private final MementoSerializer<Object> serializer;

    private final ListeningExecutorService executor;

    private static final int MAX_SERIALIZATION_ATTEMPTS = 5;
    
    private volatile boolean running = true;


    public BrooklynMementoPersisterToMultiFile(File dir, ClassLoader classLoader) {
        this.dir = checkNotNull(dir, "dir");
        MementoSerializer<Object> rawSerializer = new XmlMementoSerializer<Object>(classLoader);
        this.serializer = new RetryingMementoSerializer<Object>(rawSerializer, MAX_SERIALIZATION_ATTEMPTS);
        
        checkDirIsAccessible(dir);
        
        entitiesDir = new File(dir, "entities");
        entitiesDir.mkdir();
        checkDirIsAccessible(entitiesDir);
        
        locationsDir = new File(dir, "locations");
        locationsDir.mkdir();
        checkDirIsAccessible(locationsDir);
        
        policiesDir = new File(dir, "policies");
        policiesDir.mkdir();
        checkDirIsAccessible(policiesDir);
        
        enrichersDir = new File(dir, "enrichers");
        enrichersDir.mkdir();
        checkDirIsAccessible(enrichersDir);

        File planeDir = new File(dir, "plane");
        planeDir.mkdir();
        checkDirIsAccessible(planeDir);
        
        this.executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        
        LOG.info("Memento-persister will use directory {}", dir);
    }
    
    @Override
    public void stop(boolean graceful) {
        running = false;
        if (graceful) {
            executor.shutdown();
            try {
                executor.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw Exceptions.propagate(e);
            }
        } else {
            executor.shutdownNow();
        }
    }
    
    @Override
    public BrooklynMementoManifest loadMementoManifest(RebindExceptionHandler exceptionHandler) throws IOException {
        if (!running) {
            throw new IllegalStateException("Persister not running; cannot load memento manifest from "+dir);
        }
        
        Stopwatch stopwatch = Stopwatch.createStarted();

        FileFilter fileFilter = new FileFilter() {
            @Override public boolean accept(File file) {
                return !file.getName().endsWith(".tmp");
            }
        };
        File[] entityFiles;
        File[] locationFiles;
        File[] policyFiles;
        File[] enricherFiles;
        try {
            entityFiles = entitiesDir.listFiles(fileFilter);
            locationFiles = locationsDir.listFiles(fileFilter);
            policyFiles = policiesDir.listFiles(fileFilter);
            enricherFiles = enrichersDir.listFiles(fileFilter);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            exceptionHandler.onLoadMementoFailed(BrooklynObjectType.UNKNOWN, "Failed to list files", e);
            throw new IllegalStateException("Failed to list memento files in "+dir, e);
        }
        
        LOG.info("Loading memento manifest from {}; {} entities, {} locations, {} policies, {} enrichers", 
                new Object[] {dir, entityFiles.length, locationFiles.length, policyFiles.length, enricherFiles.length});
        
        BrooklynMementoManifestImpl.Builder builder = BrooklynMementoManifestImpl.builder();
        
        try {
            for (File file : entityFiles) {
                try {
                    String contents = readFile(file);
                    String id = (String) XmlUtil.xpath(contents, "/entity/id");
                    String type = (String) XmlUtil.xpath(contents, "/entity/type");
                    builder.entity(id, type);
                } catch (Exception e) {
                    exceptionHandler.onLoadMementoFailed(BrooklynObjectType.ENTITY, "File "+file, e);
                }
            }
            for (File file : locationFiles) {
                try {
                    String contents = readFile(file);
                    String id = (String) XmlUtil.xpath(contents, "/location/id");
                    String type = (String) XmlUtil.xpath(contents, "/location/type");
                    builder.location(id, type);
                } catch (Exception e) {
                    exceptionHandler.onLoadMementoFailed(BrooklynObjectType.LOCATION, "File "+file, e);
                }
            }
            for (File file : policyFiles) {
                try {
                    String contents = readFile(file);
                    String id = (String) XmlUtil.xpath(contents, "/policy/id");
                    String type = (String) XmlUtil.xpath(contents, "/policy/type");
                    builder.policy(id, type);
                } catch (Exception e) {
                    exceptionHandler.onLoadMementoFailed(BrooklynObjectType.POLICY, "File "+file, e);
                }
            }
            for (File file : enricherFiles) {
                try {
                    String contents = readFile(file);
                    String id = (String) XmlUtil.xpath(contents, "/enricher/id");
                    String type = (String) XmlUtil.xpath(contents, "/enricher/type");
                    builder.enricher(id, type);
                } catch (Exception e) {
                    exceptionHandler.onLoadMementoFailed(BrooklynObjectType.ENRICHER, "File "+file, e);
                }
            }
            
            if (LOG.isDebugEnabled()) LOG.debug("Loaded memento manifest; took {}", Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS))); 
            return builder.build();
            
        } finally {
            serializer.unsetLookupContext();
        }
    }

    @Override
    public BrooklynMemento loadMemento(LookupContext lookupContext, RebindExceptionHandler exceptionHandler) throws IOException {
        if (!running) {
            throw new IllegalStateException("Persister not running; cannot load memento from "+dir);
        }
        
        Stopwatch stopwatch = Stopwatch.createStarted();

        FileFilter fileFilter = new FileFilter() {
            @Override public boolean accept(File file) {
                return !file.getName().endsWith(".tmp");
            }
        };
        File[] entityFiles;
        File[] locationFiles;
        File[] policyFiles;
        File[] enricherFiles;
        try {
            entityFiles = entitiesDir.listFiles(fileFilter);
            locationFiles = locationsDir.listFiles(fileFilter);
            policyFiles = policiesDir.listFiles(fileFilter);
            enricherFiles = enrichersDir.listFiles(fileFilter);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            exceptionHandler.onLoadMementoFailed(BrooklynObjectType.UNKNOWN, "Failed to list files", e);
            throw new IllegalStateException("Failed to list memento files in "+dir, e);
        }

        LOG.info("Loading memento from {}; {} entities, {} locations, {} policies and {} enrichers", 
                new Object[] {dir, entityFiles.length, locationFiles.length, policyFiles.length}, enricherFiles.length);
        
        BrooklynMementoImpl.Builder builder = BrooklynMementoImpl.builder();
        
        serializer.setLookupContext(lookupContext);
        try {
            for (File file : entityFiles) {
                try {
                    EntityMemento memento = (EntityMemento) serializer.fromString(readFile(file));
                    if (memento == null) {
                        LOG.warn("No entity-memento deserialized from file "+file+"; ignoring and continuing");
                    } else {
                        builder.entity(memento);
                        if (memento.isTopLevelApp()) {
                            builder.applicationId(memento.getId());
                        }
                    }
                } catch (Exception e) {
                    exceptionHandler.onLoadMementoFailed(BrooklynObjectType.ENTITY, "File "+file, e);
                }
            }
            for (File file : locationFiles) {
                try {
                    LocationMemento memento = (LocationMemento) serializer.fromString(readFile(file));
                    if (memento == null) {
                        LOG.warn("No location-memento deserialized from file "+file+"; ignoring and continuing");
                    } else {
                        builder.location(memento);
                    }
                } catch (Exception e) {
                    exceptionHandler.onLoadMementoFailed(BrooklynObjectType.LOCATION, "File "+file, e);
                }
            }
            for (File file : policyFiles) {
                try {
                    PolicyMemento memento = (PolicyMemento) serializer.fromString(readFile(file));
                    if (memento == null) {
                        LOG.warn("No policy-memento deserialized from file "+file+"; ignoring and continuing");
                    } else {
                        builder.policy(memento);
                    }
                } catch (Exception e) {
                    exceptionHandler.onLoadMementoFailed(BrooklynObjectType.POLICY, "File "+file, e);
                }
            }
            for (File file : enricherFiles) {
                EnricherMemento memento = (EnricherMemento) serializer.fromString(readFile(file));
                if (memento == null) {
                    LOG.warn("No enricher-memento deserialized from file "+file+"; ignoring and continuing");
                } else {
                    builder.enricher(memento);
                }
            }
            
            if (LOG.isDebugEnabled()) LOG.debug("Loaded memento; took {}", Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS))); 
            return builder.build();
            
        } finally {
            serializer.unsetLookupContext();
        }
    }
    
    @Override
    public void checkpoint(BrooklynMemento newMemento, PersistenceExceptionHandler exceptionHandler) {
        if (!running) {
            if (LOG.isDebugEnabled()) LOG.debug("Ignoring checkpointing entire memento, because not running");
            return;
        }
        if (LOG.isDebugEnabled()) LOG.debug("Checkpointing entire memento");
        
        for (EntityMemento m : newMemento.getEntityMementos().values()) {
            persist(m);
        }
        for (LocationMemento m : newMemento.getLocationMementos().values()) {
            persist(m);
        }
        for (PolicyMemento m : newMemento.getPolicyMementos().values()) {
            persist(m);
        }
        for (EnricherMemento m : newMemento.getEnricherMementos().values()) {
            persist(m);
        }
    }
    
    @Override
    public void delta(Delta delta, PersistenceExceptionHandler exceptionHandler) {
        if (!running) {
            if (LOG.isDebugEnabled()) LOG.debug("Ignoring checkpointed delta of memento, because not running");
            return;
        }
        if (LOG.isTraceEnabled()) LOG.trace("Checkpointed delta of memento; updating {} entities, {} locations, {} policies and {} enrichers; " +
        		"removing {} entities, {} locations {} policies and {} enrichers", 
                new Object[] {delta.entities(), delta.locations(), delta.policies(), delta.enrichers(),
                delta.removedEntityIds(), delta.removedLocationIds(), delta.removedPolicyIds(), delta.removedEnricherIds()});
        
        for (EntityMemento entity : delta.entities()) {
            persist(entity);
        }
        for (LocationMemento location : delta.locations()) {
            persist(location);
        }
        for (PolicyMemento policy : delta.policies()) {
            persist(policy);
        }
        for (EnricherMemento enricher : delta.enrichers()) {
            persist(enricher);
        }
        for (String id : delta.removedEntityIds()) {
            deleteEntity(id);
        }
        for (String id : delta.removedLocationIds()) {
            deleteLocation(id);
        }
        for (String id : delta.removedPolicyIds()) {
            deletePolicy(id);
        }
        for (String id : delta.removedEnricherIds()) {
            deleteEnricher(id);
        }
    }

    @VisibleForTesting
    public File getDir() {
        return dir;
    }
    
    @Override
    @VisibleForTesting
    public void waitForWritesCompleted(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        waitForWritesCompleted(Duration.of(timeout, unit));
    }
    
    public void waitForWritesCompleted(Duration timeout) throws InterruptedException, TimeoutException {
        for (MementoFileWriter<?> writer : entityWriters.values()) {
            writer.waitForWriteCompleted(timeout);
        }
        for (MementoFileWriter<?> writer : locationWriters.values()) {
            writer.waitForWriteCompleted(timeout);
        }
        for (MementoFileWriter<?> writer : policyWriters.values()) {
            writer.waitForWriteCompleted(timeout);
        }
        for (MementoFileWriter<?> writer : enricherWriters.values()) {
            writer.waitForWriteCompleted(timeout);
        }
    }

    // TODO Promote somewhere sensible; share code with BrooklynLauncher.checkPersistenceDirAccessible
    public static void checkDirIsAccessible(File dir) {
        if (!(dir.exists() && dir.isDirectory() && dir.canRead() && dir.canWrite())) {
            throw new IllegalStateException("Invalid directory "+dir+" because "+
                    (!dir.exists() ? "does not exist" :
                        (!dir.isDirectory() ? "not a directory" :
                            (!dir.canRead() ? "not readable" :
                                (!dir.canWrite() ? "not writable" : "unknown reason")))));
        }
    }

    private String readFile(File file) throws IOException {
        return Files.asCharSource(file, Charsets.UTF_8).read();
    }
    
    private void persist(EntityMemento entity) {
        MementoFileWriter<EntityMemento> writer = entityWriters.get(entity.getId());
        if (writer == null) {
            entityWriters.putIfAbsent(entity.getId(), new MementoFileWriter<EntityMemento>(getFileFor(entity), executor, serializer));
            writer = entityWriters.get(entity.getId());
        }
        writer.write(entity);
    }
    
    private void persist(LocationMemento location) {
        MementoFileWriter<LocationMemento> writer = locationWriters.get(location.getId());
        if (writer == null) {
            locationWriters.putIfAbsent(location.getId(), new MementoFileWriter<LocationMemento>(getFileFor(location), executor, serializer));
            writer = locationWriters.get(location.getId());
        }
        writer.write(location);
    }
    
    private void persist(PolicyMemento policy) {
        MementoFileWriter<PolicyMemento> writer = policyWriters.get(policy.getId());
        if (writer == null) {
            policyWriters.putIfAbsent(policy.getId(), new MementoFileWriter<PolicyMemento>(getFileFor(policy), executor, serializer));
            writer = policyWriters.get(policy.getId());
        }
        writer.write(policy);
    }
    
    private void persist(EnricherMemento enricher) {
        MementoFileWriter<EnricherMemento> writer = enricherWriters.get(enricher.getId());
        if (writer == null) {
            enricherWriters.putIfAbsent(enricher.getId(), new MementoFileWriter<EnricherMemento>(getFileFor(enricher), executor, serializer));
            writer = enricherWriters.get(enricher.getId());
        }
        writer.write(enricher);
    }

    private void deleteEntity(String id) {
        MementoFileWriter<EntityMemento> writer = entityWriters.get(id);
        if (writer != null) {
            writer.delete();
        }
    }
    
    private void deleteLocation(String id) {
        MementoFileWriter<LocationMemento> writer = locationWriters.get(id);
        if (writer != null) {
            writer.delete();
        }
    }
    
    private void deletePolicy(String id) {
        MementoFileWriter<PolicyMemento> writer = policyWriters.get(id);
        if (writer != null) {
            writer.delete();
        }
    }
    
    private void deleteEnricher(String id) {
        MementoFileWriter<EnricherMemento> writer = enricherWriters.get(id);
        if (writer != null) {
            writer.delete();
        }
    }

    private File getFileFor(EntityMemento entity) {
        return new File(entitiesDir, entity.getId());
    }
    
    private File getFileFor(LocationMemento location) {
        return new File(locationsDir, location.getId());
    }
    
    private File getFileFor(PolicyMemento policy) {
        return new File(policiesDir, policy.getId());
    }
    
    private File getFileFor(EnricherMemento enricher) {
        return new File(enrichersDir, enricher.getId());
    }
    
    @Override
    public String getBackingStoreDescription() {
        return toString();
    }
    
}

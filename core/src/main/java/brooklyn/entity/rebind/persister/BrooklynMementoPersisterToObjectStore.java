package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.entity.rebind.dto.BrooklynMementoImpl;
import brooklyn.entity.rebind.dto.BrooklynMementoManifestImpl;
import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessor;
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
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;

/** Implementation of the {@link BrooklynMementoPersister} backed by a pluggable
 * {@link PersistenceObjectStore} such as a file system or a jclouds object store */
public class BrooklynMementoPersisterToObjectStore implements BrooklynMementoPersister {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynMementoPersisterToObjectStore.class);

    private static final int MAX_SERIALIZATION_ATTEMPTS = 5;

    private final PersistenceObjectStore objectStore;
    private final MementoSerializer<Object> serializer;

    // TODO it's 95% the same code for each of these, so refactor to avoid repetition
    private final ConcurrentMap<String, StoreObjectAccessor> entityWriters = Maps.newConcurrentMap();
    private final ConcurrentMap<String, StoreObjectAccessor> locationWriters = Maps.newConcurrentMap();
    private final ConcurrentMap<String, StoreObjectAccessor> policyWriters = Maps.newConcurrentMap();
    private final ConcurrentMap<String, StoreObjectAccessor> enricherWriters = Maps.newConcurrentMap();

    private volatile boolean running = true;

    public BrooklynMementoPersisterToObjectStore(PersistenceObjectStore objectStore, ClassLoader classLoader) {
        this.objectStore = checkNotNull(objectStore, "objectStore");
        MementoSerializer<Object> rawSerializer = new XmlMementoSerializer<Object>(classLoader);
        this.serializer = new RetryingMementoSerializer<Object>(rawSerializer, MAX_SERIALIZATION_ATTEMPTS);

        objectStore.createSubPath("entities");
        objectStore.createSubPath("locations");
        objectStore.createSubPath("policies");
        objectStore.createSubPath("enrichers");

        // FIXME does it belong here or to ManagementPlaneSyncRecordPersisterToObjectStore ?
        objectStore.createSubPath("plane");
    }

    public PersistenceObjectStore getObjectStore() {
        return objectStore;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public BrooklynMementoManifest loadMementoManifest(RebindExceptionHandler exceptionHandler) throws IOException {
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
            if (exceptionHandler!=null)
                exceptionHandler.onLoadBrooklynMementoFailed("Failed to list files", e);
            throw new IllegalStateException("Failed to list memento files in "+objectStore, e);
        }

        Stopwatch stopwatch = Stopwatch.createStarted();

        LOG.info("Loading memento from {}; {} entities, {} locations, {} policies, {} enrichers",
            new Object[]{objectStore.getSummaryName(), entitySubPathList.size(), locationSubPathList.size(), policySubPathList.size(), enricherSubPathList.size()});

        BrooklynMementoManifestImpl.Builder builder = BrooklynMementoManifestImpl.builder();

        for (String subPath : entitySubPathList) {
            try {
                StoreObjectAccessor objectAccessor = objectStore.newAccessor(subPath);
                String contents = objectAccessor.read();
                String id = (String) XmlUtil.xpath(contents, "/entity/id");
                String type = (String) XmlUtil.xpath(contents, "/entity/type");
                builder.entity(id, type);
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                exceptionHandler.onLoadEntityMementoFailed("Memento "+subPath, e);
            }
        }
        for (String subPath : locationSubPathList) {
            try {
                StoreObjectAccessor objectAccessor = objectStore.newAccessor(subPath);
                String contents = objectAccessor.read();
                String id = (String) XmlUtil.xpath(contents, "/location/id");
                String type = (String) XmlUtil.xpath(contents, "/location/type");
                builder.location(id, type);
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                exceptionHandler.onLoadLocationMementoFailed("Memento "+subPath, e);
            }
        }
        for (String subPath : policySubPathList) {
            try {
                StoreObjectAccessor objectAccessor = objectStore.newAccessor(subPath);
                String contents = objectAccessor.read();
                String id = (String) XmlUtil.xpath(contents, "/policy/id");
                String type = (String) XmlUtil.xpath(contents, "/policy/type");
                builder.policy(id, type);
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                exceptionHandler.onLoadPolicyMementoFailed("Memento "+subPath, e);
            }
        }
        for (String subPath : enricherSubPathList) {
            try {
                StoreObjectAccessor objectAccessor = objectStore.newAccessor(subPath);
                String contents = objectAccessor.read();
                String id = (String) XmlUtil.xpath(contents, "/enricher/id");
                String type = (String) XmlUtil.xpath(contents, "/enricher/type");
                builder.enricher(id, type);
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                exceptionHandler.onLoadEnricherMementoFailed("Memento "+subPath, e);
            }
        }
        
        if (LOG.isDebugEnabled())
            LOG.debug("Loaded memento manifest; took {}", Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS)));
        return builder.build();
    }

    @Override
    public BrooklynMemento loadMemento(LookupContext lookupContext, RebindExceptionHandler exceptionHandler) throws IOException {
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
            if (exceptionHandler!=null)
                exceptionHandler.onLoadBrooklynMementoFailed("Failed to list files", e);
            throw new IllegalStateException("Failed to list memento files in "+objectStore+": "+e, e);
        }
        
        LOG.info("Loading memento from {}; {} entities, {} locations, {} policies, {} enrichers",
                new Object[]{objectStore.getSummaryName(), entitySubPathList.size(), locationSubPathList.size(), policySubPathList.size(), enricherSubPathList.size()});

        BrooklynMementoImpl.Builder builder = BrooklynMementoImpl.builder();
        serializer.setLookupContext(lookupContext);
        try {
            for (String subPath : entitySubPathList) {
                try {
                    StoreObjectAccessor objectAccessor = objectStore.newAccessor(subPath);
                    EntityMemento memento = (EntityMemento) serializer.fromString(objectAccessor.read());
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
            }
            for (String subPath : locationSubPathList) {
                try {
                    StoreObjectAccessor objectAccessor = objectStore.newAccessor(subPath);
                    LocationMemento memento = (LocationMemento) serializer.fromString(objectAccessor.read());
                    if (memento == null) {
                        LOG.warn("No location-memento deserialized from " + subPath + "; ignoring and continuing");
                    } else {
                        builder.location(memento);
                    }
                } catch (Exception e) {
                    exceptionHandler.onLoadLocationMementoFailed("Memento "+subPath, e);
                }
            }
            for (String subPath : policySubPathList) {
                try {
                    StoreObjectAccessor objectAccessor = objectStore.newAccessor(subPath);
                    PolicyMemento memento = (PolicyMemento) serializer.fromString(objectAccessor.read());
                    if (memento == null) {
                        LOG.warn("No policy-memento deserialized from " + subPath + "; ignoring and continuing");
                    } else {
                        builder.policy(memento);
                    }
                } catch (Exception e) {
                    exceptionHandler.onLoadPolicyMementoFailed("Memento "+subPath, e);
                }
            }
            for (String subPath : enricherSubPathList) {
                try {
                    StoreObjectAccessor objectAccessor = objectStore.newAccessor(subPath);
                    EnricherMemento memento = (EnricherMemento) serializer.fromString(objectAccessor.read());
                    if (memento == null) {
                        LOG.warn("No enricher-memento deserialized from " + subPath + "; ignoring and continuing");
                    } else {
                        builder.enricher(memento);
                    }
                } catch (Exception e) {
                    exceptionHandler.onLoadEnricherMementoFailed("Memento "+subPath, e);
                }
            }
            
        } finally {
            serializer.unsetLookupContext();
        }

        if (LOG.isDebugEnabled()) LOG.debug("Loaded memento; took {}", Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS)));
        return builder.build();
    }
    
    @Override
    public void checkpoint(BrooklynMemento newMemento) {
        if (!running) {
            if (LOG.isDebugEnabled()) LOG.debug("Ignoring checkpointing entire memento, because not running");
            return;
        }
        if (LOG.isDebugEnabled()) LOG.debug("Checkpointing entire memento");
        
        for (EntityMemento entity : newMemento.getEntityMementos().values()) {
            persist(entity);
        }
        for (LocationMemento location : newMemento.getLocationMementos().values()) {
            persist(location);
        }
        for (PolicyMemento policy : newMemento.getPolicyMementos().values()) {
            persist(policy);
        }
        for (EnricherMemento enricher : newMemento.getEnricherMementos().values()) {
            persist(enricher);
        }
    }
    
    @Override
    public void delta(Delta delta) {
        if (!running) {
            if (LOG.isDebugEnabled()) LOG.debug("Ignoring checkpointed delta of memento, because not running");
            return;
        }
        if (LOG.isDebugEnabled()) LOG.debug("Checkpointed delta of memento; updating {} entities, {} locations and {} policies; " +
        		"removing {} entities, {} locations and {} policies", 
                new Object[] {delta.entities(), delta.locations(), delta.policies(),
                delta.removedEntityIds(), delta.removedLocationIds(), delta.removedPolicyIds()});
        
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

    @Override
    @VisibleForTesting
    public void waitForWritesCompleted(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        waitForWritesCompleted(Duration.of(timeout, unit));
    }
    
    public void waitForWritesCompleted(Duration timeout) throws InterruptedException, TimeoutException {
        for (StoreObjectAccessor writer : entityWriters.values()) {
            writer.waitForWriteCompleted(timeout);
        }
        for (StoreObjectAccessor writer : locationWriters.values()) {
            writer.waitForWriteCompleted(timeout);
        }
        for (StoreObjectAccessor writer : policyWriters.values()) {
            writer.waitForWriteCompleted(timeout);
        }
        for (StoreObjectAccessor writer : enricherWriters.values()) {
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
    
    private void persist(EntityMemento entity) {
        StoreObjectAccessor writer = entityWriters.get(entity.getId());
        if (writer == null) {
            entityWriters.putIfAbsent(entity.getId(), objectStore.newAccessor("entities/"+entity.getId()));
            writer = entityWriters.get(entity.getId());
        }
        writer.writeAsync(serializer.toString(entity));
    }
    
    private void persist(LocationMemento location) {
        StoreObjectAccessor writer = locationWriters.get(location.getId());
        if (writer == null) {
            locationWriters.putIfAbsent(location.getId(), objectStore.newAccessor("locations/"+location.getId()));
            writer = locationWriters.get(location.getId());
        }
        writer.writeAsync(serializer.toString(location));
    }
    
    private void persist(PolicyMemento policy) {
        StoreObjectAccessor writer = policyWriters.get(policy.getId());
        if (writer == null) {
            policyWriters.putIfAbsent(policy.getId(), objectStore.newAccessor("policies/"+policy.getId()));
            writer = policyWriters.get(policy.getId());
        }
        writer.writeAsync(serializer.toString(policy));
    }

    private void persist(EnricherMemento enricher) {
        StoreObjectAccessor writer = enricherWriters.get(enricher.getId());
        if (writer == null) {
            enricherWriters.putIfAbsent(enricher.getId(), objectStore.newAccessor("enrichers/"+enricher.getId()));
            writer = enricherWriters.get(enricher.getId());
        }
        writer.writeAsync(serializer.toString(enricher));
    }

    private void deleteEntity(String id) {
        StoreObjectAccessor writer = entityWriters.get(id);
        if (writer != null) {
            writer.deleteAsync();
        }
    }
    
    private void deleteLocation(String id) {
        StoreObjectAccessor writer = locationWriters.get(id);
        if (writer != null) {
            writer.deleteAsync();
        }
    }
    
    private void deletePolicy(String id) {
        StoreObjectAccessor writer = policyWriters.get(id);
        if (writer != null) {
            writer.deleteAsync();
        }
    }

    private void deleteEnricher(String id) {
        StoreObjectAccessor writer = enricherWriters.get(id);
        if (writer != null) {
            writer.deleteAsync();
        }
    }

}

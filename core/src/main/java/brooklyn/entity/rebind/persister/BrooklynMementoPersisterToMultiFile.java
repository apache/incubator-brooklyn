package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkArgument;
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

import brooklyn.entity.rebind.dto.BrooklynMementoImpl;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

public class BrooklynMementoPersisterToMultiFile implements BrooklynMementoPersister {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynMementoPersisterToMultiFile.class);

    private static final int SHUTDOWN_TIMEOUT_MS = 10*1000;
    
    private final File dir;
    private final File entitiesDir;
    private final File locationsDir;
    private final File policiesDir;

    private final ConcurrentMap<String, MementoFileWriter<EntityMemento>> entityWriters = new ConcurrentHashMap<String, MementoFileWriter<EntityMemento>>();
    private final ConcurrentMap<String, MementoFileWriter<LocationMemento>> locationWriters = new ConcurrentHashMap<String, MementoFileWriter<LocationMemento>>();
    private final ConcurrentMap<String, MementoFileWriter<PolicyMemento>> policyWriters = new ConcurrentHashMap<String, MementoFileWriter<PolicyMemento>>();
    
    private final MementoSerializer<Object> serializer;

    private final ListeningExecutorService executor;

    private static final int MAX_SERIALIZATION_ATTEMPTS = 5;
    
    private volatile boolean running = true;
    
    public BrooklynMementoPersisterToMultiFile(File dir, ClassLoader classLoader) {
        this.dir = checkNotNull(dir, "dir");
        MementoSerializer<Object> rawSerializer = new XmlMementoSerializer<Object>(classLoader);
//        this.serializer = new JsonMementoSerializer(classLoader);
        this.serializer = new RetryingMementoSerializer<Object>(rawSerializer, MAX_SERIALIZATION_ATTEMPTS);
        
        checkArgument(dir.isDirectory() && dir.canWrite(), "dir "+dir+" is not a writable directory");
        
        entitiesDir = new File(dir, "entities");
        entitiesDir.mkdir();
        checkArgument(entitiesDir.isDirectory() && entitiesDir.canWrite(), "dir "+entitiesDir+" is not a writable directory");
        
        locationsDir = new File(dir, "locations");
        locationsDir.mkdir();
        checkArgument(locationsDir.isDirectory() && locationsDir.canWrite(), "dir "+locationsDir+" is not a writable directory");
        
        policiesDir = new File(dir, "policies");
        policiesDir.mkdir();
        checkArgument(policiesDir.isDirectory() && policiesDir.canWrite(), "dir "+policiesDir+" is not a writable directory");
        
        this.executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        
        LOG.info("Memento-persister will use directory {}", dir);
    }
    
    @Override
    public void stop() {
        running = false;
        executor.shutdown();
        try {
            executor.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
    }
    
    @Override
    public BrooklynMemento loadMemento() throws IOException {
        FileFilter fileFilter = new FileFilter() {
            @Override public boolean accept(File file) {
                return !file.getName().endsWith(".tmp");
            }
        };
        File[] entityFiles = entitiesDir.listFiles(fileFilter);
        File[] locationFiles = locationsDir.listFiles(fileFilter);
        File[] policyFiles = policiesDir.listFiles(fileFilter);

        LOG.info("Loading memento from {}; {} entities, {} locations, {} policies", 
                new Object[] {dir, entityFiles.length, locationFiles.length, policyFiles.length});
        
        BrooklynMementoImpl.Builder builder = BrooklynMementoImpl.builder();
        
        for (File file : entityFiles) {
            EntityMemento memento = (EntityMemento) serializer.fromString(readFile(file));
            builder.entity(memento);
            if (memento.isTopLevelApp()) {
                builder.applicationId(memento.getId());
            }
        }
        for (File file : locationFiles) {
            LocationMemento memento = (LocationMemento) serializer.fromString(readFile(file));
            builder.location(memento);
        }
        for (File file : policyFiles) {
            PolicyMemento memento = (PolicyMemento) serializer.fromString(readFile(file));
            builder.policy(memento);
        }
        return builder.build();
    }
    
    @Override
    public void checkpoint(BrooklynMemento newMemento) {
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
        for (String id : delta.removedEntityIds()) {
            deleteEntity(id);
        }
        for (String id : delta.removedLocationIds()) {
            deleteLocation(id);
        }
        for (String id : delta.removedPolicyIds()) {
            deletePolicy(id);
        }
    }

    @Override
    @VisibleForTesting
    public void waitForWritesCompleted(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        for (MementoFileWriter<?> writer : entityWriters.values()) {
            writer.waitForWriteCompleted(timeout, unit);
        }
        for (MementoFileWriter<?> writer : locationWriters.values()) {
            writer.waitForWriteCompleted(timeout, unit);
        }
        for (MementoFileWriter<?> writer : policyWriters.values()) {
            writer.waitForWriteCompleted(timeout, unit);
        }
    }

    private String readFile(File file) throws IOException {
        return Joiner.on("\n").join(Files.readLines(file, Charsets.UTF_8));
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

    private File getFileFor(EntityMemento entity) {
        return new File(entitiesDir, entity.getId());
    }
    
    private File getFileFor(LocationMemento location) {
        return new File(locationsDir, location.getId());
    }
    
    private File getFileFor(PolicyMemento policy) {
        return new File(policiesDir, policy.getId());
    }
}

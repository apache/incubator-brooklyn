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
package brooklyn.management.ha;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToMultiFile;
import brooklyn.entity.rebind.persister.FileBasedStoreObjectAccessor;
import brooklyn.entity.rebind.persister.MementoFileWriterSync;
import brooklyn.entity.rebind.persister.MementoSerializer;
import brooklyn.entity.rebind.persister.RetryingMementoSerializer;
import brooklyn.entity.rebind.persister.XmlMementoSerializer;
import brooklyn.entity.rebind.plane.dto.ManagementPlaneSyncRecordImpl;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.io.Files;

/**
 * Structure of files is:
 * <ul>
 *   <li>{@code plane/} - top-level directory
 *     <ul>
 *       <li>{@code master} - contains the id of the management-node that is currently master
 *       <li>{@code change.log} - log of changes made
 *       <li>{@code nodes/} - sub-directory, containing one file per management-node
 *         <ul>
 *           <li>{@code a9WiuVKp} - file named after the management-node's id, containing the management node's current state
 *           <li>{@code E1eDXQF3}
 *         </ul>
 *     </ul>
 * </ul>
 * 
 * All writes are done synchronously.
 * 
 * @since 0.7.0
 * 
 * @author aled
 * @deprecated since 0.7.0 use {@link ManagementPlaneSyncRecordPersisterToObjectStore} e.g. with {@link FileBasedStoreObjectAccessor}
 */
@Beta
@Deprecated
public class ManagementPlaneSyncRecordPersisterToMultiFile implements ManagementPlaneSyncRecordPersister {

    // TODO Multiple node appending to change.log could cause strange interleaving, or perhaps even data loss?
    // But this file is not critical to functionality.
    
    // TODO Should ManagementPlaneSyncRecordPersister.Delta be different so can tell what is a significant event,
    // and thus log it in change.log - currently only subset of significant things being logged.
    
    private static final Logger LOG = LoggerFactory.getLogger(ManagementPlaneSyncRecordPersisterToMultiFile.class);

    private static final Duration SHUTDOWN_TIMEOUT = Duration.TEN_SECONDS;
    
    private final String tmpSuffix;
    private final File dir;
    private final File nodesDir;

    // TODO Leak if we go through lots of managers; but tiny!
    private final ConcurrentMap<String, MementoFileWriterSync<ManagementNodeSyncRecord>> nodeWriters = new ConcurrentHashMap<String, MementoFileWriterSync<ManagementNodeSyncRecord>>();
    
    private final MementoFileWriterSync<String> masterWriter;

    private final MementoFileWriterSync<String> changeLogWriter;

    private final MementoSerializer<Object> serializer;

    private static final int MAX_SERIALIZATION_ATTEMPTS = 5;
    
    private volatile boolean running = true;

    /**
     * @param dir         Directory to write management-plane data
     * @param classLoader ClassLoader to use when deserializing data
     * @param id          Unique identifier, e.g. used for temp file suffix in case multpile concurrent writers
     */
    public ManagementPlaneSyncRecordPersisterToMultiFile(File dir, ClassLoader classLoader, String id) {
        this.dir = checkNotNull(dir, "dir");
        MementoSerializer<Object> rawSerializer = new XmlMementoSerializer<Object>(checkNotNull(classLoader, "classLoader"));
        this.serializer = new RetryingMementoSerializer<Object>(rawSerializer, MAX_SERIALIZATION_ATTEMPTS);
        this.tmpSuffix = checkNotNull(id, "id")+".tmp"; // important to end in .tmp for loadMemento's file filter
        
        BrooklynMementoPersisterToMultiFile.checkDirIsAccessible(dir);
        
        nodesDir = new File(dir, "nodes");
        nodesDir.mkdir();
        BrooklynMementoPersisterToMultiFile.checkDirIsAccessible(nodesDir);

        masterWriter = new MementoFileWriterSync<String>(getFileForMaster(), serializer, tmpSuffix);
        changeLogWriter = new MementoFileWriterSync<String>(getFileForChangeLog(), MementoSerializer.NOOP, tmpSuffix);
        
        LOG.info("ManagementPlaneMemento-persister will use directory {}", dir);
    }
    
    @Override
    public void stop() {
        running = false;
        try {
            for (MementoFileWriterSync<?> writer : nodeWriters.values()) {
                try {
                    writer.waitForWriteCompleted(SHUTDOWN_TIMEOUT);
                } catch (TimeoutException e) {
                    LOG.warn("Timeout during shutdown, waiting for write of "+writer+"; continuing");
                }
            }
            try {
                masterWriter.waitForWriteCompleted(SHUTDOWN_TIMEOUT);
            } catch (TimeoutException e) {
                LOG.warn("Timeout during shutdown, waiting for write of "+masterWriter+"; continuing");
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
    }
    
    @VisibleForTesting
    public File getDir() {
        return dir;
    }

    @Override
    public ManagementPlaneSyncRecord loadSyncRecord() throws IOException {
        if (!running) {
            throw new IllegalStateException("Persister not running; cannot load memento from "+dir);
        }
        
        // Note this is called a lot - every time we check the heartbeats
        if (LOG.isTraceEnabled()) LOG.trace("Loading management-plane memento from {}", dir);

        Stopwatch stopwatch = Stopwatch.createStarted();

        ManagementPlaneSyncRecordImpl.Builder builder = ManagementPlaneSyncRecordImpl.builder();

        // Be careful about order: if the master-file says nodeX then nodeX's file must have an up-to-date timestamp.
        // Therefore read master file first, followed by the other node-files.
        File masterFile = getFileForMaster();
        String masterNodeId = (String) (masterFile.exists() ? serializer.fromString(readFile(masterFile)) : null);
        if (masterNodeId == null) {
            LOG.warn("No entity-memento deserialized from file "+masterFile+"; ignoring and continuing");
        } else {
            builder.masterNodeId(masterNodeId);
        }
 
        // Load node-files
        FileFilter fileFilter = new FileFilter() {
            @Override public boolean accept(File file) {
                return !file.getName().endsWith(".tmp");
            }
        };
        File[] nodeFiles = nodesDir.listFiles(fileFilter);

        for (File file : nodeFiles) {
            ManagementNodeSyncRecord memento = (ManagementNodeSyncRecord) serializer.fromString(readFile(file));
            if (memento == null) {
                LOG.warn("No manager-memento deserialized from file "+file+" (possibly just stopped?); ignoring and continuing");
            } else {
                builder.node(memento);
            }
        }
        
        if (LOG.isTraceEnabled()) LOG.trace("Loaded management-plane memento; took {}", Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS)));
        return builder.build();
    }
    
    @Override
    public void delta(Delta delta) {
        if (!running) {
            if (LOG.isDebugEnabled()) LOG.debug("Persister not running; ignoring checkpointed delta of manager-memento");
            return;
        }
        if (LOG.isTraceEnabled()) LOG.trace("Checkpointed delta of manager-memento; updating {}", delta);
        
        for (ManagementNodeSyncRecord m : delta.getNodes()) {
            persist(m);
        }
        for (String id : delta.getRemovedNodeIds()) {
            deleteNode(id);
        }
        switch (delta.getMasterChange()) {
        case NO_CHANGE:
            break; // no-op
        case SET_MASTER:
            persistMaster(checkNotNull(delta.getNewMasterOrNull()));
            break;
        case CLEAR_MASTER:
            persistMaster(null);
            break; // no-op
        default:
            throw new IllegalStateException("Unknown state for master-change: "+delta.getMasterChange());
        }
    }

    private void persistMaster(String nodeId) {
        masterWriter.write(nodeId);
        changeLogWriter.append(Time.makeDateString()+": set master to "+nodeId+"\n");
    }

    @Override
    @VisibleForTesting
    public void waitForWritesCompleted(Duration timeout) throws InterruptedException, TimeoutException {
        for (MementoFileWriterSync<?> writer : nodeWriters.values()) {
            writer.waitForWriteCompleted(timeout);
        }
        masterWriter.waitForWriteCompleted(timeout);
    }

    private String readFile(File file) throws IOException {
        return Files.asCharSource(file, Charsets.UTF_8).read();
    }
    
    private void persist(ManagementNodeSyncRecord node) {
        MementoFileWriterSync<ManagementNodeSyncRecord> writer = getOrCreateNodeWriter(node.getNodeId());
        boolean fileExists = writer.exists();
        writer.write(node);
        if (!fileExists) {
            changeLogWriter.append(Time.makeDateString()+": created node "+node.getNodeId()+"\n");
        }
        if (node.getStatus() == ManagementNodeState.TERMINATED || node.getStatus() == ManagementNodeState.FAILED) {
            changeLogWriter.append(Time.makeDateString()+": set node "+node.getNodeId()+" status to "+node.getStatus()+"\n");
        }
    }
    
    private void deleteNode(String nodeId) {
        getOrCreateNodeWriter(nodeId).delete();
        changeLogWriter.append(Time.makeDateString()+": deleted node "+nodeId+"\n");
    }
    
    private MementoFileWriterSync<ManagementNodeSyncRecord> getOrCreateNodeWriter(String nodeId) {
        MementoFileWriterSync<ManagementNodeSyncRecord> writer = nodeWriters.get(nodeId);
        if (writer == null) {
            nodeWriters.putIfAbsent(nodeId, new MementoFileWriterSync<ManagementNodeSyncRecord>(getFileForNode(nodeId), serializer, tmpSuffix));
            writer = nodeWriters.get(nodeId);
        }
        return writer;
    }
    
    private File getFileForNode(String nodeId) {
        return new File(nodesDir, nodeId);
    }
    
    private File getFileForMaster() {
        return new File(dir, "master");
    }

    private File getFileForPlaneId() {
        return new File(dir, "plane.id");
    }

    private File getFileForChangeLog() {
        return new File(dir, "change.log");
    }
}

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
package org.apache.brooklyn.core.mgmt.ha;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.ha.HighAvailabilityMode;
import org.apache.brooklyn.api.mgmt.ha.ManagementNodeState;
import org.apache.brooklyn.api.mgmt.ha.ManagementNodeSyncRecord;
import org.apache.brooklyn.api.mgmt.ha.ManagementPlaneSyncRecord;
import org.apache.brooklyn.api.mgmt.ha.ManagementPlaneSyncRecordPersister;
import org.apache.brooklyn.core.mgmt.ha.dto.BasicManagementNodeSyncRecord;
import org.apache.brooklyn.core.mgmt.ha.dto.ManagementPlaneSyncRecordImpl;
import org.apache.brooklyn.core.mgmt.persist.MementoSerializer;
import org.apache.brooklyn.core.mgmt.persist.PersistenceObjectStore;
import org.apache.brooklyn.core.mgmt.persist.RetryingMementoSerializer;
import org.apache.brooklyn.core.mgmt.persist.StoreObjectAccessorLocking;
import org.apache.brooklyn.core.mgmt.persist.XmlMementoSerializer;
import org.apache.brooklyn.core.mgmt.persist.PersistenceObjectStore.StoreObjectAccessorWithLock;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;

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
 */
@Beta
public class ManagementPlaneSyncRecordPersisterToObjectStore implements ManagementPlaneSyncRecordPersister {

    // TODO Multiple node appending to change.log could cause strange interleaving, or perhaps even data loss?
    // But this file is not critical to functionality.

    // TODO Should ManagementPlaneSyncRecordPersister.Delta be different so can tell what is a significant event,
    // and thus log it in change.log - currently only subset of significant things being logged.

    private static final Logger LOG = LoggerFactory.getLogger(ManagementPlaneSyncRecordPersisterToObjectStore.class);

    private static final Duration SHUTDOWN_TIMEOUT = Duration.TEN_SECONDS;
    private static final Duration SYNC_WRITE_TIMEOUT = Duration.TEN_SECONDS;
    public static final String NODES_SUB_PATH = "nodes";

    // TODO Leak if we go through lots of managers; but tiny!
    private final ConcurrentMap<String, StoreObjectAccessorWithLock> nodeWriters = Maps.newConcurrentMap();

    private StoreObjectAccessorWithLock masterWriter;
    private StoreObjectAccessorWithLock changeLogWriter;

    private ManagementContext mgmt;
    private final PersistenceObjectStore objectStore;
    private final MementoSerializer<Object> serializer;

    private static final int MAX_SERIALIZATION_ATTEMPTS = 5;

    private boolean started = false;
    private volatile boolean running = true;

    protected final AtomicLong checkpointLogCount = new AtomicLong();
    private static final int INITIAL_LOG_WRITES = 5;
    
    @VisibleForTesting
    /** allows, when testing, to be able to override file times / blobstore times with time from the ticker */
    private boolean preferRemoteTimestampInMemento = false;

    /**
     * @param mgmt not used much at present but handy to ensure we know it so that obj store is prepared
     * @param objectStore the objectStore use to read/write management-plane data;
     *   this must have been {@link PersistenceObjectStore#prepareForSharedUse(org.apache.brooklyn.core.mgmt.persist.PersistMode, HighAvailabilityMode)}
     * @param classLoader ClassLoader to use when deserializing data
     */
    public ManagementPlaneSyncRecordPersisterToObjectStore(ManagementContext mgmt, PersistenceObjectStore objectStore, ClassLoader classLoader) {
        this.mgmt = mgmt;
        this.objectStore = checkNotNull(objectStore, "objectStore");

        MementoSerializer<Object> rawSerializer = new XmlMementoSerializer<Object>(checkNotNull(classLoader, "classLoader"));
        this.serializer = new RetryingMementoSerializer<Object>(rawSerializer, MAX_SERIALIZATION_ATTEMPTS);

        objectStore.createSubPath(NODES_SUB_PATH);

        LOG.debug("ManagementPlaneMemento-persister will use store "+objectStore);
    }

    protected synchronized void init() {
        if (!started) {
            started = true;
            //Leading slash causes problems in SL, it's not a correct file name so remove it.
            //But once removed we can't load the master file from existing persistence stores.
            //Try to detect if the old file exists, if so use old-style names, otherwise use the correct names.
            masterWriter = new StoreObjectAccessorLocking(objectStore.newAccessor("/master"));
            if (masterWriter.get() != null) {
                changeLogWriter = new StoreObjectAccessorLocking(objectStore.newAccessor("/change.log"));
            } else {
                masterWriter = new StoreObjectAccessorLocking(objectStore.newAccessor("master"));
                changeLogWriter = new StoreObjectAccessorLocking(objectStore.newAccessor("change.log"));
            }
        }
    }

    @VisibleForTesting
    public void preferRemoteTimestampInMemento() {
        preferRemoteTimestampInMemento = true;
    }
    
    @Override
    public void stop() {
        running = false;
        try {
            for (StoreObjectAccessorWithLock writer : nodeWriters.values()) {
                try {
                    writer.waitForCurrentWrites(SHUTDOWN_TIMEOUT);
                } catch (TimeoutException e) {
                    LOG.warn("Timeout during shutdown, waiting for write of "+writer+"; continuing");
                }
            }
            try {
                masterWriter.waitForCurrentWrites(SHUTDOWN_TIMEOUT);
            } catch (TimeoutException e) {
                LOG.warn("Timeout during shutdown, waiting for write of "+masterWriter+"; continuing");
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public ManagementPlaneSyncRecord loadSyncRecord() throws IOException {
        if (!running) {
            throw new IllegalStateException("Persister not running; cannot load memento from "+ objectStore.getSummaryName());
        }
        init();
        
        // Note this is called a lot - every time we check the heartbeats
        if (LOG.isTraceEnabled()) LOG.trace("Loading management-plane memento from {}", objectStore.getSummaryName());

        Stopwatch stopwatch = Stopwatch.createStarted();

        ManagementPlaneSyncRecordImpl.Builder builder = ManagementPlaneSyncRecordImpl.builder();

        // Be careful about order: if the master-file says nodeX then nodeX's file must have an up-to-date timestamp.
        // Therefore read master file first, followed by the other node-files.
        String masterNodeId = masterWriter.get();
        if (masterNodeId == null) {
            LOG.debug("No master-memento deserialized from file "+masterWriter+"; ignoring and continuing (normal on startup, should cause an error later in live operation)");
        } else {
            builder.masterNodeId(masterNodeId);
        }

        // Load node-files
        List<String> nodeFiles = objectStore.listContentsWithSubPath(NODES_SUB_PATH);
        LOG.trace("Loading nodes from {}; {} nodes.",
                new Object[]{objectStore.getSummaryName(), nodeFiles.size()});

        for (String nodeFile : nodeFiles) {
            PersistenceObjectStore.StoreObjectAccessor objectAccessor = objectStore.newAccessor(nodeFile);
            String nodeContents = null;
            Exception problem = null;
            try {
                nodeContents = objectAccessor.get();
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                problem = e;
            }
            if (problem!=null || Strings.isBlank(nodeContents)) {
                // happens if node has gone away, or if FileBasedObjectStore.moveFile is not atomic, 
                // i.e. it has deleted but not updated it yet
                if (objectAccessor.exists()) {
                    throw Exceptions.propagate(new IllegalStateException("Node record "+nodeFile+" could not be read when "+mgmt.getManagementNodeId()+" was scanning", problem));
                } else {
                    LOG.warn("Node record "+nodeFile+" went away while "+mgmt.getManagementNodeId()+" was scanning, ignoring (it has probably been terminated)");
                    // if file was deleted, silently ignore
                    continue;
                }
            }
            ManagementNodeSyncRecord memento = (ManagementNodeSyncRecord) serializer.fromString(nodeContents);
            if (memento == null) {
                // shouldn't happen
                throw Exceptions.propagate(new IllegalStateException("Node record "+nodeFile+" could not be deserialized when "+mgmt.getManagementNodeId()+" was scanning: "+nodeContents, problem));
            } else {
                if (memento.getRemoteTimestamp()!=null && preferRemoteTimestampInMemento) {
                    // in test mode, the remote timestamp is stored in the file
                } else {
                    if (memento.getRemoteTimestamp()!=null) {
                        LOG.debug("Ignoring remote timestamp in memento file ("+memento+"); looks like this data has been manually copied in");
                    }
                    Date lastModifiedDate = objectAccessor.getLastModifiedDate();
                    ((BasicManagementNodeSyncRecord)memento).setRemoteTimestamp(lastModifiedDate!=null ? lastModifiedDate.getTime() : null);
                }
                builder.node(memento);
            }
        }

        if (LOG.isDebugEnabled()) LOG.trace("Loaded management-plane memento; {} nodes, took {}",
            nodeFiles.size(),
            Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS)));
        return builder.build();
    }
    
    @Override
    public void delta(Delta delta) {
        if (!running) {
            if (LOG.isDebugEnabled()) LOG.debug("Persister not running; ignoring checkpointed delta of manager-memento");
            return;
        }
        init();
        
        Stopwatch stopwatch = Stopwatch.createStarted();
        if (LOG.isTraceEnabled()) LOG.trace("Checkpointing delta of manager-memento; updating {}", delta);
        
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
            persistMaster(checkNotNull(delta.getNewMasterOrNull()), null);
            break;
        case CLEAR_MASTER:
            persistMaster("", delta.getExpectedMasterToClear());
            break; // no-op
        default:
            throw new IllegalStateException("Unknown state for master-change: "+delta.getMasterChange());
        }
        if (LOG.isDebugEnabled() && shouldLogCheckpoint()) LOG.debug("Checkpointed delta of manager-memento in "+Time.makeTimeStringRounded(stopwatch)+": "+delta);
    }

    private void persistMaster(String nodeId, String optionalExpectedId) {
        if (optionalExpectedId!=null) {
            String currentRemoteMaster = masterWriter.get();
            if (currentRemoteMaster==null) {
                // okay to have nothing at remote
            } else if (!currentRemoteMaster.trim().equals(optionalExpectedId.trim())) {
                LOG.warn("Master at server is "+(Strings.isBlank(currentRemoteMaster) ? "<none>" : currentRemoteMaster)+"; expected "+optionalExpectedId+" "
                    + (Strings.isNonBlank(nodeId) ? "and would set as "+nodeId : "and would clear") 
                    + ", so not applying (yet)");
                return;
            }
        }
        masterWriter.put(nodeId);
        try {
            masterWriter.waitForCurrentWrites(SYNC_WRITE_TIMEOUT);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
        changeLogWriter.append(Time.makeDateString() + ": set master to " + nodeId + "\n");
        try {
            changeLogWriter.waitForCurrentWrites(SYNC_WRITE_TIMEOUT);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    @VisibleForTesting
    public void waitForWritesCompleted(Duration timeout) throws InterruptedException, TimeoutException {
        for (StoreObjectAccessorWithLock writer : nodeWriters.values()) {
            writer.waitForCurrentWrites(timeout);
        }
        masterWriter.waitForCurrentWrites(timeout);
    }

    public void checkpoint(ManagementPlaneSyncRecord record) {
        init();
        for (ManagementNodeSyncRecord node : record.getManagementNodes().values()) {
            // Check included in case the node in the memento is the one being initialised by
            // BrooklynLauncher in the copy state command.
            if (!ManagementNodeState.INITIALIZING.equals(node.getStatus()) && node.getNodeId() != null) {
                persist(node);
            }
        }
    }

    private void persist(ManagementNodeSyncRecord node) {
        StoreObjectAccessorWithLock writer = getOrCreateNodeWriter(node.getNodeId());
        boolean fileExists = writer.exists();
        writer.put(serializer.toString(node));
        try {
            writer.waitForCurrentWrites(SYNC_WRITE_TIMEOUT);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
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

    private StoreObjectAccessorWithLock getOrCreateNodeWriter(String nodeId) {
        PersistenceObjectStore.StoreObjectAccessorWithLock writer = nodeWriters.get(nodeId);
        if (writer == null) {
            nodeWriters.putIfAbsent(nodeId, 
                new StoreObjectAccessorLocking(objectStore.newAccessor(NODES_SUB_PATH+"/"+nodeId)));
            writer = nodeWriters.get(nodeId);
        }
        return writer;
    }

    protected boolean shouldLogCheckpoint() {
        long logCount = checkpointLogCount.incrementAndGet();
        return (logCount < INITIAL_LOG_WRITES) || (logCount % 1000 == 0);
    }

}

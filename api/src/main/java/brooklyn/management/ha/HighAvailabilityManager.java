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

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;

/**
 * Monitors other management nodes (via the {@link ManagementPlaneSyncRecordPersister}) to detect
 * if the current master has failed or stopped. If so, then deterministically chooses a new master.
 * If that master is self, then promotes.

 * Users are not expected to implement this class, or to call methods on it directly.
 * 
 * Expected lifecycle of methods calls on this is:
 * <ol>
 *   <li>{@link #setPersister(ManagementPlaneSyncRecordPersister)}
 *   <li>Exactly one of {@link #disabled()} or {@link #start(HighAvailabilityMode)}
 *   <li>{@link #stop()}
 * </ol>
 * 
 * @since 0.7.0
 */
@Beta
public interface HighAvailabilityManager {

    ManagementNodeState getNodeState();
    
    /**
     * @param persister
     * @return self
     */
    HighAvailabilityManager setPersister(ManagementPlaneSyncRecordPersister persister);

    /**
     * Indicates that HA is disabled: this node will act as the only management node in this management plane,
     * and will not persist HA meta-information (meaning other nodes cannot join). 
     * <p>
     * Subsequently can expect {@link #getNodeState()} to be {@link ManagementNodeState#MASTER} 
     * and {@link #loadManagementPlaneSyncRecord(boolean)} to show just this one node --
     * as if it were running HA with just one node --
     * but {@link #isRunning()} will return false.
     * <p>
     * Currently this method is intended to be called early in the lifecycle,
     * instead of {@link #start(HighAvailabilityMode)}. It may be an error if
     * this is called after this HA Manager is started.
     */
    @Beta
    void disabled();

    /** Whether HA mode is operational */
    boolean isRunning();
    
    /**
     * Starts the monitoring of other nodes (and thus potential promotion of this node from standby to master).
     * <p>
     * When this method returns, the status of this node will be set,
     * either {@link ManagementNodeState#MASTER} if appropriate 
     * or {@link ManagementNodeState#STANDBY} / {@link ManagementNodeState#HOT_STANDBY}.
     *
     * @param startMode mode to start with
     * @throws IllegalStateException if current state of the management-plane doesn't match that desired by {@code startMode} 
     */
    void start(HighAvailabilityMode startMode);

    /**
     * Stops this node, then publishes own status (via {@link ManagementPlaneSyncRecordPersister} of {@link ManagementNodeState#TERMINATED}.
     */
    void stop();

    /** changes the mode that this HA server is running in
     * <p>
     * note it will be an error to {@link #changeMode(HighAvailabilityMode)} to {@link ManagementNodeState#MASTER} 
     * when there is already a master; to promote a node explicitly set its priority higher than
     * the others and invoke {@link #changeMode(HighAvailabilityMode)} to a standby mode on the existing master */
    void changeMode(HighAvailabilityMode mode);

    /** sets the priority, and publishes it synchronously so it is canonical */
    void setPriority(long priority);
    
    long getPriority();
    
    /**
     * Returns a snapshot of the management-plane's current / most-recently-known status,
     * as last read from {@link #loadManagementPlaneSyncRecord(boolean)}, or null if none read.
     */
    ManagementPlaneSyncRecord getLastManagementPlaneSyncRecord();
    
    /**
     * @deprecated since 0.7.0 use {@link #getLastManagementPlaneSyncRecord()} or {@link #loadManagementPlaneSyncRecord(boolean)}
     * to be explicit about the source.  
     */
    ManagementPlaneSyncRecord getManagementPlaneSyncState();
    
    /**
     * @param useLocalKnowledgeForThisNode - if true, the record for this mgmt node will be replaced with the
     * actual current status known in this JVM (may be more recent than what is persisted);
     * for most purposes there is little difference but in some cases the local node being updated
     * may be explicitly wanted or not wanted
     */
    ManagementPlaneSyncRecord loadManagementPlaneSyncRecord(boolean useLocalKnowledgeForThisNode);
    
    @VisibleForTesting
    ManagementPlaneSyncRecordPersister getPersister();
}

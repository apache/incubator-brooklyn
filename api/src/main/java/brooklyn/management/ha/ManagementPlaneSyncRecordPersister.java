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

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeoutException;

import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;

/**
 * Controls the persisting and reading back of mementos relating to the management plane.
 * This state does not relate to the entities being managed.
 * 
 * @see {@link HighAvailabilityManager#setPersister(ManagementPlaneSyncRecordPersister)} for its use in management-node failover
 * 
 * @since 0.7.0
 */
@Beta
public interface ManagementPlaneSyncRecordPersister {

    /**
     * Analogue to {@link BrooklynMementoPersister#loadMemento(brooklyn.mementos.BrooklynMementoPersister.LookupContext)}
     * <p>
     * Note that this method is *not* thread safe.
     */
    ManagementPlaneSyncRecord loadSyncRecord() throws IOException;
    
    void delta(Delta delta);

    void stop();

    @VisibleForTesting
    void waitForWritesCompleted(Duration timeout) throws InterruptedException, TimeoutException;
    
    public interface Delta {
        public enum MasterChange {
            NO_CHANGE,
            SET_MASTER,
            CLEAR_MASTER
        }
        Collection<ManagementNodeSyncRecord> getNodes();
        Collection<String> getRemovedNodeIds();
        MasterChange getMasterChange();
        String getNewMasterOrNull();
    }
}

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
package brooklyn.rest.testing.mocks;

import java.util.Map;

import brooklyn.management.ha.HighAvailabilityManager;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.management.ha.ManagementPlaneSyncRecord;
import brooklyn.management.ha.ManagementPlaneSyncRecordPersister;

public class HighAvailabilityManagerStub implements HighAvailabilityManager {

    private ManagementNodeState state = ManagementNodeState.MASTER;

    public void setState(ManagementNodeState state) {
        this.state = state;
    }

    private static RuntimeException fail() {
        throw new UnsupportedOperationException("Mocked method not implemented");
    }

    @Override
    public boolean isRunning() {
        return state != ManagementNodeState.TERMINATED;
    }

    @Override
    public ManagementNodeState getNodeState() {
        return state;
    }

    @Override
    public long getLastStateChange() {
        return 0;
    }

    @Override
    public HighAvailabilityManager setPersister(ManagementPlaneSyncRecordPersister persister) {
        throw fail();
    }

    @Override
    public void disabled() {
        throw fail();
    }

    @Override
    public void start(HighAvailabilityMode startMode) {
        throw fail();
    }

    @Override
    public void stop() {
        throw fail();
    }

    @Override
    public void changeMode(HighAvailabilityMode mode) {
        throw fail();
    }

    @Override
    public void setPriority(long priority) {
        throw fail();
    }

    @Override
    public long getPriority() {
        throw fail();
    }

    @Override
    public void publishClearNonMaster() {
        throw fail();
    }

    @Override
    public ManagementPlaneSyncRecord getLastManagementPlaneSyncRecord() {
        throw fail();
    }

    @Override
    public ManagementPlaneSyncRecord getManagementPlaneSyncState() {
        throw fail();
    }

    @Override
    public ManagementPlaneSyncRecord loadManagementPlaneSyncRecord(boolean useLocalKnowledgeForThisNode) {
        throw fail();
    }

    @Override
    public ManagementPlaneSyncRecordPersister getPersister() {
        throw fail();
    }

    @Override
    public Map<String, Object> getMetrics() {
        throw fail();
    }

}

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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.BrooklynVersion;
import brooklyn.entity.rebind.plane.dto.BasicManagementNodeSyncRecord;
import brooklyn.management.ha.BasicMasterChooser.AlphabeticMasterChooser;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableSet;

public class MasterChooserTest {

    private MutableManagementPlaneSyncRecord memento;
    private AlphabeticMasterChooser chooser;
    private long now;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        memento = new MutableManagementPlaneSyncRecord();
        chooser = new AlphabeticMasterChooser();
        now = System.currentTimeMillis();
    }
    
    @Test
    public void testChoosesFirstAlphanumeric() throws Exception {
        memento.addNode(newManagerMemento("node1",  ManagementNodeState.STANDBY, now));
        memento.addNode(newManagerMemento("node2",  ManagementNodeState.STANDBY, now));
        memento.addNode(newManagerMemento("node3",  ManagementNodeState.STANDBY, now));
        Duration heartbeatTimeout = Duration.THIRTY_SECONDS;
        String ownNodeId = "node2";
        assertEquals(chooser.choose(memento, heartbeatTimeout, ownNodeId).getNodeId(), "node1");
    }
    
    @Test
    public void testReturnsNullIfNoValid() throws Exception {
        memento.addNode(newManagerMemento("node1", ManagementNodeState.STANDBY, now - 31*1000));
        Duration heartbeatTimeout = Duration.THIRTY_SECONDS;
        assertNull(chooser.choose(memento, heartbeatTimeout, "node2"));
    }
    
    @Test
    public void testFiltersOutByHeartbeat() throws Exception {
        memento.addNode(newManagerMemento("node1", ManagementNodeState.STANDBY, now - 31*1000));
        memento.addNode(newManagerMemento("node2", ManagementNodeState.STANDBY, now - 20*1000));
        memento.addNode(newManagerMemento("node3", ManagementNodeState.STANDBY, now));
        Duration heartbeatTimeout = Duration.THIRTY_SECONDS;
        assertEquals(chooser.filterHealthy(memento, heartbeatTimeout, now).keySet(), ImmutableSet.of("node2", "node3"));
    }
    
    @Test
    public void testFiltersOutByStatus() throws Exception {
        memento.addNode(newManagerMemento("node1", ManagementNodeState.FAILED, now));
        memento.addNode(newManagerMemento("node2", ManagementNodeState.TERMINATED, now));
        memento.addNode(newManagerMemento("node3", null, now));
        memento.addNode(newManagerMemento("node4",  ManagementNodeState.STANDBY, now));
        memento.addNode(newManagerMemento("node5", ManagementNodeState.MASTER, now));
        Duration heartbeatTimeout = Duration.THIRTY_SECONDS;
        assertEquals(chooser.filterHealthy(memento, heartbeatTimeout, now).keySet(), ImmutableSet.of("node4", "node5"));
    }
    
    private ManagementNodeSyncRecord newManagerMemento(String nodeId, ManagementNodeState status, long timestamp) {
        return BasicManagementNodeSyncRecord.builder().brooklynVersion(BrooklynVersion.get()).nodeId(nodeId).status(status).localTimestamp(timestamp).remoteTimestamp(timestamp).build();
    }
}

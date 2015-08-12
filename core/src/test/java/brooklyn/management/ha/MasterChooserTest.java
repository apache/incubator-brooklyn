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

import java.util.List;

import org.apache.brooklyn.management.ha.ManagementNodeState;
import org.apache.brooklyn.management.ha.ManagementNodeSyncRecord;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.BrooklynVersion;
import brooklyn.entity.basic.EntityFunctions;
import brooklyn.entity.rebind.plane.dto.BasicManagementNodeSyncRecord;
import brooklyn.management.ha.BasicMasterChooser.AlphabeticMasterChooser;
import brooklyn.management.ha.BasicMasterChooser.ScoredRecord;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

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
        assertEquals(getIds(chooser.sort(chooser.filterHealthy(memento, heartbeatTimeout, now))), ImmutableList.of("node2", "node3"));
    }
    
    protected static List<String> getIds(List<ScoredRecord<?>> filterHealthy) {
        return ImmutableList.copyOf(Iterables.transform(filterHealthy, EntityFunctions.id()));
    }

    @Test
    public void testFiltersOutByStatusNotPreferringMaster() throws Exception {
        assertEquals(doTestFiltersOutByStatus(false, false), ImmutableList.of("node4", "node5"));
    }
    @Test
    public void testFiltersOutByStatusPreferringMaster() throws Exception {
        assertEquals(doTestFiltersOutByStatus(true, false), ImmutableList.of("node5", "node4"));
    }
    
    @Test
    public void testFiltersOutByStatusNotPreferringHot() throws Exception {
        assertEquals(doTestFiltersOutByStatus(false, true), ImmutableList.of("node4", "node5", "node6"));
    }
    @Test
    public void testFiltersOutByStatusPreferringHot() throws Exception {
        assertEquals(doTestFiltersOutByStatus(true, true), ImmutableList.of("node5", "node6", "node4"));
    }
    
    protected List<String> doTestFiltersOutByStatus(boolean preferHot, boolean includeHot) throws Exception {
        chooser = new AlphabeticMasterChooser(preferHot);
        memento.addNode(newManagerMemento("node1", ManagementNodeState.FAILED, now));
        memento.addNode(newManagerMemento("node2", ManagementNodeState.TERMINATED, now));
        memento.addNode(newManagerMemento("node3", null, now));
        memento.addNode(newManagerMemento("node4",  ManagementNodeState.STANDBY, now));
        memento.addNode(newManagerMemento("node5", ManagementNodeState.MASTER, now));
        if (includeHot)
            memento.addNode(newManagerMemento("node6",  ManagementNodeState.HOT_STANDBY, now));
        return getIds(chooser.sort(chooser.filterHealthy(memento, Duration.THIRTY_SECONDS, now)));
    }

    @Test
    public void testExplicityPriority() throws Exception {
        chooser = new AlphabeticMasterChooser();
        memento.addNode(newManagerMemento("node1", ManagementNodeState.STANDBY, now, BrooklynVersion.get(), 2L));
        memento.addNode(newManagerMemento("node2", ManagementNodeState.STANDBY, now, BrooklynVersion.get(), -1L));
        memento.addNode(newManagerMemento("node3", ManagementNodeState.STANDBY, now, BrooklynVersion.get(), null));
        List<String> order = getIds(chooser.sort(chooser.filterHealthy(memento, Duration.THIRTY_SECONDS, now)));
        assertEquals(order, ImmutableList.of("node1", "node3", "node2"));
    }

    @Test
    public void testVersionsMaybeNull() throws Exception {
        chooser = new AlphabeticMasterChooser();
        memento.addNode(newManagerMemento("node1", ManagementNodeState.STANDBY, now, "v10", null));
        memento.addNode(newManagerMemento("node2", ManagementNodeState.STANDBY, now, "v3-snapshot", null));
        memento.addNode(newManagerMemento("node3", ManagementNodeState.STANDBY, now, "v3-snapshot", -1L));
        memento.addNode(newManagerMemento("node4", ManagementNodeState.STANDBY, now, "v3-snapshot", null));
        memento.addNode(newManagerMemento("node5", ManagementNodeState.STANDBY, now, "v3-stable", null));
        memento.addNode(newManagerMemento("node6", ManagementNodeState.STANDBY, now, "v1", null));
        memento.addNode(newManagerMemento("node7", ManagementNodeState.STANDBY, now, null, null));
        List<String> order = getIds(chooser.sort(chooser.filterHealthy(memento, Duration.THIRTY_SECONDS, now)));
        assertEquals(order, ImmutableList.of("node1", "node5", "node6", "node2", "node4", "node7", "node3"));
    }

    private ManagementNodeSyncRecord newManagerMemento(String nodeId, ManagementNodeState status, long timestamp) {
        return newManagerMemento(nodeId, status, timestamp, BrooklynVersion.get(), null);
    }
    private ManagementNodeSyncRecord newManagerMemento(String nodeId, ManagementNodeState status, long timestamp,
            String version, Long priority) {
        return BasicManagementNodeSyncRecord.builder().brooklynVersion(version).nodeId(nodeId).status(status).localTimestamp(timestamp).remoteTimestamp(timestamp).
            priority(priority).build();
    }
}

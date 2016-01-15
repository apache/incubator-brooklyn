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

import static org.testng.Assert.assertEquals;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ha.HighAvailabilityMode;
import org.apache.brooklyn.api.mgmt.ha.ManagementNodeState;
import org.apache.brooklyn.api.mgmt.ha.ManagementPlaneSyncRecordPersister;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.ha.HighAvailabilityManagerImpl;
import org.apache.brooklyn.core.mgmt.ha.ManagementPlaneSyncRecordPersisterToObjectStore;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.mgmt.persist.BrooklynMementoPersisterToObjectStore;
import org.apache.brooklyn.core.mgmt.persist.InMemoryObjectStore;
import org.apache.brooklyn.core.mgmt.persist.ListeningObjectStore;
import org.apache.brooklyn.core.mgmt.persist.PersistMode;
import org.apache.brooklyn.core.mgmt.persist.PersistenceObjectStore;
import org.apache.brooklyn.core.mgmt.rebind.PersistenceExceptionHandlerImpl;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class WarmStandbyTest {

    private static final Logger log = LoggerFactory.getLogger(WarmStandbyTest.class);
    
    private List<HaMgmtNode> nodes = new MutableList<WarmStandbyTest.HaMgmtNode>();
    Map<String,String> sharedBackingStore = MutableMap.of();
    Map<String,Date> sharedBackingStoreDates = MutableMap.of();
    private ClassLoader classLoader = getClass().getClassLoader();
    
    public class HaMgmtNode {
        // TODO share with HotStandbyTest and SplitBrainTest and a few others (minor differences but worth it ultimately)
        
        private ManagementContextInternal mgmt;
        private String ownNodeId;
        private String nodeName;
        private ListeningObjectStore objectStore;
        private ManagementPlaneSyncRecordPersister persister;
        private HighAvailabilityManagerImpl ha;

        @BeforeMethod(alwaysRun=true)
        public void setUp() throws Exception {
            nodeName = "node "+nodes.size();
            mgmt = newLocalManagementContext();
            ownNodeId = mgmt.getManagementNodeId();
            objectStore = new ListeningObjectStore(newPersistenceObjectStore());
            objectStore.injectManagementContext(mgmt);
            objectStore.prepareForSharedUse(PersistMode.CLEAN, HighAvailabilityMode.DISABLED);
            persister = new ManagementPlaneSyncRecordPersisterToObjectStore(mgmt, objectStore, classLoader);
            ((ManagementPlaneSyncRecordPersisterToObjectStore)persister).preferRemoteTimestampInMemento();
            BrooklynMementoPersisterToObjectStore persisterObj = new BrooklynMementoPersisterToObjectStore(objectStore, mgmt.getBrooklynProperties(), classLoader);
            mgmt.getRebindManager().setPersister(persisterObj, PersistenceExceptionHandlerImpl.builder().build());
            ha = ((HighAvailabilityManagerImpl)mgmt.getHighAvailabilityManager())
                .setPollPeriod(Duration.PRACTICALLY_FOREVER)
                .setHeartbeatTimeout(Duration.THIRTY_SECONDS)
                .setPersister(persister);
            log.info("Created "+nodeName+" "+ownNodeId);
        }
        
        public void tearDown() throws Exception {
            if (ha != null) ha.stop();
            if (mgmt != null) Entities.destroyAll(mgmt);
            if (objectStore != null) objectStore.deleteCompletely();
        }
        
        @Override
        public String toString() {
            return nodeName+" "+ownNodeId;
        }
    }
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        nodes.clear();
        sharedBackingStore.clear();
    }
    
    public HaMgmtNode newNode() throws Exception {
        HaMgmtNode node = new HaMgmtNode();
        node.setUp();
        nodes.add(node);
        return node;
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        for (HaMgmtNode n: nodes)
            n.tearDown();
    }

    protected ManagementContextInternal newLocalManagementContext() {
        return new LocalManagementContextForTests();
    }

    protected PersistenceObjectStore newPersistenceObjectStore() {
        return new InMemoryObjectStore(sharedBackingStore, sharedBackingStoreDates);
    }

    // TODO refactor above -- routines above this line are shared among HotStandbyTest and SplitBrainTest
    
    @Test
    public void testWarmStandby() throws Exception {
        HaMgmtNode n1 = newNode();
        n1.ha.start(HighAvailabilityMode.AUTO);
        assertEquals(n1.ha.getNodeState(), ManagementNodeState.MASTER);
        
        TestApplication app = TestApplication.Factory.newManagedInstanceForTests(n1.mgmt);
        app.start(MutableList.<Location>of());
        
        n1.mgmt.getRebindManager().forcePersistNow(false, null);

        HaMgmtNode n2 = newNode();
        n2.ha.start(HighAvailabilityMode.STANDBY);
        assertEquals(n2.ha.getNodeState(), ManagementNodeState.STANDBY);

        assertEquals(n2.mgmt.getApplications().size(), 0);
    }
    
    // TODO support forcible demotion, and check that a master forcibly demoted 
    // to warm standby clears its apps, policies, and locations  


}

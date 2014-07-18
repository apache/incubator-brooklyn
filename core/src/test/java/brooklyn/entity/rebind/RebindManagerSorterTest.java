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
package brooklyn.entity.rebind;

import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.entity.trait.Identifiable;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.TreeNode;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

public class RebindManagerSorterTest {

    private TestApplication app;
    private ManagementContext managementContext;
    private RebindManagerImpl rebindManager;
    private Set<ManagementContext> mgmts = MutableSet.of();
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        mgmts.add(managementContext = app.getManagementContext());
        rebindManager = (RebindManagerImpl) managementContext.getRebindManager();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        for (ManagementContext m: mgmts) Entities.destroyAll(m);
        mgmts.clear();
    }

    @Test
    public void testSortOrder() throws Exception {
        TestEntity e1a = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity e1b = e1a.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        // In reverse order
        Map<String, EntityMemento> nodes = toMementos(ImmutableList.of(e1b, e1a, app));
        Map<String, EntityMemento> sortedNodes = rebindManager.sortParentFirst(nodes);
        assertOrder(sortedNodes, ImmutableList.of(app, e1a, e1b));

        // already in correct order
        Map<String, EntityMemento> nodes2 = toMementos(ImmutableList.of(app, e1a, e1b));
        Map<String, EntityMemento> sortedNodes2 = rebindManager.sortParentFirst(nodes2);
        assertOrder(sortedNodes2, ImmutableList.of(app, e1a, e1b));
    }
    
    @Test
    public void testSortOrderMultipleBranches() throws Exception {
        TestEntity e1a = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity e1b = e1a.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity e2a = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity e2b = e2a.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        Map<String, EntityMemento> nodes = toMementos(ImmutableList.of(e2b, e1b, e2a, e1a, app));
        Map<String, EntityMemento> sortedNodes = rebindManager.sortParentFirst(nodes);
        assertOrder(sortedNodes, ImmutableList.of(app, e1a, e1b), ImmutableList.of(app, e2a, e2b));
    }
    
    @Test
    public void testSortOrderMultipleApps() throws Exception {
        TestApplication app2 = TestApplication.Factory.newManagedInstanceForTests();
        mgmts.add(app2.getManagementContext());

        TestEntity e1a = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity e1b = e1a.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity e2a = app2.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity e2b = e2a.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        Map<String, EntityMemento> nodes = toMementos(ImmutableList.of(e2b, e1b, e2a, e1a, app, app2));
        Map<String, EntityMemento> sortedNodes = rebindManager.sortParentFirst(nodes);
        assertOrder(sortedNodes, ImmutableList.of(app, e1a, e1b), ImmutableList.of(app2, e2a, e2b));
    }

    @Test
    public void testSortOrderWhenNodesMissing() throws Exception {
        TestEntity e1a = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity e1b = e1a.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        Map<String, EntityMemento> nodes = toMementos(ImmutableList.of(e1b, e1a));
        Map<String, EntityMemento> sortedNodes = rebindManager.sortParentFirst(nodes);
        assertOrder(sortedNodes, ImmutableList.of(e1a, e1b));
    }
    
    @SuppressWarnings("unchecked")
    private void assertOrder(Map<String, ? extends TreeNode> nodes, Iterable<? extends Identifiable> order) {
        assertOrders(nodes, order);
    }
    @SuppressWarnings("unchecked")
    private void assertOrder(Map<String, ? extends TreeNode> nodes, Iterable<? extends Identifiable> order1, Iterable<? extends Identifiable> order2) {
        assertOrders(nodes, order1, order2);
    }
    private void assertOrders(Map<String, ? extends TreeNode> nodes, Iterable<? extends Identifiable>... orders) {
        List<String> actualOrder = ImmutableList.copyOf(nodes.keySet());
        String errmsg = "actualOrder="+actualOrder+"; requiredSubOrderings="+Arrays.toString(orders);
        for (Iterable<? extends Identifiable> order : orders) {
            int prevIndex = -1;
            for (Identifiable o : order) {
                int index = actualOrder.indexOf(o.getId());
                assertTrue(index > prevIndex, errmsg);
                prevIndex = index;
            }
        }
    }

    private Map<String, EntityMemento> toMementos(Iterable<? extends Entity> entities) {
        Map<String, EntityMemento> result = Maps.newLinkedHashMap();
        for (Entity entity : entities) {
            result.put(entity.getId(), MementosGenerators.newEntityMemento(entity));
        }
        return result;
    }
}

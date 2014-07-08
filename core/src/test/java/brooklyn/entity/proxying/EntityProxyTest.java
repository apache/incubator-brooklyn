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
package brooklyn.entity.proxying;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.Set;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.management.EntityManager;
import brooklyn.management.Task;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class EntityProxyTest extends BrooklynAppUnitTestSupport {

    private TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }
    
    @Test
    public void testBuiltAppGivesProxies() {
        assertIsProxy(entity);
        assertIsProxy(app);
    }

    @Test
    public void testGetChildrenAndParentsReturnsProxies() {
        TestEntity child = (TestEntity) Iterables.get(app.getChildren(), 0);
        Application parent = (Application) child.getParent();
        
        assertIsProxy(child);
        assertIsProxy(parent);
    }
    
    @Test
    public void testEffectorOnProxyIsRecorded() {
        Object result = entity.identityEffector("abc");
        assertEquals(result, "abc");
        
        Set<Task<?>> tasks = mgmt.getExecutionManager().getTasksWithAllTags(
                ImmutableList.of(ManagementContextInternal.EFFECTOR_TAG, 
                BrooklynTaskTags.tagForContextEntity(entity)));
        Task<?> task = Iterables.get(tasks, 0);
        assertEquals(tasks.size(), 1, "tasks="+tasks);
        assertTrue(task.getDescription().contains("identityEffector"));
    }
    
    @Test
    public void testEntityManagerQueriesGiveProxies() {
        EntityManager entityManager = mgmt.getEntityManager();
        
        Application retrievedApp = (Application) entityManager.getEntity(app.getId());
        TestEntity retrievedEntity = (TestEntity) entityManager.getEntity(entity.getId());

        assertIsProxy(retrievedApp);
        assertIsProxy(retrievedEntity);
        
        Collection<Entity> entities = entityManager.getEntities();
        for (Entity e : entities) {
            assertIsProxy(e);
        }
        assertEquals(ImmutableSet.copyOf(entities), ImmutableSet.of(app, entity));
    }

    @Test
    public void testCreateAndManageChild() {
        TestEntity result = entity.createAndManageChild(EntitySpec.create(TestEntity.class));
        assertIsProxy(result);
        assertIsProxy(Iterables.get(entity.getChildren(), 0));
        assertIsProxy(result.getParent());
        assertIsProxy(mgmt.getEntityManager().getEntity(result.getId()));
    }

    @Test
    public void testDisplayName() {
        TestEntity result = entity.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("Boo"));
        assertIsProxy(result);
        assertEquals(result.getDisplayName(), "Boo");
    }

    @Test
    public void testCreateRespectsFlags() {
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class).
                configure("confName", "boo"));
        assertEquals(entity2.getConfig(TestEntity.CONF_NAME), "boo");
    }

    @Test
    public void testCreateRespectsConfigKey() {
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class).
                configure(TestEntity.CONF_NAME, "foo"));
        assertEquals(entity2.getConfig(TestEntity.CONF_NAME), "foo");
    }

    @Test
    public void testCreateRespectsConfInMap() {
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class).
                configure(MutableMap.of(TestEntity.CONF_NAME, "bar")));
        assertEquals(entity2.getConfig(TestEntity.CONF_NAME), "bar");
    }

    @Test
    public void testCreateRespectsFlagInMap() {
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class).
                configure(MutableMap.of("confName", "baz")));
        assertEquals(entity2.getConfig(TestEntity.CONF_NAME), "baz");
    }

    @Test
    public void testCreateInAppWithClassAndMap() {
        StartableApplication app2 = null;
        try {
            ApplicationBuilder appB = new ApplicationBuilder() {
                @Override
                protected void doBuild() {
                    addChild(MutableMap.of("confName", "faz"), TestEntity.class);
                }
            };
            app2 = appB.manage();
            assertEquals(Iterables.getOnlyElement(app2.getChildren()).getConfig(TestEntity.CONF_NAME), "faz");
        } finally {
            if (app2 != null) Entities.destroyAll(app2.getManagementContext());
        }
    }

    private void assertIsProxy(Entity e) {
        assertFalse(e instanceof AbstractEntity, "e="+e+";e.class="+(e != null ? e.getClass() : null));
        assertTrue(e instanceof EntityProxy, "e="+e+";e.class="+(e != null ? e.getClass() : null));
    }
}

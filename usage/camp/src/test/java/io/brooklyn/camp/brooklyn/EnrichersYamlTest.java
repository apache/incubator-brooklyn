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
package io.brooklyn.camp.brooklyn;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.enricher.basic.Propagator;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityAdjuncts;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.policy.Enricher;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.policy.TestEnricher;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

@Test
public class EnrichersYamlTest extends AbstractYamlTest {
    private static final Logger log = LoggerFactory.getLogger(EnrichersYamlTest.class);

    @Test
    public void testWithAppEnricher() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-app-with-enricher.yaml"));
        waitForApplicationTasks(app);
        Assert.assertEquals(app.getDisplayName(), "test-app-with-enricher");
        
        log.info("App started:");
        Entities.dumpInfo(app);
        
        Assert.assertEquals(EntityAdjuncts.getNonSystemEnrichers(app).size(), 1);
        final Enricher enricher = EntityAdjuncts.getNonSystemEnrichers(app).iterator().next();
        Assert.assertTrue(enricher instanceof TestEnricher, "enricher="+enricher);
        Assert.assertEquals(enricher.getConfig(TestEnricher.CONF_NAME), "Name from YAML");
        Assert.assertEquals(enricher.getConfig(TestEnricher.CONF_FROM_FUNCTION), "$brooklyn: is a fun place");
        
        Entity target = ((EntityInternal)app).getExecutionContext().submit(MutableMap.of(), new Callable<Entity>() {
            public Entity call() {
                return enricher.getConfig(TestEnricher.TARGET_ENTITY);
            }}).get();
        Assert.assertNotNull(target);
        Assert.assertEquals(target.getDisplayName(), "testentity");
        Assert.assertEquals(target, app.getChildren().iterator().next());
        Entity targetFromFlag = ((EntityInternal)app).getExecutionContext().submit(MutableMap.of(), new Callable<Entity>() {
            public Entity call() {
                return enricher.getConfig(TestEnricher.TARGET_ENTITY_FROM_FLAG);
            }}).get();
        Assert.assertEquals(targetFromFlag, target);
        Map<?, ?> leftoverProperties = ((TestEnricher) enricher).getLeftoverProperties();
        Assert.assertEquals(leftoverProperties.get("enricherLiteralValue1"), "Hello");
        Assert.assertEquals(leftoverProperties.get("enricherLiteralValue2"), "World");
        Assert.assertEquals(leftoverProperties.size(), 2);
    }
    
    @Test
    public void testWithEntityEnricher() throws Exception {
        final Entity app = createAndStartApplication(loadYaml("test-entity-with-enricher.yaml"));
        waitForApplicationTasks(app);
        Assert.assertEquals(app.getDisplayName(), "test-entity-with-enricher");

        log.info("App started:");
        Entities.dumpInfo(app);

        Assert.assertEquals(EntityAdjuncts.getNonSystemEnrichers(app).size(), 0);
        Assert.assertEquals(app.getChildren().size(), 1);
        final Entity child = app.getChildren().iterator().next();
        Asserts.eventually(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return EntityAdjuncts.getNonSystemEnrichers(child).size();
            }
        }, Predicates.<Integer> equalTo(1));        
        final Enricher enricher = EntityAdjuncts.getNonSystemEnrichers(child).iterator().next();
        Assert.assertNotNull(enricher);
        Assert.assertTrue(enricher instanceof TestEnricher, "enricher=" + enricher + "; type=" + enricher.getClass());
        Assert.assertEquals(enricher.getConfig(TestEnricher.CONF_NAME), "Name from YAML");
        Assert.assertEquals(enricher.getConfig(TestEnricher.CONF_FROM_FUNCTION), "$brooklyn: is a fun place");
        
        Assert.assertEquals(((TestEnricher) enricher).getLeftoverProperties(),
                ImmutableMap.of("enricherLiteralValue1", "Hello", "enricherLiteralValue2", "World"));
    }
    
    @Test
    public void testPropagatingEnricher() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-propagating-enricher.yaml"));
        waitForApplicationTasks(app);
        Assert.assertEquals(app.getDisplayName(), "test-propagating-enricher");

        log.info("App started:");
        Entities.dumpInfo(app);
        TestEntity entity = (TestEntity)app.getChildren().iterator().next();
        entity.setAttribute(TestEntity.NAME, "New Name");
        Asserts.eventually(Entities.attributeSupplier(app, TestEntity.NAME), Predicates.<String>equalTo("New Name"));
    }
    
    @Test
    public void testPropogateChildSensor() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-basic-template.yaml",
                    "  brooklyn.config:",
                    "    test.confName: parent entity",
                    "  id: parentId",
                    "  brooklyn.enrichers:",
                    "  - enricherType: brooklyn.enricher.basic.Propagator",
                    "    brooklyn.config:",
                    "      enricher.producer: $brooklyn:component(\"childId\")",
                    "      enricher.propagating.propagatingAll: true",
                    "  brooklyn.children:",
                    "  - serviceType: brooklyn.test.entity.TestEntity",
                    "    id: childId",
                    "    brooklyn.config:",
                    "      test.confName: Child Name"));
        waitForApplicationTasks(app);
        
        log.info("App started:");
        Entities.dumpInfo(app);
        Assert.assertEquals(app.getChildren().size(), 1);
        final Entity parentEntity = app.getChildren().iterator().next();
        Assert.assertTrue(parentEntity instanceof TestEntity, "Expected parent entity to be TestEntity, found:" + parentEntity);
        Assert.assertEquals(parentEntity.getChildren().size(), 1);
        Entity childEntity = parentEntity.getChildren().iterator().next();
        Assert.assertTrue(childEntity instanceof TestEntity, "Expected child entity to be TestEntity, found:" + childEntity);
        Asserts.eventually(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return EntityAdjuncts.getNonSystemEnrichers(parentEntity).size();
            }
        }, Predicates.<Integer>equalTo(1));
        Enricher enricher = EntityAdjuncts.getNonSystemEnrichers(parentEntity).iterator().next();
        Asserts.assertTrue(enricher instanceof Propagator, "Expected enricher to be Propagator, found:" + enricher);
        final Propagator propagator = (Propagator)enricher;
        Entity producer = ((EntityInternal)parentEntity).getExecutionContext().submit(MutableMap.of(), new Callable<Entity>() {
            public Entity call() {
                return propagator.getConfig(Propagator.PRODUCER);
            }}).get();
        Assert.assertEquals(producer, childEntity);
        Asserts.assertTrue(Boolean.valueOf(propagator.getConfig(Propagator.PROPAGATING_ALL)), "Expected Propagator.PROPAGATING_ALL to be true");
        ((TestEntity)childEntity).setAttribute(TestEntity.NAME, "New Name");
        Asserts.eventually(Entities.attributeSupplier(parentEntity, TestEntity.NAME), Predicates.<String>equalTo("New Name"));
    }
    
    @Test
    public void testMultipleEnricherReferences() throws Exception {
        final Entity app = createAndStartApplication(loadYaml("test-referencing-enrichers.yaml"));
        waitForApplicationTasks(app);
        
        Entity entity1 = null, entity2 = null, child1 = null, child2 = null, grandchild1 = null, grandchild2 = null;
        
        Assert.assertEquals(app.getChildren().size(), 2);
        for (Entity child : app.getChildren()) {
            if (child.getDisplayName().equals("entity 1"))
                entity1 = child;
            if (child.getDisplayName().equals("entity 2"))
                entity2 = child;
        }
        Assert.assertNotNull(entity1);
        Assert.assertNotNull(entity2);
        
        Assert.assertEquals(entity1.getChildren().size(), 2);
        for (Entity child : entity1.getChildren()) {
            if (child.getDisplayName().equals("child 1"))
                child1 = child;
            if (child.getDisplayName().equals("child 2"))
                child2 = child;
        }
        Assert.assertNotNull(child1);
        Assert.assertNotNull(child2);
        
        Assert.assertEquals(child1.getChildren().size(), 2);
        for (Entity child : child1.getChildren()) {
            if (child.getDisplayName().equals("grandchild 1"))
               grandchild1 = child;
            if (child.getDisplayName().equals("grandchild 2"))
                grandchild2 = child;
        }
        Assert.assertNotNull(grandchild1);
        Assert.assertNotNull(grandchild2);
        
        ImmutableSet<Enricher> enrichers = new ImmutableSet.Builder<Enricher>()
                .add(getEnricher(app))
                .add(getEnricher(entity1))
                .add(getEnricher(entity2))
                .add(getEnricher(child1))
                .add(getEnricher(child2))
                .add(getEnricher(grandchild1))
                .add(getEnricher(grandchild2))
                .build();
        
        Map<ConfigKey<Entity>, Entity> keyToEntity = new ImmutableMap.Builder<ConfigKey<Entity>, Entity>()
                .put(TestReferencingEnricher.TEST_APPLICATION, app)
                .put(TestReferencingEnricher.TEST_ENTITY_1, entity1)
                .put(TestReferencingEnricher.TEST_ENTITY_2, entity2)
                .put(TestReferencingEnricher.TEST_CHILD_1, child1)
                .put(TestReferencingEnricher.TEST_CHILD_2, child2)
                .put(TestReferencingEnricher.TEST_GRANDCHILD_1, grandchild1)
                .put(TestReferencingEnricher.TEST_GRANDCHILD_2, grandchild2)
                .build();
        
        for (Enricher enricher : enrichers)
            checkReferences(enricher, keyToEntity);
    }
    
    private void checkReferences(final Enricher enricher, Map<ConfigKey<Entity>, Entity> keyToEntity) throws Exception {
        for (final ConfigKey<Entity> key : keyToEntity.keySet()) {
            final Entity entity = keyToEntity.get(key); // Grab an entity whose execution context we can use
            Entity fromConfig = ((EntityInternal)entity).getExecutionContext().submit(MutableMap.of(), new Callable<Entity>() {
                @Override
                public Entity call() throws Exception {
                    return (Entity) enricher.getConfig(key);
                }
            }).get();
            Assert.assertEquals(fromConfig, keyToEntity.get(key));
        }
    }
    
    private Enricher getEnricher(Entity entity) {
        List<Enricher> enrichers = EntityAdjuncts.getNonSystemEnrichers(entity);
        Assert.assertEquals(enrichers.size(), 1, "Wrong number of enrichers: "+enrichers);
        Enricher enricher = enrichers.iterator().next();
        Assert.assertTrue(enricher instanceof TestReferencingEnricher, "Wrong enricher: "+enricher);
        return enricher;
    }
    
    @Override
    protected Logger getLogger() {
        return log;
    }
    
}

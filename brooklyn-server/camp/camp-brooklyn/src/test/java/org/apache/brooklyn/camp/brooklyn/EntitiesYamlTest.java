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
package org.apache.brooklyn.camp.brooklyn;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.StringReader;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.methods.BrooklynDslCommon;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.methods.DslComponent;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.methods.DslComponent.Scope;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityFunctions;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.mgmt.internal.EntityManagerInternal;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityImpl;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.software.base.SameServerEntity;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

@Test
public class EntitiesYamlTest extends AbstractYamlTest {
    private static final Logger log = LoggerFactory.getLogger(EntitiesYamlTest.class);

    protected Entity setupAndCheckTestEntityInBasicYamlWith(String ...extras) throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-basic-template.yaml", extras));
        waitForApplicationTasks(app);

        Entities.dumpInfo(app);
        
        Assert.assertEquals(app.getDisplayName(), "test-entity-basic-template");

        log.info("App started:");
        Entities.dumpInfo(app);
        
        Assert.assertTrue(app.getChildren().iterator().hasNext(), "Expected app to have child entity");
        Entity entity = app.getChildren().iterator().next();
        Assert.assertTrue(entity instanceof TestEntity, "Expected TestEntity, found " + entity.getClass());
        
        return (TestEntity)entity;
    }
    
    @Test
    public void testSingleEntity() throws Exception {
        setupAndCheckTestEntityInBasicYamlWith();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBrooklynConfig() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith( 
            "  brooklyn.config:",
            "    test.confName: Test Entity Name",
            "    test.confMapPlain:",
            "      foo: bar",
            "      baz: qux",
            "    test.confListPlain:",
            "      - dogs",
            "      - cats",
            "      - badgers",
            "    test.confSetThing: !!set",
            "      ? square",
            "      ? circle",
            "      ? triangle",
            "    test.confObject: 5");
        
        Assert.assertEquals(testEntity.getConfig(TestEntity.CONF_NAME), "Test Entity Name");
        List<String> list = testEntity.getConfig(TestEntity.CONF_LIST_PLAIN);
        Assert.assertEquals(list, ImmutableList.of("dogs", "cats", "badgers"));
        Map<String, String> map = testEntity.getConfig(TestEntity.CONF_MAP_PLAIN);
        Assert.assertEquals(map, ImmutableMap.of("foo", "bar", "baz", "qux"));
        Set<String> set = (Set<String>)testEntity.getConfig(TestEntity.CONF_SET_THING);
        Assert.assertEquals(set, ImmutableSet.of("square", "circle", "triangle"));
        Object object = testEntity.getConfig(TestEntity.CONF_OBJECT);
        Assert.assertEquals(object, 5);
    }

    @Test
    public void testFlagInBrooklynConfig() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith( 
            "  brooklyn.config:",
            "    confName: Foo Bar");
        Assert.assertEquals(testEntity.getConfig(TestEntity.CONF_NAME), "Foo Bar");
    }

    @Test
    public void testUndeclaredItemInBrooklynConfig() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith( 
            "  brooklyn.config:",
            "    test.dynamic.confName: Foo Bar");
        Assert.assertEquals(testEntity.getConfig(ConfigKeys.newStringConfigKey("test.dynamic.confName")), "Foo Bar");
    }

    @Test
    public void testFlagAtRoot() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith( 
            "  confName: Foo Bar");
        Assert.assertEquals(testEntity.getConfig(TestEntity.CONF_NAME), "Foo Bar");
    }

    @Test
    public void testFlagAtRootEntityImpl() throws Exception {
        Entity app = createAndStartApplication(
                "services:",
                "- serviceType: " + TestEntityImpl.class.getName(),
                "  confName: Foo Bar");
        Entity testEntity = Iterables.getOnlyElement(app.getChildren());
        Assert.assertEquals(testEntity.getConfig(TestEntity.CONF_NAME), "Foo Bar");
    }

    @Test
    public void testConfigKeyAtRoot() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith( 
            "  test.confName: Foo Bar");
        Assert.assertEquals(testEntity.getConfig(TestEntity.CONF_NAME), "Foo Bar");
    }

    @Test
    public void testUndeclaredItemAtRootIgnored() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith( 
            "  test.dynamic.confName: Foo Bar");
        // should NOT be set (and there should be a warning in the log)
        String dynamicConfNameValue = testEntity.getConfig(ConfigKeys.newStringConfigKey("test.dynamic.confName"));
        Assert.assertNull(dynamicConfNameValue);
    }

    @Test
    public void testExplicitFlags() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith( 
            "  brooklyn.flags:",
            "    confName: Foo Bar");
        Assert.assertEquals(testEntity.getConfig(TestEntity.CONF_NAME), "Foo Bar");
    }

    @Test
    public void testExplicitFlagsEntityImpl() throws Exception {
        Entity app = createAndStartApplication(
                "services:",
                "- serviceType: " + TestEntityImpl.class.getName(),
                "  brooklyn.flags:",
                "    confName: Foo Bar");
        Entity testEntity = Iterables.getOnlyElement(app.getChildren());
        Assert.assertEquals(testEntity.getConfig(TestEntity.CONF_NAME), "Foo Bar");
    }

    @Test
    public void testUndeclaredExplicitFlagsIgnored() throws Exception {
        Entity testEntity = setupAndCheckTestEntityInBasicYamlWith( 
            "  brooklyn.flags:",
            "    test.dynamic.confName: Foo Bar");
        String dynamicConfNameValue = testEntity.getConfig(ConfigKeys.newStringConfigKey("test.dynamic.confName"));
        Assert.assertNull(dynamicConfNameValue);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEmptyConfig() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-basic-template.yaml",
            "  brooklyn.config:",
            "    test.confName: \"\"",
            "    test.confListPlain: !!seq []",
            "    test.confMapPlain: !!map {}",
            "    test.confSetPlain: !!set {}",
            "    test.confObject: \"\""));
        waitForApplicationTasks(app);

        Assert.assertEquals(app.getDisplayName(), "test-entity-basic-template");

        log.info("App started:");
        Entities.dumpInfo(app);

        Entity entity = app.getChildren().iterator().next();
        Assert.assertNotNull(entity, "Expected app to have child entity");
        Assert.assertTrue(entity instanceof TestEntity, "Expected TestEntity, found " + entity.getClass());
        TestEntity testEntity = (TestEntity) entity;
        Assert.assertEquals(testEntity.getConfig(TestEntity.CONF_NAME), "");
        List<String> list = testEntity.getConfig(TestEntity.CONF_LIST_PLAIN);
        Assert.assertEquals(list, ImmutableList.of());
        Map<String, String> map = testEntity.getConfig(TestEntity.CONF_MAP_PLAIN);
        Assert.assertEquals(map, ImmutableMap.of());
        // TODO: CONF_SET_PLAIN is being set to an empty ArrayList - may be a snakeyaml issue?
        //        Set<String> plainSet = (Set<String>)testEntity.getConfig(TestEntity.CONF_SET_PLAIN);
        //        Assert.assertEquals(plainSet, ImmutableSet.of());
        Object object = testEntity.getConfig(TestEntity.CONF_OBJECT);
        Assert.assertEquals(object, "");
    }
    
    @SuppressWarnings("unchecked")
    public void testEmptyStructuredConfig() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-basic-template.yaml",
            "  brooklyn.config:",
            "    test.confName: \"\"",
            "    test.confListThing: !!seq []",
            "    test.confSetThing: !!set {}",
            "    test.confMapThing: !!map {}"));
        waitForApplicationTasks(app);

        Assert.assertEquals(app.getDisplayName(), "test-entity-basic-template");

        log.info("App started:");
        Entities.dumpInfo(app);

        Entity entity = app.getChildren().iterator().next();
        Assert.assertNotNull(entity, "Expected app to have child entity");
        Assert.assertTrue(entity instanceof TestEntity, "Expected TestEntity, found " + entity.getClass());
        TestEntity testEntity = (TestEntity) entity;
        List<String> thingList = (List<String>)testEntity.getConfig(TestEntity.CONF_LIST_THING);
        Set<String> thingSet = (Set<String>)testEntity.getConfig(TestEntity.CONF_SET_THING);
        Map<String, String> thingMap = (Map<String, String>)testEntity.getConfig(TestEntity.CONF_MAP_THING);
        Assert.assertEquals(thingList, Lists.newArrayList());
        Assert.assertEquals(thingSet, ImmutableSet.of());
        Assert.assertEquals(thingMap, ImmutableMap.of());
    }

    @Test
    public void testSensor() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-basic-template.yaml", 
            "  brooklyn.config:",
            "    test.confObject: $brooklyn:sensor(\"org.apache.brooklyn.core.test.entity.TestEntity\", \"test.sequence\")"));
        waitForApplicationTasks(app);

        Assert.assertEquals(app.getDisplayName(), "test-entity-basic-template");

        log.info("App started:");
        Entities.dumpInfo(app);

        Entity entity = app.getChildren().iterator().next();
        Assert.assertNotNull(entity, "Expected app to have child entity");
        Assert.assertTrue(entity instanceof TestEntity, "Expected TestEntity, found " + entity.getClass());
        TestEntity testEntity = (TestEntity) entity;
        Object object = testEntity.getConfig(TestEntity.CONF_OBJECT);
        Assert.assertNotNull(object);
        Assert.assertTrue(object instanceof AttributeSensor, "attributeSensor="+object);
        Assert.assertEquals(object, TestEntity.SEQUENCE);
    }

    @Test
    public void testSensorOnArbitraryClass() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-basic-template.yaml", 
            "  brooklyn.config:",
            "    test.confObject: $brooklyn:sensor(\""+EntitiesYamlTest.class.getName()+"$ArbitraryClassWithSensor\", \"mysensor\")"));
        waitForApplicationTasks(app);

        log.info("App started:");
        Entities.dumpInfo(app);

        TestEntity entity = (TestEntity) app.getChildren().iterator().next();
        Object object = entity.getConfig(TestEntity.CONF_OBJECT);
        Assert.assertEquals(object, ArbitraryClassWithSensor.MY_SENSOR);
    }
    public static class ArbitraryClassWithSensor {
        public static final AttributeSensor<String> MY_SENSOR = Sensors.newStringSensor("mysensor");
    }
    
    @Test
    public void testComponent() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-basic-template.yaml",
            "  brooklyn.config:",
            "    test.confName: first entity",
            "  id: te1",
            "- serviceType: org.apache.brooklyn.core.test.entity.TestEntity",
            "  name: second entity",
            "  brooklyn.config:",
            "    test.confObject: $brooklyn:component(\"te1\")"));
        waitForApplicationTasks(app);
        Entity firstEntity = null;
        Entity secondEntity = null;
        Assert.assertEquals(app.getChildren().size(), 2);
        for (Entity entity : app.getChildren()) {
            if (entity.getDisplayName().equals("testentity"))
                firstEntity = entity;
            else if (entity.getDisplayName().equals("second entity"))
                secondEntity = entity;
        }
        final Entity[] entities = {firstEntity, secondEntity};
        Assert.assertNotNull(entities[0], "Expected app to contain child named 'testentity'");
        Assert.assertNotNull(entities[1], "Expected app to contain child named 'second entity'");
        Object object = ((EntityInternal)app).getExecutionContext().submit(MutableMap.of(), new Callable<Object>() {
            public Object call() {
                return entities[1].getConfig(TestEntity.CONF_OBJECT);
            }}).get();
        Assert.assertNotNull(object);
        Assert.assertEquals(object, firstEntity, "Expected second entity's test.confObject to contain first entity");
    }

    @Test
    public void testGrandchildEntities() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-basic-template.yaml", 
            "  brooklyn.config:",
            "    test.confName: first entity",
            "  brooklyn.children:",
            "  - serviceType: org.apache.brooklyn.core.test.entity.TestEntity",
            "    name: Child Entity",
            "    brooklyn.config:",
            "      test.confName: Name of the first Child",
            "    brooklyn.children:",
            "    - serviceType: org.apache.brooklyn.core.test.entity.TestEntity",
            "      name: Grandchild Entity",
            "      brooklyn.config:",
            "        test.confName: Name of the Grandchild",
            "  - serviceType: org.apache.brooklyn.core.test.entity.TestEntity",
            "    name: Second Child",
            "    brooklyn.config:",
            "      test.confName: Name of the second Child"));
        waitForApplicationTasks(app);
        Assert.assertEquals(app.getChildren().size(), 1);
        Entity firstEntity = app.getChildren().iterator().next();
        Assert.assertEquals(firstEntity.getConfig(TestEntity.CONF_NAME), "first entity");
        Assert.assertEquals(firstEntity.getChildren().size(), 2);
        Entity firstChild = null;
        Entity secondChild = null;
        for (Entity entity : firstEntity.getChildren()) {
            if (entity.getConfig(TestEntity.CONF_NAME).equals("Name of the first Child"))
                firstChild = entity;
            if (entity.getConfig(TestEntity.CONF_NAME).equals("Name of the second Child"))
                secondChild = entity;
        }
        Assert.assertNotNull(firstChild, "Expected a child of 'first entity' with the name 'Name of the first Child'");
        Assert.assertNotNull(secondChild, "Expected a child of 'first entity' with the name 'Name of the second Child'");
        Assert.assertEquals(firstChild.getChildren().size(), 1);
        Entity grandchild = firstChild.getChildren().iterator().next();
        Assert.assertEquals(grandchild.getConfig(TestEntity.CONF_NAME), "Name of the Grandchild");
        Assert.assertEquals(secondChild.getChildren().size(), 0);
    }

    @Test
    public void testWithInitConfig() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-with-init-config.yaml"));
        waitForApplicationTasks(app);
        Assert.assertEquals(app.getDisplayName(), "test-entity-with-init-config");
        TestEntityWithInitConfig testWithConfigInit = null;
        TestEntity testEntity = null;
        Assert.assertEquals(app.getChildren().size(), 2);
        for (Entity entity : app.getChildren()) {
            if (entity instanceof TestEntity)
                testEntity = (TestEntity) entity;
            if (entity instanceof TestEntityWithInitConfig)
                testWithConfigInit = (TestEntityWithInitConfig) entity;
        }
        Assert.assertNotNull(testEntity, "Expected app to contain TestEntity child");
        Assert.assertNotNull(testWithConfigInit, "Expected app to contain TestEntityWithInitConfig child");
        Assert.assertEquals(testWithConfigInit.getEntityCachedOnInit(), testEntity);
        log.info("App started:");
        Entities.dumpInfo(app);
    }

    @Test
    public void testMultipleReferencesJava() throws Exception {
        final Entity app = createAndStartApplication(loadYaml("test-referencing-entities.yaml"));
        waitForApplicationTasks(app);
        
        Entity root1 = Tasks.resolving(new DslComponent(Scope.ROOT, "xxx").newTask(), Entity.class).context( ((EntityInternal)app).getExecutionContext() ).embedResolutionInTask(true).get();
        Assert.assertEquals(root1, app);
        
        Entity c1 = Tasks.resolving(new DslComponent("c1").newTask(), Entity.class).context( ((EntityInternal)app).getExecutionContext() ).embedResolutionInTask(true).get();
        Assert.assertEquals(c1, Entities.descendants(app, EntityPredicates.displayNameEqualTo("child 1")).iterator().next());
        
        Entity e1 = Tasks.resolving(new DslComponent(Scope.PARENT, "xxx").newTask(), Entity.class).context( ((EntityInternal)c1).getExecutionContext() ).embedResolutionInTask(true).get();
        Assert.assertEquals(e1, Entities.descendants(app, EntityPredicates.displayNameEqualTo("entity 1")).iterator().next());
        
        Entity root2 = Tasks.resolving(new DslComponent(Scope.ROOT, "xxx").newTask(), Entity.class).context( ((EntityInternal)c1).getExecutionContext() ).embedResolutionInTask(true).get();
        Assert.assertEquals(root2, app);
        
        Entity c1a = Tasks.resolving(BrooklynDslCommon.descendant("c1").newTask(), Entity.class).context( ((EntityInternal)e1).getExecutionContext() ).embedResolutionInTask(true).get();
        Assert.assertEquals(c1a, c1);
        Entity e1a = Tasks.resolving(BrooklynDslCommon.ancestor("e1").newTask(), Entity.class).context( ((EntityInternal)c1).getExecutionContext() ).embedResolutionInTask(true).get();
        Assert.assertEquals(e1a, e1);
        try {
            Tasks.resolving(BrooklynDslCommon.ancestor("c1").newTask(), Entity.class).context( ((EntityInternal)e1).getExecutionContext() ).embedResolutionInTask(true).get();
            Assert.fail("Should not have found c1 as ancestor of e1");
        } catch (Exception e) { /* expected */ }
    }
    
    @Test
    public void testMultipleReferences() throws Exception {
        final Entity app = createAndStartApplication(loadYaml("test-referencing-entities.yaml"));
        waitForApplicationTasks(app);
        
        Entities.dumpInfo(app);
        
        Assert.assertEquals(app.getDisplayName(), "test-referencing-entities");

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

        Map<ConfigKey<Entity>, Entity> keyToEntity = new ImmutableMap.Builder<ConfigKey<Entity>, Entity>()
            .put(ReferencingYamlTestEntity.TEST_REFERENCE_ROOT, app)
            .put(ReferencingYamlTestEntity.TEST_REFERENCE_SCOPE_ROOT, app)
            .put(ReferencingYamlTestEntity.TEST_REFERENCE_APP, app)
            .put(ReferencingYamlTestEntity.TEST_REFERENCE_ENTITY1, entity1)
            .put(ReferencingYamlTestEntity.TEST_REFERENCE_ENTITY1_ALT, entity1)
            .put(ReferencingYamlTestEntity.TEST_REFERENCE_ENTITY2, entity2)
            .put(ReferencingYamlTestEntity.TEST_REFERENCE_CHILD1, child1)
            .put(ReferencingYamlTestEntity.TEST_REFERENCE_CHILD2, child2)
            .put(ReferencingYamlTestEntity.TEST_REFERENCE_GRANDCHILD1, grandchild1)
            .put(ReferencingYamlTestEntity.TEST_REFERENCE_GRANDCHILD2, grandchild2)
            .build();

        Iterable<Entity> entitiesInApp = ((EntityInternal)app).getExecutionContext().submit(MutableMap.of(), new Callable<Iterable<Entity>>() {
            @Override
            public Iterable<Entity> call() throws Exception {
                return ((EntityManagerInternal)((EntityInternal)app).getManagementContext().getEntityManager()).getAllEntitiesInApplication((Application)app);
            }
        }).get();

        for (Entity entityInApp : entitiesInApp) {
            checkReferences(entityInApp, keyToEntity);
            try {
                getResolvedConfigInTask(entityInApp, ReferencingYamlTestEntity.TEST_REFERENCE_BOGUS);
                Assert.fail("Should not have resolved "+ReferencingYamlTestEntity.TEST_REFERENCE_BOGUS+" at "+entityInApp);
            } catch (Exception e) {
                /* expected */
            }
        }
    }
    
    @Test
    public void testScopeReferences() throws Exception {
        addCatalogItems(
                "brooklyn.catalog:",
                "  items:",
                "  -  id: ref_child",
                "     item:",
                "      type: " + ReferencingYamlTestEntity.class.getName(),
                "      test.reference.root: $brooklyn:root()",
                "      test.reference.scope_root: $brooklyn:scopeRoot()",
                "      brooklyn.children:",
                "      - type: " + ReferencingYamlTestEntity.class.getName(),
                "        test.reference.root: $brooklyn:root()",
                "        test.reference.scope_root: $brooklyn:scopeRoot()",

                "  -  id: ref_parent",
                "     item:",
                "      type: " + ReferencingYamlTestEntity.class.getName(),
                "      test.reference.root: $brooklyn:root()",
                "      test.reference.scope_root: $brooklyn:scopeRoot()",
                "      brooklyn.children:",
                "      - type: " + ReferencingYamlTestEntity.class.getName(),
                "        test.reference.root: $brooklyn:root()",
                "        test.reference.scope_root: $brooklyn:scopeRoot()",
                "        brooklyn.children:",
                "        - type: ref_child");
        Entity app = createAndStartApplication(
                "brooklyn.config:",
                "  test.reference.root: $brooklyn:root()",
                "  test.reference.scope_root: $brooklyn:scopeRoot()",
                "services:",
                "- type: " + ReferencingYamlTestEntity.class.getName(),
                "  test.reference.root: $brooklyn:root()",
                "  test.reference.scope_root: $brooklyn:scopeRoot()",
                "  brooklyn.children:",
                "  - type: " + ReferencingYamlTestEntity.class.getName(),
                "    test.reference.root: $brooklyn:root()",
                "    test.reference.scope_root: $brooklyn:scopeRoot()",
                "    brooklyn.children:",
                "    - type: ref_parent");
        
        assertScopes(app, app, app);
        Entity e1 = nextChild(app);
        assertScopes(e1, app, app);
        Entity e2 = nextChild(e1);
        assertScopes(e2, app, app);
        Entity e3 = nextChild(e2);
        assertScopes(e3, app, e3);
        Entity e4 = nextChild(e3);
        assertScopes(e4, app, e3);
        Entity e5 = nextChild(e4);
        assertScopes(e5, app, e5);
        Entity e6 = nextChild(e5);
        assertScopes(e6, app, e5);
    }
    
    private static Entity nextChild(Entity entity) {
        return Iterables.getOnlyElement(entity.getChildren());
    }
    private static void assertScopes(Entity entity, Entity root, Entity scopeRoot) {
        assertEquals(entity.config().get(ReferencingYamlTestEntity.TEST_REFERENCE_ROOT), root);
        assertEquals(entity.config().get(ReferencingYamlTestEntity.TEST_REFERENCE_SCOPE_ROOT), scopeRoot);
    }

    private void checkReferences(final Entity entity, Map<ConfigKey<Entity>, Entity> keyToEntity) throws Exception {
        for (final ConfigKey<Entity> key : keyToEntity.keySet()) {
            try {
                Assert.assertEquals(getResolvedConfigInTask(entity, key).get(), keyToEntity.get(key), "For entity " + entity.toString() + ":");
            } catch (Throwable t) {
                Exceptions.propagateIfFatal(t);
                Assert.fail("Wrong value for "+entity+":"+key+", "+((EntityInternal)entity).config().getLocalRaw(key)+": "+t, t);
            }
        }
    }

    private Maybe<Entity> getResolvedConfigInTask(final Entity entity, final ConfigKey<Entity> key) {
        return Tasks.resolving(Tasks.<Entity>builder().body(
            Functionals.callable(Suppliers.compose(EntityFunctions.config(key), Suppliers.ofInstance(entity))) ).build())
            .as(Entity.class)
            .context( ((EntityInternal)entity).getExecutionContext() ).embedResolutionInTask(true)
            .getMaybe();
    }

    public void testWithAppLocation() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-basic-template.yaml",  
            "location: localhost:(name=yaml name)"));
        waitForApplicationTasks(app);
        Assert.assertEquals(app.getLocations().size(), 1);
        Location location = app.getLocations().iterator().next();
        Assert.assertNotNull(location);
        Assert.assertEquals(location.getDisplayName(), "yaml name");
        Assert.assertEquals(app.getChildren().size(), 1);
        Entity entity = app.getChildren().iterator().next();
        Assert.assertNotNull(entity);
        Assert.assertEquals(entity.getLocations().size(), 1);
    }

    @Test
    public void testWithEntityLocation() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-basic-template.yaml",  
            "  location: localhost:(name=yaml name)\n"));
        waitForApplicationTasks(app);
        Assert.assertEquals(app.getLocations().size(), 0);
        Assert.assertEquals(app.getChildren().size(), 1);
        Entity entity = app.getChildren().iterator().next();
        Assert.assertEquals(entity.getLocations().size(), 1);
        Location location = entity.getLocations().iterator().next();
        Assert.assertNotNull(location);
        Assert.assertEquals(location.getDisplayName(), "yaml name");
        Assert.assertNotNull(entity);
    }

    @Test
    public void testWith2AppLocations() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-basic-template.yaml",  
            "locations:",
            "- localhost:(name=localhost name)",
            "- byon:(hosts=\"1.1.1.1\", name=byon name)"));
        waitForApplicationTasks(app);

        Assert.assertEquals(app.getLocations().size(), 2);
        Location localhostLocation = null, byonLocation = null; 
        for (Location location : app.getLocations()) {
            if (location.getDisplayName().equals("localhost name"))
                localhostLocation = location;
            else if (location.getDisplayName().equals("byon name"))
                byonLocation = location;
        }
        Assert.assertNotNull(localhostLocation);
        Assert.assertNotNull(byonLocation);
        Assert.assertEquals(app.getChildren().size(), 1);
        Entity entity = app.getChildren().iterator().next();
        Assert.assertNotNull(entity);
        Assert.assertEquals(entity.getLocations().size(), 2);
    }

    @Test
    public void testWith2EntityLocations() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-basic-template.yaml",  
            "  locations:",
            "  - localhost:(name=localhost name)",
            "  - byon:(hosts=\"1.1.1.1\", name=byon name)"));
        waitForApplicationTasks(app);
        Assert.assertEquals(app.getLocations().size(), 0);
        Assert.assertEquals(app.getChildren().size(), 1);
        Entity entity = app.getChildren().iterator().next();
        Assert.assertEquals(entity.getLocations().size(), 2);
        Location localhostLocation = null, byonLocation = null; 
        for (Location location : entity.getLocations()) {
            if (location.getDisplayName().equals("localhost name"))
                localhostLocation = location;
            else if (location.getDisplayName().equals("byon name"))
                byonLocation = location;
        }
        Assert.assertNotNull(localhostLocation);
        Assert.assertNotNull(byonLocation);
    }

    @Test
    public void testWithAppAndEntityLocations() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-basic-template.yaml",  
            "  location: localhost:(name=localhost name)",
            "location: byon:(hosts=\"1.1.1.1\", name=byon name)"));
        waitForApplicationTasks(app);
        Assert.assertEquals(app.getLocations().size(), 1);
        Assert.assertEquals(app.getChildren().size(), 1);
        Entity entity = app.getChildren().iterator().next();
        
        Assert.assertEquals(entity.getLocations().size(), 2);
        Iterator<Location> entityLocationIterator = entity.getLocations().iterator();
        Assert.assertEquals(entityLocationIterator.next().getDisplayName(), "localhost name");
        Assert.assertEquals(entityLocationIterator.next().getDisplayName(), "byon name");
        
        Location appLocation = app.getLocations().iterator().next();
        Assert.assertEquals(appLocation.getDisplayName(), "byon name");
    }

    @Test
    public void testCreateClusterWithMemberSpec() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-cluster-with-member-spec.yaml"));
        waitForApplicationTasks(app);
        assertEquals(app.getChildren().size(), 1);

        Entity clusterEntity = Iterables.getOnlyElement(app.getChildren());
        assertTrue(clusterEntity instanceof DynamicCluster, "cluster="+clusterEntity);

        DynamicCluster cluster = DynamicCluster.class.cast(clusterEntity);
        assertEquals(cluster.getMembers().size(), 2, "members="+cluster.getMembers());

        for (Entity member : cluster.getMembers()) {
            assertTrue(member instanceof TestEntity, "member="+member);
            assertEquals(member.getConfig(TestEntity.CONF_NAME), "yamlTest");
        }
    }

    @Test
    public void testEntitySpecConfig() throws Exception {
        String yaml =
                "services:\n"+
                "- serviceType: org.apache.brooklyn.core.test.entity.TestEntity\n"+
                "  brooklyn.config:\n"+
                "   test.childSpec:\n"+
                "     $brooklyn:entitySpec:\n"+
                "       type: org.apache.brooklyn.core.test.entity.TestEntity\n"+
                "       brooklyn.config:\n"+
                "         test.confName: inchildspec\n";
        
        Application app = (Application) createStartWaitAndLogApplication(new StringReader(yaml));
        TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());
        
        TestEntity child = (TestEntity) entity.createAndManageChildFromConfig();
        assertEquals(child.getConfig(TestEntity.CONF_NAME), "inchildspec");
    }

    @Test
    public void testEntitySpecFlags() throws Exception {
        String yaml =
                "services:\n"+
                "- serviceType: org.apache.brooklyn.core.test.entity.TestEntity\n"+
                "  confName: inParent\n"+
                "  brooklyn.config:\n"+
                "   test.childSpec:\n"+
                "     $brooklyn:entitySpec:\n"+
                "       type: org.apache.brooklyn.core.test.entity.TestEntity\n"+
                "       confName: inchildspec\n";
        
        Application app = (Application) createStartWaitAndLogApplication(new StringReader(yaml));
        TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());
        
        TestEntity child = (TestEntity) entity.createAndManageChildFromConfig();
        assertEquals(child.getConfig(TestEntity.CONF_NAME), "inchildspec");
    }

    @Test
    public void testEntitySpecExplicitFlags() throws Exception {
        String yaml =
                "services:\n"+
                "- serviceType: org.apache.brooklyn.core.test.entity.TestEntity\n"+
                "  brooklyn.flags:\n"+
                "    confName: inParent\n"+
                "  brooklyn.config:\n"+
                "   test.childSpec:\n"+
                "     $brooklyn:entitySpec:\n"+
                "       type: org.apache.brooklyn.core.test.entity.TestEntity\n"+
                "       brooklyn.flags:\n"+
                "         confName: inchildspec\n";
        
        Application app = (Application) createStartWaitAndLogApplication(new StringReader(yaml));
        TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());
        
        TestEntity child = (TestEntity) entity.createAndManageChildFromConfig();
        assertEquals(child.getConfig(TestEntity.CONF_NAME), "inchildspec");
    }

    @Test
    public void testEntitySpecWithChildren() throws Exception {
        String yaml =
                "services:\n"+
                "- serviceType: org.apache.brooklyn.core.test.entity.TestEntity\n"+
                "  brooklyn.config:\n"+
                "   test.childSpec:\n"+
                "     $brooklyn:entitySpec:\n"+
                "       type: org.apache.brooklyn.core.test.entity.TestEntity\n"+
                "       brooklyn.config:\n"+
                "         test.confName: child\n"+
                "       brooklyn.children:\n"+
                "       - type: org.apache.brooklyn.core.test.entity.TestEntity\n" +
                "         brooklyn.config:\n" +
                "           test.confName: grandchild\n" +
                "         brooklyn.children:\n"+
                "         - type: org.apache.brooklyn.core.test.entity.TestEntity\n" +
                "           brooklyn.config:\n" +
                "             test.confName: greatgrandchild\n";
        
        Application app = (Application) createStartWaitAndLogApplication(new StringReader(yaml));
        TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());
        
        TestEntity child = (TestEntity) entity.createAndManageChildFromConfig();
        assertEquals(child.getConfig(TestEntity.CONF_NAME), "child");
        assertEquals(child.getChildren().size(), 1, "Child entity should have exactly one child of its own");

        TestEntity grandchild = (TestEntity) Iterables.getOnlyElement(child.getChildren());
        assertEquals(grandchild.getConfig(TestEntity.CONF_NAME), "grandchild");
        assertEquals(grandchild.getChildren().size(), 1, "Grandchild entity should have exactly one child of its own");

        TestEntity greatgrandchild = (TestEntity) Iterables.getOnlyElement(grandchild.getChildren());
        assertEquals(greatgrandchild.getConfig(TestEntity.CONF_NAME), "greatgrandchild");
    }
    
    @Test
    public void testNestedEntitySpecConfigs() throws Exception {
        String yaml =
                "services:\n"+
                "- serviceType: org.apache.brooklyn.core.test.entity.TestEntity\n"+
                "  brooklyn.config:\n"+
                "   test.childSpec:\n"+
                "     $brooklyn:entitySpec:\n"+
                "       type: org.apache.brooklyn.core.test.entity.TestEntity\n"+
                "       brooklyn.config:\n"+
                "         test.confName: inchildspec\n"+
                "         test.childSpec:\n"+
                "           $brooklyn:entitySpec:\n"+
                "             type: org.apache.brooklyn.core.test.entity.TestEntity\n"+
                "             brooklyn.config:\n"+
                "               test.confName: ingrandchildspec\n";
        
        Application app = (Application) createStartWaitAndLogApplication(new StringReader(yaml));
        TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());
        
        TestEntity child = (TestEntity) entity.createAndManageChildFromConfig();
        assertEquals(child.getConfig(TestEntity.CONF_NAME), "inchildspec");
        
        TestEntity grandchild = (TestEntity) child.createAndManageChildFromConfig();
        assertEquals(grandchild.getConfig(TestEntity.CONF_NAME), "ingrandchildspec");
    }
    
    @Test
    public void testEntitySpecInUnmatchedConfig() throws Exception {
        String yaml =
                "services:\n"+
                "- serviceType: org.apache.brooklyn.core.test.entity.TestEntity\n"+
                "  brooklyn.config:\n"+
                "   key.does.not.match:\n"+
                "     $brooklyn:entitySpec:\n"+
                "       type: org.apache.brooklyn.core.test.entity.TestEntity\n"+
                "       brooklyn.config:\n"+
                "         test.confName: inchildspec\n";
        
        Application app = (Application) createStartWaitAndLogApplication(new StringReader(yaml));
        TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());
        EntitySpec<?> entitySpec = (EntitySpec<?>) entity.config().getBag().getStringKey("key.does.not.match");
        assertEquals(entitySpec.getType(), TestEntity.class);
        assertEquals(entitySpec.getConfig(), ImmutableMap.of(TestEntity.CONF_NAME, "inchildspec"));
    }

    @Test
    public void testAppWithSameServerEntityStarts() throws Exception {
        Entity app = createAndStartApplication(loadYaml("same-server-entity-test.yaml"));
        waitForApplicationTasks(app);
        assertNotNull(app);
        assertEquals(app.getAttribute(Attributes.SERVICE_STATE_ACTUAL), Lifecycle.RUNNING, "service state");
        assertTrue(app.getAttribute(Attributes.SERVICE_UP), "service up");

        assertEquals(app.getChildren().size(), 1);
        Entity entity = Iterables.getOnlyElement(app.getChildren());
        assertTrue(entity instanceof SameServerEntity, "entity="+entity);

        SameServerEntity sse = (SameServerEntity) entity;
        assertEquals(sse.getChildren().size(), 2);
        for (Entity child : sse.getChildren()) {
            assertTrue(child instanceof BasicEntity, "child="+child);
        }
    }
    
    @Test
    public void testEntityImplExposesAllInterfacesIncludingStartable() throws Exception {
        String yaml =
                "services:\n"+
                "- serviceType: org.apache.brooklyn.core.test.entity.TestEntityImpl\n";
        
        Application app = (Application) createStartWaitAndLogApplication(new StringReader(yaml));
        TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());
        assertTrue(entity.getCallHistory().contains("start"), "history="+entity.getCallHistory());
    }

    @Test
    public void testEntityWithInitializer() throws Exception {
        String yaml =
                "services:\n"+
                "- type: "+TestEntity.class.getName()+"\n"+
                "  brooklyn.initializers: [ { type: "+TestSensorAndEffectorInitializer.class.getName()+" } ]";
        
        Application app = (Application) createStartWaitAndLogApplication(new StringReader(yaml));
        TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());
        
        Effector<?> hi = entity.getEffector(TestSensorAndEffectorInitializer.EFFECTOR_SAY_HELLO);
        Assert.assertNotNull(hi);
        
        Assert.assertNotNull( entity.getEntityType().getSensor(TestSensorAndEffectorInitializer.SENSOR_HELLO_DEFINED) );
        Assert.assertNotNull( entity.getEntityType().getSensor(TestSensorAndEffectorInitializer.SENSOR_HELLO_DEFINED_EMITTED) );
        Assert.assertNull( entity.getEntityType().getSensor(TestSensorAndEffectorInitializer.SENSOR_LAST_HELLO) );
        
        Assert.assertNull( entity.getAttribute(Sensors.newStringSensor(TestSensorAndEffectorInitializer.SENSOR_LAST_HELLO)) );
        Assert.assertNull( entity.getAttribute(Sensors.newStringSensor(TestSensorAndEffectorInitializer.SENSOR_HELLO_DEFINED)) );
        Assert.assertEquals( entity.getAttribute(Sensors.newStringSensor(TestSensorAndEffectorInitializer.SENSOR_HELLO_DEFINED_EMITTED)),
            "1");
        
        Task<String> saying = entity.invoke(Effectors.effector(String.class, TestSensorAndEffectorInitializer.EFFECTOR_SAY_HELLO).buildAbstract(), 
            MutableMap.of("name", "Bob"));
        Assert.assertEquals(saying.get(Duration.TEN_SECONDS), "Hello Bob");
        Assert.assertEquals( entity.getAttribute(Sensors.newStringSensor(TestSensorAndEffectorInitializer.SENSOR_LAST_HELLO)),
            "Bob");
    }

    @Test
    public void testEntityWithConfigurableInitializerEmpty() throws Exception {
        String yaml =
                "services:\n"+
                "- type: "+TestEntity.class.getName()+"\n"+
                "  brooklyn.initializers: [ { type: "+TestSensorAndEffectorInitializer.TestConfigurableInitializer.class.getName()+" } ]";
        
        Application app = (Application) createStartWaitAndLogApplication(new StringReader(yaml));
        TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());
        
        Task<String> saying = entity.invoke(Effectors.effector(String.class, TestSensorAndEffectorInitializer.EFFECTOR_SAY_HELLO).buildAbstract(), 
            MutableMap.of("name", "Bob"));
        Assert.assertEquals(saying.get(Duration.TEN_SECONDS), "Hello Bob");
    }

    @Test
    public void testEntityWithConfigurableInitializerNonEmpty() throws Exception {
        String yaml =
                "services:\n"+
                "- type: "+TestEntity.class.getName()+"\n"+
                "  brooklyn.initializers: [ { "
                  + "type: "+TestSensorAndEffectorInitializer.TestConfigurableInitializer.class.getName()+","
                  + "brooklyn.config: { "+TestSensorAndEffectorInitializer.TestConfigurableInitializer.HELLO_WORD+": Hey }"
                  + " } ]";
        
        Application app = (Application) createStartWaitAndLogApplication(new StringReader(yaml));
        TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());
        
        Task<String> saying = entity.invoke(Effectors.effector(String.class, TestSensorAndEffectorInitializer.EFFECTOR_SAY_HELLO).buildAbstract(), 
            MutableMap.of("name", "Bob"));
        Assert.assertEquals(saying.get(Duration.TEN_SECONDS), "Hey Bob");
    }

    @Test
    public void testEntityTypeAsImpl() throws Exception {
        String yaml =
                "services:"+"\n"+
                "- type: "+CustomTestEntityImpl.class.getName()+"\n";

        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));

        Entity testEntity = Iterables.getOnlyElement(app.getChildren());
        assertEquals(testEntity.getEntityType().getName(), "CustomTestEntityImpl");
    }
    
    public static class CustomTestEntityImpl extends TestEntityImpl {
        public CustomTestEntityImpl() {
            System.out.println("in CustomTestEntityImpl");
        }
        @Override
        protected String getEntityTypeName() {
            return "CustomTestEntityImpl";
        }
    }

    @Override
    protected Logger getLogger() {
        return log;
    }
}

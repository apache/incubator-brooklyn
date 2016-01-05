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
package org.apache.brooklyn.core.entity;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.enricher.AbstractEnricher;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.policy.AbstractPolicy;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityImpl;
import org.apache.brooklyn.core.test.entity.TestEntityNoEnrichersImpl;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class EntitySpecTest extends BrooklynAppUnitTestSupport {

    private SimulatedLocation loc;
    private TestEntity entity;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = new SimulatedLocation();
    }

    @Test
    public void testSetsConfig() throws Exception {
        // TODO Test other permutations
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, "myname"));
        assertEquals(entity.getConfig(TestEntity.CONF_NAME), "myname");
    }

    @Test
    public void testAddsChildren() throws Exception {
        entity = app.createAndManageChild( EntitySpec.create(TestEntity.class)
            .displayName("child")
            .child(EntitySpec.create(TestEntity.class)
                .displayName("grandchild")) );

        Entity child = Iterables.getOnlyElement(app.getChildren());
        assertEquals(child, entity);
        assertEquals(child.getDisplayName(), "child");
        Entity grandchild = Iterables.getOnlyElement(child.getChildren());
        assertEquals(grandchild.getDisplayName(), "grandchild");
    }


    @Test
    public void testAddsPolicySpec() throws Exception {
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .policy(PolicySpec.create(MyPolicy.class)
                        .displayName("mypolicyname")
                        .configure(MyPolicy.CONF1, "myconf1val")
                        .configure("myfield", "myfieldval")));

        Policy policy = Iterables.getOnlyElement(entity.policies());
        assertTrue(policy instanceof MyPolicy, "policy="+policy);
        assertEquals(policy.getDisplayName(), "mypolicyname");
        assertEquals(policy.getConfig(MyPolicy.CONF1), "myconf1val");
    }

    @Test
    public void testAddsPolicy() throws Exception {
        MyPolicy policy = new MyPolicy();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .policy(policy));

        assertEquals(Iterables.getOnlyElement(entity.policies()), policy);
    }

    @Test
    public void testAddsEnricherSpec() throws Exception {
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class, TestEntityNoEnrichersImpl.class)
                .enricher(EnricherSpec.create(MyEnricher.class)
                        .displayName("myenrichername")
                        .configure(MyEnricher.CONF1, "myconf1val")
                        .configure("myfield", "myfieldval")));

        Enricher enricher = Iterables.getOnlyElement(entity.enrichers());
        assertTrue(enricher instanceof MyEnricher, "enricher="+enricher);
        assertEquals(enricher.getDisplayName(), "myenrichername");
        assertEquals(enricher.getConfig(MyEnricher.CONF1), "myconf1val");
    }

    @Test
    public void testAddsEnricher() throws Exception {
        MyEnricher enricher = new MyEnricher();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class, TestEntityNoEnrichersImpl.class)
                .enricher(enricher));

        assertEquals(Iterables.getOnlyElement(entity.enrichers()), enricher);
    }

    @Test
    public void testAddsMembers() throws Exception {
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        BasicGroup group = app.createAndManageChild(EntitySpec.create(BasicGroup.class)
                .member(entity));

        Asserts.assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of(entity));
        Asserts.assertEqualsIgnoringOrder(entity.groups(), ImmutableSet.of(group));
    }

    @Test
    public void testAddsGroups() throws Exception {
        BasicGroup group = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .group(group));

        Asserts.assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of(entity));
        Asserts.assertEqualsIgnoringOrder(entity.groups(), ImmutableSet.of(group));
    }

    @Test
    public void testCallsConfigureAfterConstruction() throws Exception {
        AbstractEntityLegacyTest.MyEntity entity = app.createAndManageChild(EntitySpec.create(AbstractEntityLegacyTest.MyEntity.class));

        assertEquals(entity.getConfigureCount(), 1);
        assertEquals(entity.getConfigureDuringConstructionCount(), 0);
    }

    @Test
    public void testDisplayNameUsesDefault() throws Exception {
        TestEntity entity = app.addChild(EntitySpec.create(TestEntity.class));

        assertTrue(entity.getDisplayName().startsWith("TestEntity:"+entity.getId().substring(0,4)), "displayName="+entity.getDisplayName());
    }

    @Test
    public void testDisplayNameUsesCustom() throws Exception {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("entityname"));

        assertEquals(entity.getDisplayName(), "entityname");
    }

    @Test
    public void testDisplayNameUsesOverriddenDefault() throws Exception {
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .impl(TestEntityWithDefaultNameImpl.class)
                .configure(TestEntityWithDefaultNameImpl.DEFAULT_NAME, "myOverriddenDefaultName"));
        assertEquals(entity.getDisplayName(), "myOverriddenDefaultName");
    }

    @Test
    public void testDisplayNameUsesCustomWhenOverriddenDefault() throws Exception {
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .impl(TestEntityWithDefaultNameImpl.class)
                .configure(TestEntityWithDefaultNameImpl.DEFAULT_NAME, "myOverriddenDefaultName")
                .displayName("myEntityName"));
        assertEquals(entity.getDisplayName(), "myEntityName");
    }

    @Test
    public void testCreatingEntitySpecFromSpecCreatesDuplicate() {
        EntitySpec<TestEntity> originalChildSpec = EntitySpec.create(TestEntity.class);
        EntitySpec<TestEntity> originalEntitySpec = EntitySpec.create(TestEntity.class).child(originalChildSpec);
        EntitySpec<TestEntity> duplicateEntitySpec = EntitySpec.create(originalEntitySpec);
        EntitySpec<?> duplicateChildSpec = duplicateEntitySpec.getChildren().get(0);

        assertEquals(originalEntitySpec, duplicateEntitySpec);
        assertTrue(originalEntitySpec != duplicateEntitySpec);
        assertEquals(originalChildSpec, duplicateChildSpec);
        assertTrue(originalChildSpec != duplicateChildSpec);
    }

    public static class TestEntityWithDefaultNameImpl extends TestEntityImpl {
        public static final ConfigKey<String> DEFAULT_NAME = ConfigKeys.newStringConfigKey("defaultName");

        @Override
        public void init() {
            super.init();
            if (getConfig(DEFAULT_NAME) != null) setDefaultDisplayName(getConfig(DEFAULT_NAME));
        }
    }

    public static class MyPolicy extends AbstractPolicy {
        public static final BasicConfigKey<String> CONF1 = new BasicConfigKey<String>(String.class, "testpolicy.conf1", "my descr, conf1", "defaultval1");
        public static final BasicConfigKey<Integer> CONF2 = new BasicConfigKey<Integer>(Integer.class, "testpolicy.conf2", "my descr, conf2", 2);

        @SetFromFlag
        public String myfield;
    }

    public static class MyEnricher extends AbstractEnricher {
        public static final BasicConfigKey<String> CONF1 = new BasicConfigKey<String>(String.class, "testenricher.conf1", "my descr, conf1", "defaultval1");
        public static final BasicConfigKey<Integer> CONF2 = new BasicConfigKey<Integer>(Integer.class, "testenricher.conf2", "my descr, conf2", 2);

        @SetFromFlag
        public String myfield;
    }
}

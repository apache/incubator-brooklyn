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

import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.policy.Policy;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.policy.TestPolicy;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

@Test
public class PoliciesYamlTest extends AbstractYamlTest {
    static final Logger log = LoggerFactory.getLogger(PoliciesYamlTest.class);

    @Test
    public void testWithAppPolicy() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-app-with-policy.yaml"));
        waitForApplicationTasks(app);
        Assert.assertEquals(app.getDisplayName(), "test-app-with-policy");

        log.info("App started:");
        Entities.dumpInfo(app);

        Assert.assertEquals(app.getPolicies().size(), 1);
        Policy policy = app.getPolicies().iterator().next();
        Assert.assertTrue(policy instanceof TestPolicy);
        Assert.assertEquals(policy.getConfig(TestPolicy.CONF_NAME), "Name from YAML");
        Assert.assertEquals(policy.getConfig(TestPolicy.CONF_FROM_FUNCTION), "$brooklyn: is a fun place");
        Map<?, ?> leftoverProperties = ((TestPolicy) policy).getLeftoverProperties();
        Assert.assertEquals(leftoverProperties.get("policyLiteralValue1"), "Hello");
        Assert.assertEquals(leftoverProperties.get("policyLiteralValue2"), "World");
        Assert.assertEquals(leftoverProperties.size(), 2);
    }
    
    @Test
    public void testWithEntityPolicy() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-with-policy.yaml"));
        waitForApplicationTasks(app);
        Assert.assertEquals(app.getDisplayName(), "test-entity-with-policy");

        log.info("App started:");
        Entities.dumpInfo(app);

        Assert.assertEquals(app.getPolicies().size(), 0);
        Assert.assertEquals(app.getChildren().size(), 1);
        Entity child = app.getChildren().iterator().next();
        Assert.assertEquals(child.getPolicies().size(), 1);
        Policy policy = child.getPolicies().iterator().next();
        Assert.assertNotNull(policy);
        Assert.assertTrue(policy instanceof TestPolicy, "policy=" + policy + "; type=" + policy.getClass());
        Assert.assertEquals(policy.getConfig(TestPolicy.CONF_NAME), "Name from YAML");
        Assert.assertEquals(policy.getConfig(TestPolicy.CONF_FROM_FUNCTION), "$brooklyn: is a fun place");
        Assert.assertEquals(((TestPolicy) policy).getLeftoverProperties(),
                ImmutableMap.of("policyLiteralValue1", "Hello", "policyLiteralValue2", "World"));
        Assert.assertEquals(policy.getConfig(TestPolicy.TEST_ATTRIBUTE_SENSOR), TestEntity.NAME);
    }
    
    @Test
    public void testChildWithPolicy() throws Exception {
        Entity app = createAndStartApplication(loadYaml("test-entity-basic-template.yaml",
                    "  brooklyn.config:",
                    "    test.confName: parent entity",
                    "  brooklyn.children:",
                    "  - serviceType: brooklyn.test.entity.TestEntity",
                    "    name: Child Entity",
                    "    brooklyn.policies:",
                    "    - policyType: brooklyn.test.policy.TestPolicy",
                    "      brooklyn.config:",
                    "        test.confName: Name from YAML",
                    "        test.attributeSensor: $brooklyn:sensor(\"brooklyn.test.entity.TestEntity\", \"test.name\")"));
        waitForApplicationTasks(app);

        Assert.assertEquals(app.getChildren().size(), 1);
        Entity firstEntity = app.getChildren().iterator().next();
        Assert.assertEquals(firstEntity.getChildren().size(), 1);
        final Entity child = firstEntity.getChildren().iterator().next();
        Assert.assertEquals(child.getChildren().size(), 0);

        Assert.assertEquals(app.getPolicies().size(), 0);
        Assert.assertEquals(firstEntity.getPolicies().size(), 0);
        
        Asserts.eventually(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return child.getPolicies().size();
            }
        }, Predicates.<Integer> equalTo(1));
        
        Policy policy = child.getPolicies().iterator().next();
        Assert.assertTrue(policy instanceof TestPolicy);
        Assert.assertEquals(policy.getConfig(TestPolicy.TEST_ATTRIBUTE_SENSOR), TestEntity.NAME);
    }
    
    @Test
    public void testMultiplePolicyReferences() throws Exception {
        final Entity app = createAndStartApplication(loadYaml("test-referencing-policies.yaml"));
        waitForApplicationTasks(app);
        Assert.assertEquals(app.getDisplayName(), "test-referencing-policies");
        
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
        
        ImmutableSet<Policy> policies = new ImmutableSet.Builder<Policy>()
                .add(getPolicy(app))
                .add(getPolicy(entity1))
                .add(getPolicy(entity2))
                .add(getPolicy(child1))
                .add(getPolicy(child2))
                .add(getPolicy(grandchild1))
                .add(getPolicy(grandchild2))
                .build();
        
        Map<ConfigKey<Entity>, Entity> keyToEntity = new ImmutableMap.Builder<ConfigKey<Entity>, Entity>()
                .put(ReferencingYamlTestEntity.TEST_REFERENCE_APP, app)
                .put(ReferencingYamlTestEntity.TEST_REFERENCE_ENTITY1, entity1)
                .put(ReferencingYamlTestEntity.TEST_REFERENCE_ENTITY2, entity2)
                .put(ReferencingYamlTestEntity.TEST_REFERENCE_CHILD1, child1)
                .put(ReferencingYamlTestEntity.TEST_REFERENCE_CHILD2, child2)
                .put(ReferencingYamlTestEntity.TEST_REFERENCE_GRANDCHILD1, grandchild1)
                .put(ReferencingYamlTestEntity.TEST_REFERENCE_GRANDCHILD2, grandchild2)
                .build();
        
        for (Policy policy : policies)
            checkReferences(policy, keyToEntity);
        
    }
    
    private void checkReferences(final Policy policy, Map<ConfigKey<Entity>, Entity> keyToEntity) throws Exception {
        for (final ConfigKey<Entity> key : keyToEntity.keySet()) {
            final Entity entity = keyToEntity.get(key); // Grab an entity whose execution context we can use
            Entity fromConfig = ((EntityInternal)entity).getExecutionContext().submit(MutableMap.of(), new Callable<Entity>() {
                @Override
                public Entity call() throws Exception {
                    return (Entity) policy.getConfig(key);
                }
            }).get();
            Assert.assertEquals(fromConfig, keyToEntity.get(key));
        }
    }
    
    private Policy getPolicy(Entity entity) {
        Assert.assertEquals(entity.getPolicies().size(), 1);
        Policy policy = entity.getPolicies().iterator().next();
        Assert.assertTrue(policy instanceof TestReferencingPolicy);
        return policy;
    }

    @Override
    protected Logger getLogger() {
        return log;
    }
    
}
